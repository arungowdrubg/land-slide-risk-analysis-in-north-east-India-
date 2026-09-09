"""Train the BhuRaksha landslide-risk classifier from the two supplied CSV files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder


TARGET = "Risk_Level"
JOIN_COLUMNS = ["year", "month_name", "district", "Season"]
NUMERIC_FEATURES = [
    "Terrain_Area_Share_Percent",
    "Recorded_Slope_Failures",
    "rainfall_mm",
]
CATEGORICAL_FEATURES = [
    "month_name",
    "district",
    "Season",
    "Slope_Angle_Range_Degrees",
    "Dominant_Failure_Mechanism",
    "Soil type",
    "Vegetation",
]
FEATURE_COLUMNS = NUMERIC_FEATURES + CATEGORICAL_FEATURES
REFERENCE_COLUMNS = ["year"] + FEATURE_COLUMNS


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--land-data", type=Path, required=True,
                        help="Path to 'land data - Sheet1.csv'.")
    parser.add_argument("--weather-data", type=Path, required=True,
                        help="Path to 'sikkim landslide - Sheet1.csv'.")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).parent / "artifacts",
                        help="Directory for the trained model and reports.")
    parser.add_argument("--test-year", type=int, default=None,
                        help="Year reserved for time-based evaluation (defaults to the newest year).")
    return parser.parse_args()


def require_columns(frame: pd.DataFrame, columns: list[str], source: str) -> None:
    missing = sorted(set(columns) - set(frame.columns))
    if missing:
        raise ValueError(f"{source} is missing required columns: {', '.join(missing)}")


def load_training_data(land_path: Path, weather_path: Path) -> pd.DataFrame:
    """Join the labelled slope records to district/month environmental data."""
    land = pd.read_csv(land_path)
    weather = pd.read_csv(weather_path)
    land.columns = land.columns.str.strip()
    weather.columns = weather.columns.str.strip()

    require_columns(land, JOIN_COLUMNS + [TARGET] + NUMERIC_FEATURES[:2]
                    + ["Slope_Angle_Range_Degrees", "Dominant_Failure_Mechanism"], "Land data")
    require_columns(weather, JOIN_COLUMNS + ["rainfall_mm", "Soil type", "Vegetation"], "Weather data")

    # The supplied weather file has an entirely blank trailing column. Select
    # just the fields used by the model, so an unnamed Excel-export column is harmless.
    weather = weather[JOIN_COLUMNS + ["rainfall_mm", "Soil type", "Vegetation"]].copy()
    if weather.duplicated(JOIN_COLUMNS).any():
        raise ValueError("Weather data must contain one row per year/month/district/season.")

    data = land.merge(weather, on=JOIN_COLUMNS, how="left", validate="many_to_one")
    if data["rainfall_mm"].isna().any():
        unmatched = data.loc[data["rainfall_mm"].isna(), JOIN_COLUMNS].drop_duplicates()
        raise ValueError(f"Some land rows have no matching weather rows: {unmatched.to_dict('records')}")

    for column in NUMERIC_FEATURES:
        data[column] = pd.to_numeric(data[column], errors="coerce")
    for column in CATEGORICAL_FEATURES + [TARGET]:
        data[column] = data[column].fillna("Unknown").astype(str).str.strip()

    data = data[data[TARGET] != "Unknown"].copy()
    if data.empty:
        raise ValueError("No labelled rows remain after loading the data.")
    return data


def build_model() -> Pipeline:
    preprocess = ColumnTransformer(
        transformers=[
            ("numeric", SimpleImputer(strategy="median"), NUMERIC_FEATURES),
            ("categorical", Pipeline(steps=[
                ("imputer", SimpleImputer(strategy="constant", fill_value="Unknown")),
                ("one_hot", OneHotEncoder(handle_unknown="ignore")),
            ]), CATEGORICAL_FEATURES),
        ]
    )
    return Pipeline(steps=[
        ("preprocess", preprocess),
        ("classifier", RandomForestClassifier(
            n_estimators=500,
            min_samples_leaf=2,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
        )),
    ])


def evaluate(data: pd.DataFrame, test_year: int) -> dict[str, object]:
    train_rows = data[data["year"] < test_year]
    test_rows = data[data["year"] == test_year]
    if train_rows.empty or test_rows.empty:
        raise ValueError(f"Cannot evaluate with test year {test_year}; both train and test rows are required.")

    model = build_model()
    model.fit(train_rows[FEATURE_COLUMNS], train_rows[TARGET])
    predicted = model.predict(test_rows[FEATURE_COLUMNS])
    return {
        "evaluation": "Train on all years before test_year; evaluate on test_year only.",
        "test_year": test_year,
        "train_rows": int(len(train_rows)),
        "test_rows": int(len(test_rows)),
        "accuracy": round(float(accuracy_score(test_rows[TARGET], predicted)), 4),
        "macro_f1": round(float(f1_score(test_rows[TARGET], predicted, average="macro")), 4),
        "classification_report": classification_report(
            test_rows[TARGET], predicted, output_dict=True, zero_division=0
        ),
    }


def save_reference_rows(data: pd.DataFrame, output_dir: Path) -> None:
    """Keep the latest known five slope profiles for summary API requests."""
    latest = (
        data.sort_values("year")
        .groupby(["district", "month_name", "Slope_Angle_Range_Degrees"], as_index=False)
        .tail(1)
        .loc[:, REFERENCE_COLUMNS]
        .sort_values(["district", "month_name", "Slope_Angle_Range_Degrees"])
    )
    latest.to_csv(output_dir / "latest_reference_features.csv", index=False)


def main() -> None:
    args = parse_arguments()
    data = load_training_data(args.land_data, args.weather_data)
    test_year = args.test_year or int(data["year"].max())
    metrics = evaluate(data, test_year)

    # After an honest time-based evaluation, refit on every supplied record so
    # the deployed model benefits from all labelled observations.
    final_model = build_model()
    final_model.fit(data[FEATURE_COLUMNS], data[TARGET])

    args.output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump({
        "model": final_model,
        "feature_columns": FEATURE_COLUMNS,
        "class_labels": list(final_model.named_steps["classifier"].classes_),
        "training_rows": int(len(data)),
        "source_years": sorted(int(year) for year in data["year"].unique()),
    }, args.output_dir / "risk_model.joblib")
    save_reference_rows(data, args.output_dir)
    (args.output_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print(json.dumps({
        "model": str(args.output_dir / "risk_model.joblib"),
        "metrics": {key: metrics[key] for key in ("test_year", "train_rows", "test_rows", "accuracy", "macro_f1")},
        "training_rows": len(data),
    }, indent=2))


if __name__ == "__main__":
    main()

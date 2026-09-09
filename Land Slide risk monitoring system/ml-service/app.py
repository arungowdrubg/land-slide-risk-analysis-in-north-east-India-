"""FastAPI inference service for the trained BhuRaksha risk model."""

from __future__ import annotations

from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from train import CATEGORICAL_FEATURES, FEATURE_COLUMNS, NUMERIC_FEATURES


ARTIFACT_DIR = Path(__file__).parent / "artifacts"
MODEL_PATH = ARTIFACT_DIR / "risk_model.joblib"
REFERENCE_PATH = ARTIFACT_DIR / "latest_reference_features.csv"
MONTH_NAMES = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]
RISK_SCORE_BY_LABEL = {"Low": 20, "Average": 45, "Medium": 70, "High": 95}

if not MODEL_PATH.exists() or not REFERENCE_PATH.exists():
    raise RuntimeError(
        "Model artifacts are missing. Run train.py first; see ml-service/README.md."
    )

ARTIFACT = joblib.load(MODEL_PATH)
MODEL = ARTIFACT["model"]
REFERENCE_DATA = pd.read_csv(REFERENCE_PATH)
CLASS_LABELS = list(ARTIFACT["class_labels"])

app = FastAPI(title="BhuRaksha ML Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Local prototype: allow the standalone HTML page and Spring API.
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictionRequest(BaseModel):
    month_name: str = Field(examples=["July"])
    district: str = Field(examples=["Gangtok"])
    season: str = Field(examples=["Monsoon"])
    slope_angle_range_degrees: str = Field(examples=["25-35"])
    terrain_area_share_percent: float = Field(examples=[31.6])
    recorded_slope_failures: float = Field(examples=[2])
    dominant_failure_mechanism: str = Field(examples=["Planar slide / Wedge failure"])
    rainfall_mm: float = Field(examples=[420.0])
    soil_type: str = Field(examples=["red loamy soil"])
    vegetation: str = Field(examples=["Subtropical Broadleaf Forests"])


def request_to_frame(request: PredictionRequest) -> pd.DataFrame:
    return pd.DataFrame([{
        "month_name": request.month_name,
        "district": request.district,
        "Season": request.season,
        "Slope_Angle_Range_Degrees": request.slope_angle_range_degrees,
        "Terrain_Area_Share_Percent": request.terrain_area_share_percent,
        "Recorded_Slope_Failures": request.recorded_slope_failures,
        "Dominant_Failure_Mechanism": request.dominant_failure_mechanism,
        "rainfall_mm": request.rainfall_mm,
        "Soil type": request.soil_type,
        "Vegetation": request.vegetation,
    }], columns=FEATURE_COLUMNS)


def standard_risk_level(dataset_label: str) -> str:
    if dataset_label == "Low":
        return "LOW"
    if dataset_label == "High":
        return "HIGH"
    return "MODERATE"


def probability_response(probabilities: np.ndarray) -> dict[str, object]:
    probability_by_label = dict(zip(CLASS_LABELS, probabilities.tolist(), strict=True))
    predicted_label = max(probability_by_label, key=probability_by_label.get)
    risk_score = round(sum(
        probability * RISK_SCORE_BY_LABEL.get(label, 50)
        for label, probability in probability_by_label.items()
    ))
    return {
        "dataset_risk_level": predicted_label,
        "risk_level": standard_risk_level(predicted_label),
        "risk_score": min(100, max(0, risk_score)),
        "probabilities": {label: round(probability, 4) for label, probability in probability_by_label.items()},
    }


def predict_frame(frame: pd.DataFrame) -> list[dict[str, object]]:
    probabilities = MODEL.predict_proba(frame[FEATURE_COLUMNS])
    return [probability_response(row) for row in probabilities]


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "training_rows": ARTIFACT["training_rows"],
        "source_years": ARTIFACT["source_years"],
    }


@app.post("/predict")
def predict(request: PredictionRequest) -> dict[str, object]:
    return predict_frame(request_to_frame(request))[0]


@app.get("/predict/summary")
def predict_summary(district: str, month: int) -> dict[str, object]:
    """Score the five latest known slope profiles for a district/month.

    This is a baseline convenience endpoint for the Java API. For a live
    assessment, POST current rainfall and field observations to /predict.
    """
    if month not in range(1, 13):
        raise HTTPException(status_code=422, detail="month must be between 1 and 12")
    month_name = MONTH_NAMES[month - 1]
    rows = REFERENCE_DATA[
        (REFERENCE_DATA["district"].str.casefold() == district.casefold())
        & (REFERENCE_DATA["month_name"] == month_name)
    ].copy()
    if rows.empty:
        available = sorted(REFERENCE_DATA["district"].unique().tolist())
        raise HTTPException(status_code=404, detail={
            "message": f"No reference features for {district} in {month_name}",
            "available_districts": available,
        })

    predictions = predict_frame(rows)
    scores = np.array([prediction["risk_score"] for prediction in predictions], dtype=float)
    weights = pd.to_numeric(rows["Terrain_Area_Share_Percent"], errors="coerce").fillna(0).to_numpy()
    if weights.sum() <= 0:
        weights = np.ones(len(rows))
    combined_score = round(float(np.average(scores, weights=weights)))
    highest_index = int(np.argmax(scores))
    combined_label = "LOW" if combined_score < 35 else "MODERATE" if combined_score < 80 else "HIGH"
    return {
        "district": rows.iloc[0]["district"],
        "month_name": month_name,
        "reference_year": int(rows["year"].max()),
        "risk_level": combined_label,
        "risk_score": combined_score,
        "rainfall_mm": round(float(rows["rainfall_mm"].mean()), 1),
        "slope_range": rows.iloc[highest_index]["Slope_Angle_Range_Degrees"],
        "slope_predictions": predictions,
    }

# BhuRaksha ML service

This service trains a Random Forest classifier for the four labels in the supplied
land dataset (`Low`, `Average`, `Medium`, `High`). It joins the two files using
`year`, `month_name`, `district`, and `Season`.

## Train the supplied data

From the project root, create a virtual environment and install the dependencies:

```powershell
py -m venv ml-service/.venv
ml-service/.venv/Scripts/python.exe -m pip install -r ml-service/requirements.txt
ml-service/.venv/Scripts/python.exe ml-service/train.py `
  --land-data "C:\Users\arung\Downloads\land data - Sheet1.csv" `
  --weather-data "C:\Users\arung\Downloads\sikkim landslide - Sheet1.csv"
```

Training writes these local, ignored artifacts:

- `artifacts/risk_model.joblib` — preprocessing pipeline and fitted classifier
- `artifacts/metrics.json` — holdout evaluation (latest year is held out)
- `artifacts/latest_reference_features.csv` — latest known monthly profiles for the summary endpoint

## Run inference

```powershell
ml-service/.venv/Scripts/python.exe -m uvicorn app:app --app-dir ml-service --reload --port 8001
```

Use `http://localhost:8001/docs` to try `POST /predict`. The Spring Boot backend
uses `GET /predict/summary` and exposes it as `GET /api/risk?place=Gangtok`.

`/predict/summary` uses the latest historical month/district profile (2025 in the
provided data) as a baseline. For a real warning system, send fresh rainfall,
ground observations, and slope-failure counts to `POST /predict`; do not treat
the historical baseline as a live sensor feed.

`Recorded_Slope_Failures` is strongly associated with the supplied target labels.
That makes this a *current-risk classification* model when field failures are
observed, rather than a pure pre-landslide forecasting model. A pre-event model
should be trained separately without that field and validated against event dates.

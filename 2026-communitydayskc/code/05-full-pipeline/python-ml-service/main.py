"""
05 - Full Pipeline · Python ML Service
Identical API to example 01 — reused here as the scoring microservice
in the Docker Compose stack.

Run (via Docker): docker-compose up
Run (standalone): uvicorn main:app --port 8000
"""

from contextlib import asynccontextmanager
import logging
from pathlib import Path

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.ensemble import RandomForestClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

MODEL_PATH = Path("fraud_model.pkl")
MODEL_VERSION = "v1.0.0"
_pipeline: Pipeline | None = None


class TransactionFeatures(BaseModel):
    amount: float
    hour_of_day: int = Field(..., ge=0, le=23)
    merchant_risk_score: float = Field(..., ge=0.0, le=1.0)
    velocity_30d: int = Field(..., ge=0)
    geo_risk_score: float = Field(..., ge=0.0, le=1.0)


class FraudScore(BaseModel):
    fraud_probability: float
    is_fraud: bool
    model_version: str
    risk_level: str


def _train() -> Pipeline:
    rng = np.random.default_rng(42)
    n_legit, n_fraud = 1800, 200
    X_legit = np.column_stack([
        rng.normal(200, 100, n_legit).clip(1),
        rng.integers(8, 20, n_legit).astype(float),
        rng.uniform(0.0, 0.25, n_legit),
        rng.integers(1, 15, n_legit).astype(float),
        rng.uniform(0.0, 0.20, n_legit),
    ])
    X_fraud = np.column_stack([
        rng.normal(1500, 400, n_fraud).clip(100),
        rng.choice([1, 2, 3, 22, 23], n_fraud).astype(float),
        rng.uniform(0.60, 1.0, n_fraud),
        rng.integers(15, 35, n_fraud).astype(float),
        rng.uniform(0.55, 1.0, n_fraud),
    ])
    X = np.vstack([X_legit, X_fraud])
    y = np.hstack([np.zeros(n_legit), np.ones(n_fraud)])
    p = Pipeline([("scaler", StandardScaler()), ("model", RandomForestClassifier(100, random_state=42))])
    p.fit(X, y)
    return p


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _pipeline
    _pipeline = joblib.load(MODEL_PATH) if MODEL_PATH.exists() else _train()
    if not MODEL_PATH.exists():
        joblib.dump(_pipeline, MODEL_PATH)
    logger.info("Model ready — version %s", MODEL_VERSION)
    yield


app = FastAPI(title="Fraud Detection Service", version=MODEL_VERSION, lifespan=lifespan)


@app.post("/predict/fraud", response_model=FraudScore)
async def predict(features: TransactionFeatures):
    if _pipeline is None:
        raise HTTPException(503, "Model not loaded")
    X = np.array([[features.amount, features.hour_of_day,
                   features.merchant_risk_score, features.velocity_30d,
                   features.geo_risk_score]])
    prob = float(_pipeline.predict_proba(X)[0][1])
    risk = "HIGH" if prob > 0.75 else "MEDIUM" if prob > 0.40 else "LOW"
    return FraudScore(fraud_probability=round(prob, 4), is_fraud=prob > 0.85,
                      model_version=MODEL_VERSION, risk_level=risk)


@app.get("/health")
async def health():
    return {"status": "healthy", "model_loaded": _pipeline is not None}

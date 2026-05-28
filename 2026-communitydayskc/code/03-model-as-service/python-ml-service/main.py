"""
01 - Model as a Service
FastAPI server that trains a fraud detection model on startup and serves predictions.

Run: uvicorn main:app --reload --port 8000
Docs: http://localhost:8000/docs
"""

import logging
from contextlib import asynccontextmanager
from pathlib import Path

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sklearn.ensemble import RandomForestClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

MODEL_PATH = Path("fraud_model.pkl")
MODEL_VERSION = "v1.0.0"
_pipeline: Pipeline | None = None


# ── Domain models ────────────────────────────────────────────────────────────

class TransactionFeatures(BaseModel):
    amount: float = Field(..., description="Transaction amount in USD", example=1850.0)
    hour_of_day: int = Field(..., ge=0, le=23, description="Hour of transaction (UTC)", example=3)
    merchant_risk_score: float = Field(..., ge=0.0, le=1.0, description="0=safe, 1=high risk", example=0.8)
    velocity_30d: int = Field(..., ge=0, description="Transactions in last 30 days", example=22)
    geo_risk_score: float = Field(..., ge=0.0, le=1.0, description="Geographic risk score", example=0.75)


class FraudScore(BaseModel):
    fraud_probability: float
    is_fraud: bool
    model_version: str
    risk_level: str
    feature_importances: dict[str, float]


# ── Model training ────────────────────────────────────────────────────────────

def _build_pipeline() -> Pipeline:
    return Pipeline([
        ("scaler", StandardScaler()),
        ("model", RandomForestClassifier(n_estimators=100, max_depth=8, random_state=42))
    ])


def _train_demo_model() -> Pipeline:
    """Train on synthetic data that captures realistic fraud patterns."""
    logger.info("Training demo fraud detection model...")
    rng = np.random.default_rng(42)
    n_legit, n_fraud = 1800, 200

    # Legit: moderate amounts, business hours, low-risk merchants/geography, normal velocity
    X_legit = np.column_stack([
        rng.normal(200, 100, n_legit).clip(1),          # amount
        rng.integers(8, 20, n_legit).astype(float),      # hour (business hours)
        rng.uniform(0.0, 0.25, n_legit),                 # merchant risk (low)
        rng.integers(1, 15, n_legit).astype(float),      # velocity
        rng.uniform(0.0, 0.20, n_legit),                 # geo risk (low)
    ])

    # Fraud: large amounts, odd hours, risky merchants/geography, burst velocity
    X_fraud = np.column_stack([
        rng.normal(1500, 400, n_fraud).clip(100),        # amount
        rng.choice([1, 2, 3, 4, 22, 23], n_fraud).astype(float),  # odd hours
        rng.uniform(0.60, 1.0, n_fraud),                 # merchant risk (high)
        rng.integers(15, 35, n_fraud).astype(float),     # velocity (burst)
        rng.uniform(0.55, 1.0, n_fraud),                 # geo risk (high)
    ])

    X = np.vstack([X_legit, X_fraud])
    y = np.hstack([np.zeros(n_legit), np.ones(n_fraud)])

    pipeline = _build_pipeline()
    pipeline.fit(X, y)
    logger.info("Training complete. Feature importances: %s",
                dict(zip(["amount", "hour", "merch_risk", "velocity", "geo_risk"],
                         pipeline["model"].feature_importances_.round(3))))
    return pipeline


# ── Startup / shutdown ────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    global _pipeline
    if MODEL_PATH.exists():
        _pipeline = joblib.load(MODEL_PATH)
        logger.info("Loaded model from %s", MODEL_PATH)
    else:
        _pipeline = _train_demo_model()
        joblib.dump(_pipeline, MODEL_PATH)
        logger.info("Model saved to %s", MODEL_PATH)
    yield
    logger.info("Shutting down ML service")


app = FastAPI(
    title="Fraud Detection ML Service",
    description="Serves a RandomForest fraud detection model via REST",
    version=MODEL_VERSION,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

FEATURE_NAMES = ["amount", "hour_of_day", "merchant_risk_score", "velocity_30d", "geo_risk_score"]


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.post("/predict/fraud", response_model=FraudScore)
async def predict_fraud(features: TransactionFeatures):
    if _pipeline is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    X = np.array([[
        features.amount,
        features.hour_of_day,
        features.merchant_risk_score,
        features.velocity_30d,
        features.geo_risk_score,
    ]])

    prob = float(_pipeline.predict_proba(X)[0][1])
    risk = "HIGH" if prob > 0.75 else "MEDIUM" if prob > 0.40 else "LOW"

    importances = dict(zip(FEATURE_NAMES,
                           _pipeline["model"].feature_importances_.round(4).tolist()))

    return FraudScore(
        fraud_probability=round(prob, 4),
        is_fraud=prob > 0.85,
        model_version=MODEL_VERSION,
        risk_level=risk,
        feature_importances=importances,
    )


@app.get("/health")
async def health():
    return {"status": "healthy", "model_loaded": _pipeline is not None, "version": MODEL_VERSION}


@app.get("/model/info")
async def model_info():
    if _pipeline is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    rf: RandomForestClassifier = _pipeline["model"]
    return {
        "version": MODEL_VERSION,
        "algorithm": "RandomForestClassifier",
        "n_estimators": rf.n_estimators,
        "max_depth": rf.max_depth,
        "feature_names": FEATURE_NAMES,
        "feature_importances": dict(zip(FEATURE_NAMES, rf.feature_importances_.round(4).tolist())),
        "n_classes": rf.n_classes_,
        "classes": rf.classes_.tolist(),
    }

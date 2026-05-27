# 02 — ONNX In-JVM

**Pattern:** Train in Python once, export to ONNX, run inference inside the JVM forever — no Python at runtime.

## Architecture

```
[Python — train_export.py]          (run once)
         |
         ▼
   fraud_model.onnx   ←─────────────────────────────────────────┐
         |                                                       |
[Spring Boot — OnnxFraudDetector]                        (retrain in Python,
    OrtSession.run() — sub-ms                             swap the file, done)
    No network, no Python process
```

## Why ONNX?

- Train in Python (scikit-learn, XGBoost, PyTorch — anything)
- Export once to a universal binary format (`.onnx`)
- Load and run inside the JVM — sub-millisecond, no HTTP, no Python
- Swap the model file without touching Java code

## How to Run

### Step 1 — Generate the ONNX model (Python, run once)

```bash
cd python-train-export
pip install -r requirements.txt
python train_export.py
# Output: ../java-onnx-inference/src/main/resources/models/fraud_model.onnx
```

### Step 2 — Start the Spring Boot app

```bash
cd java-onnx-inference
mvn spring-boot:run
```

### Step 3 — Test it

```bash
# High-risk demo
curl http://localhost:8081/api/infer/demo/high-risk

# Low-risk demo
curl http://localhost:8081/api/infer/demo/low-risk

# Latency benchmark (1000 inferences)
curl http://localhost:8081/api/infer/benchmark

# Custom features: [amount, hour, merchantRisk, velocity, geoRisk]
curl -X POST http://localhost:8081/api/infer/fraud \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.0, "hourOfDay": 10, "merchantRiskScore": 0.3, "velocity30d": 8, "geoRiskScore": 0.2}'
```

## Expected Benchmark Output

```json
{
  "iterations": 1000,
  "total_ms": 180.4,
  "avg_ms_per_inference": 0.18,
  "last_prediction": { ... }
}
```
Under 0.2ms average — compare to 10–50ms for an HTTP call.

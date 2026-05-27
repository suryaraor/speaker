"""
02 - ONNX In-JVM
Step 1: Train a sklearn model in Python and export it to ONNX format.
The resulting .onnx file is loaded by the Java Spring Boot app.

Run: python train_export.py
Output: ../java-onnx-inference/src/main/resources/models/fraud_model.onnx
"""

import os
from pathlib import Path

import numpy as np
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
import onnxruntime as rt


# ── Training data (same synthetic distribution as example 01) ─────────────────

def generate_data(seed: int = 42):
    rng = np.random.default_rng(seed)
    n_legit, n_fraud = 1800, 200

    X_legit = np.column_stack([
        rng.normal(200, 100, n_legit).clip(1).astype(np.float32),
        rng.integers(8, 20, n_legit).astype(np.float32),
        rng.uniform(0.0, 0.25, n_legit).astype(np.float32),
        rng.integers(1, 15, n_legit).astype(np.float32),
        rng.uniform(0.0, 0.20, n_legit).astype(np.float32),
    ])
    X_fraud = np.column_stack([
        rng.normal(1500, 400, n_fraud).clip(100).astype(np.float32),
        rng.choice([1, 2, 3, 22, 23], n_fraud).astype(np.float32),
        rng.uniform(0.60, 1.0, n_fraud).astype(np.float32),
        rng.integers(15, 35, n_fraud).astype(np.float32),
        rng.uniform(0.55, 1.0, n_fraud).astype(np.float32),
    ])

    X = np.vstack([X_legit, X_fraud]).astype(np.float32)
    y = np.hstack([np.zeros(n_legit), np.ones(n_fraud)]).astype(np.int64)
    return X, y


# ── Train ──────────────────────────────────────────────────────────────────────

print("Training GradientBoostingClassifier...")
X, y = generate_data()

pipeline = Pipeline([
    ("scaler", StandardScaler()),
    ("model", GradientBoostingClassifier(n_estimators=100, max_depth=4, random_state=42)),
])
pipeline.fit(X, y)
print(f"  Training accuracy: {(pipeline.predict(X) == y).mean():.3f}")
print(f"  Feature importances: {pipeline['model'].feature_importances_.round(3)}")


# ── Export to ONNX ────────────────────────────────────────────────────────────

# zipmap=False gives us a float32 probability array — much easier to consume in Java
options = {GradientBoostingClassifier: {"zipmap": False}}

initial_types = [("float_input", FloatTensorType([None, 5]))]
# target_opset=18 keeps IR version ≤9, compatible with onnxruntime-1.17.x Java
onnx_model = convert_sklearn(pipeline, initial_types=initial_types, options=options,
                              target_opset=18)

output_dir = Path("../java-onnx-inference/src/main/resources/models")
output_dir.mkdir(parents=True, exist_ok=True)
output_path = output_dir / "fraud_model.onnx"

with open(output_path, "wb") as f:
    f.write(onnx_model.SerializeToString())

print(f"\nONNX model saved to: {output_path.resolve()}")


# ── Validate the exported model ───────────────────────────────────────────────

print("\nValidating ONNX model with onnxruntime...")
sess = rt.InferenceSession(str(output_path))

print("  Inputs:")
for inp in sess.get_inputs():
    print(f"    name='{inp.name}' shape={inp.shape} type={inp.type}")

print("  Outputs:")
for out in sess.get_outputs():
    print(f"    name='{out.name}' shape={out.shape} type={out.type}")

# Test with a high-risk sample
test_sample = np.array([[1800.0, 3.0, 0.9, 28.0, 0.8]], dtype=np.float32)
label, probs = sess.run(["label", "probabilities"], {"float_input": test_sample})
print(f"\n  High-risk sample -> label={label[0]}, fraud_prob={probs[0][1]:.4f}")

# Test with a low-risk sample
test_safe = np.array([[50.0, 14.0, 0.05, 5.0, 0.1]], dtype=np.float32)
label, probs = sess.run(["label", "probabilities"], {"float_input": test_safe})
print(f"  Low-risk sample  -> label={label[0]}, fraud_prob={probs[0][1]:.4f}")

print("\nDone. Now run the Java app: cd ../java-onnx-inference && mvn spring-boot:run")

package com.example.onnx.model;

/**
 * Five numeric features fed to the ONNX model.
 * Must match the input shape expected by fraud_model.onnx exactly.
 * Use floats (float32) — ONNX Runtime expects float32, not double.
 */
public record TransactionFeatures(
    float amount,
    float hourOfDay,
    float merchantRiskScore,
    float velocity30d,
    float geoRiskScore
) {
    /** Flat float array for ONNX tensor creation: shape [1][5]. */
    public float[] toArray() {
        return new float[]{amount, hourOfDay, merchantRiskScore, velocity30d, geoRiskScore};
    }
}

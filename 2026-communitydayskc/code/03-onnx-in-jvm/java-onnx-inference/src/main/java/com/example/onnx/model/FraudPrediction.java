package com.example.onnx.model;

/**
 * Result of in-JVM ONNX inference.
 * inferenceNanos shows just how fast in-process scoring is (typically < 1ms).
 */
public record FraudPrediction(
    float  fraudProbability,
    boolean isFraud,
    String  riskLevel,
    long    inferenceNanos
) {}

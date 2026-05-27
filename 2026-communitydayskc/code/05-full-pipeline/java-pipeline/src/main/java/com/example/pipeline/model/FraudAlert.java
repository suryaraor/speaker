package com.example.pipeline.model;

import java.time.Instant;

/** Emitted when a transaction scores above the fraud threshold. */
public record FraudAlert(
    String  transactionId,
    String  accountId,
    double  amount,
    double  fraudProbability,
    String  riskLevel,
    Instant detectedAt,
    String  scoringMethod    // "ONNX_IN_JVM" or "REST_FALLBACK"
) {}

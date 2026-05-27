package com.example.springai.model;

/**
 * Fraud alert produced by the ML scoring pipeline (examples 01–05).
 * Spring AI receives this as the explainer's input.
 */
public record FraudAlert(
        String transactionId,
        String accountId,
        double amount,
        String currency,
        double fraudProbability,   // 0.0 – 1.0
        String riskLevel,          // HIGH | MEDIUM | LOW
        int hourOfDay,             // 0 – 23
        double merchantRiskScore,  // 0.0 – 1.0
        int velocity30d,           // transaction count in last 30 days
        double geoRiskScore,       // 0.0 – 1.0
        String merchantCategory,
        boolean cardPresent
) {}

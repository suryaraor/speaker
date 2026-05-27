package com.example.features.model;

import java.util.Map;

/**
 * Fully-engineered feature set ready for any ML model.
 * Includes the raw features, derived features, and metadata for auditing.
 */
public record TransactionFeatures(

    // --- Raw (normalized) ---
    double amountNormalized,     // log1p(amount) — reduces skew
    int    hourOfDay,
    int    dayOfWeek,            // 1=Monday … 7=Sunday

    // --- Categorical encodings ---
    double merchantRiskScore,    // domain lookup
    double channelRiskScore,     // ONLINE > MOBILE > POS > ATM
    double geoRiskScore,

    // --- Behavioral velocity features ---
    int    txn1h,                // transactions in last 1 hour
    int    txn24h,               // transactions in last 24 hours
    int    txn30d,               // transactions in last 30 days
    double avgAmount7d,          // rolling 7-day average transaction amount

    // --- Derived ratio features ---
    double amountToAvgRatio,     // how unusual is this amount vs. account history?

    // --- Audit ---
    Map<String, Object> metadata // feature values before normalization, for explainability

) {}

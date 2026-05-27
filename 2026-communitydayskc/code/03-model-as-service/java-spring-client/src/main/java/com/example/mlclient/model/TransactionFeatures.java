package com.example.mlclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Engineered features sent to the Python ML service.
 * Snake_case matches the Python Pydantic model field names exactly.
 */
public record TransactionFeatures(
    double amount,
    @JsonProperty("hour_of_day")        int hourOfDay,
    @JsonProperty("merchant_risk_score") double merchantRiskScore,
    @JsonProperty("velocity_30d")        int velocity30d,
    @JsonProperty("geo_risk_score")      double geoRiskScore
) {}

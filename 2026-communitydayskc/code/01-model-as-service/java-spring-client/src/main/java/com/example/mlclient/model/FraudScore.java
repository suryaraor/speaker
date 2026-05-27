package com.example.mlclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Response from the Python ML service. */
public record FraudScore(
    @JsonProperty("fraud_probability")  double fraudProbability,
    @JsonProperty("is_fraud")           boolean isFraud,
    @JsonProperty("model_version")      String modelVersion,
    @JsonProperty("risk_level")         String riskLevel,
    @JsonProperty("feature_importances") Map<String, Double> featureImportances
) {}

package com.example.nativeml.model;

/**
 * Credit decision produced by the Weka RandomForest classifier.
 */
public record CreditDecision(
    String decision,           // APPROVE | DENY | REVIEW
    double approveProb,
    double denyProb,
    double reviewProb,
    double loanToIncomeRatio,
    String explanation
) {}

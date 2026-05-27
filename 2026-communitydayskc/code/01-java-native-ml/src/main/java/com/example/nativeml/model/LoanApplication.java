package com.example.nativeml.model;

/**
 * Credit/loan application — the raw input from an applicant.
 */
public record LoanApplication(
    String applicantId,
    int    creditScore,       // 300–850
    double annualIncome,      // USD
    double loanAmount,        // USD
    int    employmentYears,   // years at current employer
    double debtToIncomeRatio  // existing debt / annual income (0.0 – 1.0+)
) {}

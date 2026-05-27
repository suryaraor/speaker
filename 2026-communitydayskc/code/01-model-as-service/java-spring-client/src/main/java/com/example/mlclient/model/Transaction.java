package com.example.mlclient.model;

import java.time.Instant;

/**
 * Raw transaction as received from the payment system.
 * Feature engineering transforms this into {@link TransactionFeatures}.
 */
public record Transaction(
    String transactionId,
    String accountId,
    double amount,
    Instant timestamp,
    String merchantId,
    String merchantCategory,
    String location,       // ISO-2 country code
    String ipAddress
) {}

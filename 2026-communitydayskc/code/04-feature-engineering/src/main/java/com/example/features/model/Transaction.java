package com.example.features.model;

import java.time.Instant;

public record Transaction(
    String  transactionId,
    String  accountId,
    double  amount,
    Instant timestamp,
    String  merchantCategory,
    String  countryCode,     // ISO-2
    String  channel          // ONLINE | ATM | POS | MOBILE
) {}

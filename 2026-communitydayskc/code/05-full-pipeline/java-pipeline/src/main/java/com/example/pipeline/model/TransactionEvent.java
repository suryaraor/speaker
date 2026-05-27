package com.example.pipeline.model;

import java.time.Instant;

/** Kafka message payload — published by upstream payment systems. */
public record TransactionEvent(
    String  transactionId,
    String  accountId,
    double  amount,
    Instant timestamp,
    String  merchantCategory,
    String  countryCode,
    String  channel
) {}

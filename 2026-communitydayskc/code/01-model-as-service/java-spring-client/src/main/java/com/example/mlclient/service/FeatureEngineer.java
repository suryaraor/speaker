package com.example.mlclient.service;

import com.example.mlclient.model.Transaction;
import com.example.mlclient.model.TransactionFeatures;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transforms a raw {@link Transaction} into ML-ready {@link TransactionFeatures}.
 *
 * Key insight from the talk: feature engineering lives HERE — in Java — because
 * this is where domain knowledge, business rules, and data access already exist.
 * The Python ML model only ever sees clean, structured numeric features.
 */
@Service
public class FeatureEngineer {

    // In production: use Redis with a TTL-based sliding window
    private final ConcurrentHashMap<String, AtomicInteger> velocityStore = new ConcurrentHashMap<>();

    // Merchant category → risk score (domain knowledge encoded in Java)
    private static final Map<String, Double> MERCHANT_RISK = Map.of(
        "GROCERY",     0.03,
        "RETAIL",      0.05,
        "FUEL",        0.12,
        "RESTAURANTS", 0.08,
        "ELECTRONICS", 0.25,
        "WIRE_TRANSFER", 0.55,
        "CRYPTO",      0.85,
        "GAMBLING",    0.80
    );

    // Country → risk score (sourced from risk management team)
    private static final Map<String, Double> GEO_RISK = Map.of(
        "US", 0.10, "CA", 0.10, "GB", 0.12, "DE", 0.11,
        "FR", 0.12, "AU", 0.11, "JP", 0.10,
        "NG", 0.75, "RO", 0.65, "VN", 0.60
    );

    private static final double DEFAULT_MERCHANT_RISK = 0.30;
    private static final double DEFAULT_GEO_RISK      = 0.30;

    public TransactionFeatures extract(Transaction tx) {
        int hour = tx.timestamp()
            .atOffset(ZoneOffset.UTC)
            .getHour();

        double merchantRisk = MERCHANT_RISK
            .getOrDefault(tx.merchantCategory(), DEFAULT_MERCHANT_RISK);

        double geoRisk = GEO_RISK
            .getOrDefault(tx.location(), DEFAULT_GEO_RISK);

        // Rolling 30-day velocity (in-memory approximation; use Redis in production)
        int velocity = velocityStore
            .computeIfAbsent(tx.accountId(), k -> new AtomicInteger(0))
            .incrementAndGet();

        return new TransactionFeatures(
            tx.amount(),
            hour,
            merchantRisk,
            velocity,
            geoRisk
        );
    }
}

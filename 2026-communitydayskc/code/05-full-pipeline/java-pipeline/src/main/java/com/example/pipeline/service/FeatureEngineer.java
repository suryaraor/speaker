package com.example.pipeline.service;

import com.example.pipeline.model.TransactionEvent;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Lightweight feature extractor for the streaming pipeline. */
@Service
public class FeatureEngineer {

    private final ConcurrentHashMap<String, AtomicInteger> velocity = new ConcurrentHashMap<>();

    private static final Map<String, Float> MERCHANT_RISK = Map.of(
        "GROCERY", 0.03f, "RETAIL", 0.06f, "ELECTRONICS", 0.25f,
        "WIRE_TRANSFER", 0.55f, "CRYPTO", 0.88f, "GAMBLING", 0.82f
    );

    private static final Map<String, Float> GEO_RISK = Map.of(
        "US", 0.10f, "GB", 0.12f, "DE", 0.11f,
        "NG", 0.75f, "RO", 0.65f, "VN", 0.60f
    );

    public float[] extract(TransactionEvent tx) {
        int hour = tx.timestamp().atOffset(ZoneOffset.UTC).getHour();
        float merchantRisk = MERCHANT_RISK.getOrDefault(tx.merchantCategory(), 0.30f);
        float geoRisk      = GEO_RISK.getOrDefault(tx.countryCode(), 0.30f);
        int vel = velocity.computeIfAbsent(tx.accountId(), k -> new AtomicInteger(0))
                          .incrementAndGet();

        return new float[]{
            (float) tx.amount(), (float) hour, merchantRisk, (float) vel, geoRisk
        };
    }
}

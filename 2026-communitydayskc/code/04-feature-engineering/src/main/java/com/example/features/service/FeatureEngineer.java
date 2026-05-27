package com.example.features.service;

import com.example.features.model.Transaction;
import com.example.features.model.TransactionFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates all feature extraction for a transaction.
 * Domain knowledge (risk tables, thresholds) lives here in Java — not in Python.
 */
@Service
public class FeatureEngineer {

    private static final Logger log = LoggerFactory.getLogger(FeatureEngineer.class);

    private final VelocityStore velocityStore;

    public FeatureEngineer(VelocityStore velocityStore) {
        this.velocityStore = velocityStore;
    }

    // Map.of() supports max 10 entries; use Map.ofEntries for larger maps
    private static final Map<String, Double> MERCHANT_RISK = Map.of(
        "GROCERY", 0.03, "RETAIL", 0.06, "RESTAURANTS", 0.07,
        "FUEL", 0.12, "ELECTRONICS", 0.25, "TRAVEL", 0.18,
        "WIRE_TRANSFER", 0.55, "CRYPTO", 0.88, "GAMBLING", 0.82
    );

    private static final Map<String, Double> CHANNEL_RISK = Map.of(
        "ATM", 0.15, "POS", 0.05, "MOBILE", 0.10, "ONLINE", 0.20
    );

    private static final Map<String, Double> GEO_RISK = Map.ofEntries(
        Map.entry("US", 0.10), Map.entry("CA", 0.10), Map.entry("GB", 0.12),
        Map.entry("DE", 0.11), Map.entry("FR", 0.12), Map.entry("AU", 0.11),
        Map.entry("JP", 0.10), Map.entry("SG", 0.11),
        Map.entry("NG", 0.75), Map.entry("RO", 0.65), Map.entry("VN", 0.60),
        Map.entry("PH", 0.55)
    );

    public TransactionFeatures extract(Transaction tx) {
        ZonedDateTime utc = tx.timestamp().atZone(ZoneOffset.UTC);
        int hour      = utc.getHour();
        int dayOfWeek = utc.getDayOfWeek().getValue();

        double merchantRisk = MERCHANT_RISK.getOrDefault(tx.merchantCategory(), 0.30);
        double channelRisk  = CHANNEL_RISK.getOrDefault(tx.channel(), 0.15);
        double geoRisk      = GEO_RISK.getOrDefault(tx.countryCode(), 0.30);

        velocityStore.record(tx.accountId(), tx.amount(), tx.timestamp());
        int    txn1h  = velocityStore.countInLastHours(tx.accountId(), 1);
        int    txn24h = velocityStore.countInLastHours(tx.accountId(), 24);
        int    txn30d = velocityStore.countInLastDays(tx.accountId(), 30);
        double avg7d  = velocityStore.avgAmountInLastDays(tx.accountId(), 7);

        double amountToAvgRatio = (avg7d > 0) ? tx.amount() / avg7d : 1.0;
        double amountNormalized = Math.log1p(tx.amount());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("raw_amount",  tx.amount());
        metadata.put("merchant_category", tx.merchantCategory());
        metadata.put("country_code", tx.countryCode());
        metadata.put("channel", tx.channel());
        metadata.put("is_weekend",  dayOfWeek >= DayOfWeek.SATURDAY.getValue());
        metadata.put("is_overnight", hour < 6 || hour > 22);

        log.debug("Features for tx={}: hour={} merchantRisk={} txn1h={} ratio={}",
            tx.transactionId(), hour, merchantRisk, txn1h, amountToAvgRatio);

        return new TransactionFeatures(
            amountNormalized, hour, dayOfWeek,
            merchantRisk, channelRisk, geoRisk,
            txn1h, txn24h, txn30d, avg7d,
            amountToAvgRatio, metadata
        );
    }
}

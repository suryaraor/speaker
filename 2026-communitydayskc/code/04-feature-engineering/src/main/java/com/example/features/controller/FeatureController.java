package com.example.features.controller;

import com.example.features.model.Transaction;
import com.example.features.model.TransactionFeatures;
import com.example.features.service.FeatureEngineer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/features")
public class FeatureController {

    private final FeatureEngineer featureEngineer;

    public FeatureController(FeatureEngineer featureEngineer) {
        this.featureEngineer = featureEngineer;
    }

    @PostMapping("/extract")
    public ResponseEntity<TransactionFeatures> extract(@RequestBody Transaction tx) {
        return ResponseEntity.ok(featureEngineer.extract(tx));
    }

    @PostMapping("/extract/batch")
    public ResponseEntity<List<TransactionFeatures>> extractBatch(@RequestBody List<Transaction> txns) {
        return ResponseEntity.ok(txns.stream().map(featureEngineer::extract).toList());
    }

    @GetMapping("/demo")
    public ResponseEntity<TransactionFeatures> demo() {
        Transaction tx = new Transaction(
            "TXN-FE-DEMO", "ACC-55555", 1200.00,
            Instant.parse("2026-05-26T02:15:00Z"),
            "ELECTRONICS", "RO", "ONLINE"
        );
        return extract(tx);
    }

    @GetMapping("/demo/velocity")
    public ResponseEntity<List<TransactionFeatures>> demoVelocity() {
        String accountId = "ACC-VEL-DEMO-" + System.currentTimeMillis();
        List<Transaction> txns = List.of(
            new Transaction("TXN-V1", accountId, 50.0,   Instant.now().minusSeconds(3600), "RETAIL",      "US", "POS"),
            new Transaction("TXN-V2", accountId, 200.0,  Instant.now().minusSeconds(1800), "RETAIL",      "US", "POS"),
            new Transaction("TXN-V3", accountId, 800.0,  Instant.now().minusSeconds(600),  "ELECTRONICS", "US", "ONLINE"),
            new Transaction("TXN-V4", accountId, 1500.0, Instant.now().minusSeconds(120),  "CRYPTO",      "NG", "ONLINE"),
            new Transaction("TXN-V5", accountId, 2200.0, Instant.now(),                    "CRYPTO",      "NG", "ONLINE")
        );
        return extractBatch(txns);
    }
}

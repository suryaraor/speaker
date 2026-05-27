package com.example.mlclient.controller;

import com.example.mlclient.model.FraudScore;
import com.example.mlclient.model.Transaction;
import com.example.mlclient.service.FraudDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/fraud")
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);

    private final FraudDetectionService fraudDetectionService;

    public FraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<FraudScore> evaluate(@RequestBody Transaction tx) {
        log.info("Evaluating transaction: {}", tx.transactionId());
        return ResponseEntity.ok(fraudDetectionService.evaluate(tx));
    }

    @PostMapping("/evaluate/async")
    public Mono<FraudScore> evaluateAsync(@RequestBody Transaction tx) {
        return fraudDetectionService.evaluateAsync(tx);
    }

    @GetMapping("/demo/high-risk")
    public ResponseEntity<FraudScore> demoHighRisk() {
        Transaction tx = new Transaction(
            "TXN-DEMO-HIGH", "ACC-12345", 1850.00,
            Instant.parse("2026-05-23T03:15:00Z"),
            "MERCH-CRYPTO-9999", "CRYPTO", "NG", "41.203.0.1"
        );
        return evaluate(tx);
    }

    @GetMapping("/demo/low-risk")
    public ResponseEntity<FraudScore> demoLowRisk() {
        Transaction tx = new Transaction(
            "TXN-DEMO-LOW", "ACC-67890", 42.50,
            Instant.parse("2026-05-23T14:30:00Z"),
            "MERCH-GROCERY-001", "GROCERY", "US", "72.14.0.100"
        );
        return evaluate(tx);
    }
}

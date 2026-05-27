package com.example.pipeline.controller;

import com.example.pipeline.model.FraudAlert;
import com.example.pipeline.service.FraudAlertService;
import com.example.pipeline.service.FraudScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final FraudAlertService             alertService;
    private final FraudScoringService           scoringService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PipelineController(FraudAlertService alertService,
                               FraudScoringService scoringService,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.alertService   = alertService;
        this.scoringService = scoringService;
        this.kafkaTemplate  = kafkaTemplate;
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> alerts() {
        return ResponseEntity.ok(alertService.getRecentAlerts());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "scoring_method", scoringService.isOnnxAvailable() ? "ONNX_IN_JVM" : "REST_FALLBACK",
            "onnx_available", scoringService.isOnnxAvailable(),
            "timestamp", Instant.now()
        ));
    }

    @PostMapping("/test/send")
    public ResponseEntity<Map<String, String>> sendTestTransaction(
            @RequestParam(defaultValue = "TXN-TEST-001") String txnId,
            @RequestParam(defaultValue = "CRYPTO") String merchantCategory,
            @RequestParam(defaultValue = "NG") String countryCode,
            @RequestParam(defaultValue = "1800.0") double amount) {

        record TestEvent(String transactionId, String accountId, double amount,
                         Instant timestamp, String merchantCategory,
                         String countryCode, String channel) {}

        kafkaTemplate.send("transactions", txnId,
            new TestEvent(txnId, "ACC-TEST", amount, Instant.now(),
                          merchantCategory, countryCode, "ONLINE"));
        return ResponseEntity.ok(Map.of("status", "sent", "transactionId", txnId));
    }
}

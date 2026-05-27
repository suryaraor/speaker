package com.example.pipeline.service;

import com.example.pipeline.model.FraudAlert;
import com.example.pipeline.model.TransactionEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);
    private static final double FRAUD_THRESHOLD = 0.75;

    private final FeatureEngineer     featureEngineer;
    private final FraudScoringService scoringService;
    private final FraudAlertService   alertService;
    private final Counter             processedCounter;
    private final Counter             flaggedCounter;

    public TransactionConsumer(FeatureEngineer featureEngineer,
                               FraudScoringService scoringService,
                               FraudAlertService alertService,
                               MeterRegistry registry) {
        this.featureEngineer  = featureEngineer;
        this.scoringService   = scoringService;
        this.alertService     = alertService;
        this.processedCounter = registry.counter("transactions.processed");
        this.flaggedCounter   = registry.counter("transactions.flagged.fraud");
    }

    @KafkaListener(topics = "${kafka.topic.transactions:transactions}", groupId = "fraud-pipeline")
    public void onTransaction(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Received tx={} partition={} offset={}", event.transactionId(), partition, offset);
        processedCounter.increment();

        float[] features = featureEngineer.extract(event);
        double fraudProb = scoringService.score(features);

        if (fraudProb >= FRAUD_THRESHOLD) {
            String method = scoringService.isOnnxAvailable() ? "ONNX_IN_JVM" : "REST_FALLBACK";
            FraudAlert alert = new FraudAlert(
                event.transactionId(), event.accountId(), event.amount(),
                fraudProb, riskLevel(fraudProb), Instant.now(), method
            );
            alertService.raise(alert);
            flaggedCounter.increment();
            log.warn("FRAUD DETECTED: tx={} prob={} method={}", event.transactionId(), fraudProb, method);
        }
    }

    private static String riskLevel(double prob) {
        if (prob > 0.90) return "CRITICAL";
        if (prob > 0.75) return "HIGH";
        return "MEDIUM";
    }
}

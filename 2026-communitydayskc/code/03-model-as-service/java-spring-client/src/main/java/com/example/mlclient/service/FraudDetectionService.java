package com.example.mlclient.service;

import com.example.mlclient.model.FraudScore;
import com.example.mlclient.model.Transaction;
import com.example.mlclient.model.TransactionFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private static final FraudScore SAFE_FALLBACK = new FraudScore(
        0.0, false, "fallback-v0", "LOW", Map.of()
    );

    private final WebClient mlWebClient;
    private final FeatureEngineer featureEngineer;
    private final Duration mlTimeout;

    public FraudDetectionService(
            WebClient mlWebClient,
            FeatureEngineer featureEngineer,
            @Value("${ml.service.timeout-ms:5000}") long timeoutMs) {
        this.mlWebClient     = mlWebClient;
        this.featureEngineer = featureEngineer;
        this.mlTimeout       = Duration.ofMillis(timeoutMs);
    }

    public FraudScore evaluate(Transaction tx) {
        TransactionFeatures features = featureEngineer.extract(tx);
        log.debug("Scoring tx={} features={}", tx.transactionId(), features);
        return callMlService(features)
            .doOnError(e -> log.warn("ML service unreachable for tx={}: {}", tx.transactionId(), e.getMessage()))
            .onErrorReturn(SAFE_FALLBACK)
            .block();
    }

    public Mono<FraudScore> evaluateAsync(Transaction tx) {
        TransactionFeatures features = featureEngineer.extract(tx);
        return callMlService(features).onErrorReturn(SAFE_FALLBACK);
    }

    private Mono<FraudScore> callMlService(TransactionFeatures features) {
        return mlWebClient.post()
            .uri("/predict/fraud")
            .bodyValue(features)
            .retrieve()
            .bodyToMono(FraudScore.class)
            .timeout(mlTimeout);
    }
}

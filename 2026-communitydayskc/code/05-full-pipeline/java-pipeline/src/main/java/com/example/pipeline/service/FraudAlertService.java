package com.example.pipeline.service;

import com.example.pipeline.model.FraudAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FraudAlertService {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertService.class);

    private final KafkaTemplate<String, FraudAlert> kafkaTemplate;
    private final List<FraudAlert> recentAlerts = Collections.synchronizedList(new ArrayList<>());

    private static final String ALERTS_TOPIC = "fraud-alerts";
    private static final int    MAX_RECENT   = 100;

    public FraudAlertService(KafkaTemplate<String, FraudAlert> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void raise(FraudAlert alert) {
        log.warn("Raising fraud alert: tx={} account={} amount={} prob={}",
            alert.transactionId(), alert.accountId(), alert.amount(), alert.fraudProbability());
        kafkaTemplate.send(ALERTS_TOPIC, alert.transactionId(), alert);
        recentAlerts.add(alert);
        if (recentAlerts.size() > MAX_RECENT) {
            recentAlerts.remove(0);
        }
    }

    public List<FraudAlert> getRecentAlerts() {
        return List.copyOf(recentAlerts);
    }
}

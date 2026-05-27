package com.example.features.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory velocity store — tracks transaction counts and rolling averages per account.
 * Production note: replace with Redis ZSET commands for distributed deployments.
 */
@Service
public class VelocityStore {

    private static final Logger log = LoggerFactory.getLogger(VelocityStore.class);

    private final ConcurrentHashMap<String, List<double[]>> history = new ConcurrentHashMap<>();

    public void record(String accountId, double amount, Instant timestamp) {
        history.computeIfAbsent(accountId, k -> Collections.synchronizedList(new ArrayList<>()))
               .add(new double[]{timestamp.toEpochMilli(), amount});
    }

    public int countInLastHours(String accountId, int hours) {
        long cutoff = Instant.now().minusSeconds(hours * 3600L).toEpochMilli();
        return (int) getHistory(accountId).stream().filter(e -> e[0] >= cutoff).count();
    }

    public int countInLastDays(String accountId, int days) {
        return countInLastHours(accountId, days * 24);
    }

    public double avgAmountInLastDays(String accountId, int days) {
        long cutoff = Instant.now().minusSeconds((long) days * 24 * 3600).toEpochMilli();
        return getHistory(accountId).stream()
            .filter(e -> e[0] >= cutoff)
            .mapToDouble(e -> e[1])
            .average()
            .orElse(0.0);
    }

    private List<double[]> getHistory(String accountId) {
        return history.getOrDefault(accountId, Collections.emptyList());
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void evictStaleEntries() {
        long cutoff = Instant.now().minusSeconds(31L * 24 * 3600).toEpochMilli();
        history.forEach((id, events) -> events.removeIf(e -> e[0] < cutoff));
        log.info("Velocity store eviction complete. Accounts tracked: {}", history.size());
    }
}

package com.example.springai.model;

import java.util.List;

/**
 * Structured output produced by Spring AI's ChatClient.
 * The LLM fills this record automatically via reflection-based schema extraction.
 */
public record FraudExplanation(
        String summary,               // One sentence: what happened and why it matters
        List<String> riskFactors,     // Exactly 3 contributing risk factors
        String recommendedAction,     // BLOCK | MANUAL_REVIEW | ALLOW_WITH_ALERT | ALLOW
        String customerMessage,       // Plain-English text if customer contact is needed
        String analystNote,           // Technical detail for the review queue
        int urgencyScore              // 1 (low) – 10 (immediate action required)
) {}

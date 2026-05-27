package com.example.springai.service;

import com.example.springai.model.FraudAlert;
import com.example.springai.model.FraudExplanation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FraudExplainerService {

    private final ChatClient chatClient;

    public FraudExplainerService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Explain a single fraud alert.
     *
     * Spring AI key features demonstrated:
     *   1. ChatClient fluent API            — .prompt().user(...).call()
     *   2. Parameterized prompt template    — .text("...{param}...").param(k, v)
     *   3. Structured output                — .entity(FraudExplanation.class)
     *      Spring AI introspects the record, generates a JSON schema, instructs the
     *      LLM to respond in that schema, then deserialises the result automatically.
     */
    public FraudExplanation explain(FraudAlert alert) {
        return chatClient.prompt()
                .user(u -> u
                        .text("""
                                Analyze the following ML fraud-detection output and return a
                                structured explanation.

                                === TRANSACTION ===
                                ID            : {transactionId}
                                Account       : {accountId}
                                Amount        : {amount} {currency}
                                Merchant cat. : {merchantCategory}
                                Card present  : {cardPresent}
                                Hour of day   : {hour}:00 (24-h)

                                === ML MODEL SCORES (all 0.0–1.0, higher = more risk) ===
                                Fraud probability : {probability}% → {riskLevel} risk
                                Merchant risk     : {merchantRisk}
                                Geographic risk   : {geoRisk}
                                30-day velocity   : {velocity} transactions

                                Return exactly:
                                - summary            : one clear sentence
                                - riskFactors        : list of exactly 3 factors
                                - recommendedAction  : BLOCK | MANUAL_REVIEW | ALLOW_WITH_ALERT | ALLOW
                                - customerMessage    : non-technical, reassuring if appropriate
                                - analystNote        : technical note for the review queue
                                - urgencyScore       : integer 1–10
                                """)
                        .param("transactionId",    alert.transactionId())
                        .param("accountId",        alert.accountId())
                        .param("amount",           String.format("%.2f", alert.amount()))
                        .param("currency",         alert.currency())
                        .param("merchantCategory", alert.merchantCategory())
                        .param("cardPresent",      alert.cardPresent() ? "yes" : "no (card-not-present)")
                        .param("hour",             alert.hourOfDay())
                        .param("probability",      String.format("%.1f", alert.fraudProbability() * 100))
                        .param("riskLevel",        alert.riskLevel())
                        .param("merchantRisk",     String.format("%.2f", alert.merchantRiskScore()))
                        .param("geoRisk",          String.format("%.2f", alert.geoRiskScore()))
                        .param("velocity",         alert.velocity30d())
                )
                .call()
                .entity(FraudExplanation.class);
    }

    /**
     * Free-text fraud Q&A — analysts can ask follow-up questions.
     * Demonstrates the simplest possible Spring AI usage.
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}

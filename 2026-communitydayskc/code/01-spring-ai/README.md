# 01 — Spring AI: LLM-Powered Fraud Explanation

**Pattern:** Spring AI ChatClient → Structured Output → FraudExplanation  
**Port:** 8001

## What This Shows

Examples 02–04 detect fraud using ML models (REST, ONNX, native Java). The Bonus example (05) wires them into a streaming pipeline.  
This example answers the *next* question: **"Why was this flagged?"**

Spring AI translates raw ML scores into structured, human-readable explanations
using a Large Language Model — with zero boilerplate and full provider flexibility.

## Key Spring AI Concepts Demonstrated

| Concept | Code Location |
|---|---|
| `ChatClient` fluent API | `FraudExplainerService.java` |
| Parameterized prompt template | `.text("...{param}...").param(k, v)` |
| Structured output (auto-schema) | `.entity(FraudExplanation.class)` |
| System prompt / persona | `AiConfig.java` |
| Provider swap (OpenAI / Claude / Ollama) | `pom.xml` + `application.properties` |

## Run

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

Or with Ollama (no API key, free):
1. Install Ollama + pull a model: `ollama pull llama3.1`
2. Swap the starter in `pom.xml` to `spring-ai-ollama-spring-boot-starter`
3. Uncomment the Ollama block in `application.properties`
4. `mvn spring-boot:run`

## Endpoints

### POST /api/fraud/explain
Explain a fraud alert from the ML pipeline.

```bash
curl -X POST http://localhost:8006/api/fraud/explain \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-8821",
    "accountId": "ACC-1042",
    "amount": 1850.00,
    "currency": "USD",
    "fraudProbability": 0.92,
    "riskLevel": "HIGH",
    "hourOfDay": 3,
    "merchantRiskScore": 0.80,
    "velocity30d": 22,
    "geoRiskScore": 0.75,
    "merchantCategory": "FUEL",
    "cardPresent": false
  }'
```

**Response:**
```json
{
  "summary": "A high-value card-not-present FUEL charge at 3 AM exceeds normal spending patterns with elevated merchant and geographic risk.",
  "riskFactors": [
    "Card-not-present transaction at 3 AM is statistically anomalous",
    "FUEL merchant category with 0.80 risk score is a known fraud vector",
    "Geographic risk score of 0.75 suggests location mismatch"
  ],
  "recommendedAction": "BLOCK",
  "customerMessage": "We placed a temporary hold on transaction TXN-8821 for your security. Please call us to verify.",
  "analystNote": "92% fraud probability — all three risk dimensions elevated. Velocity (22/30d) is high. Recommend immediate review. Card-not-present FUEL at 0300h matches 'card testing + purchase' fraud pattern.",
  "urgencyScore": 9
}
```

### POST /api/fraud/ask
Free-text Q&A for fraud analysts.

```bash
curl -X POST http://localhost:8006/api/fraud/ask \
  -H "Content-Type: application/json" \
  -d '"Why is a 3 AM FUEL purchase with card-not-present high risk?"'
```

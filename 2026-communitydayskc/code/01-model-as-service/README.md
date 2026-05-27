# 01 — Model as a Service (REST Pattern)

**Pattern:** Python ML model runs as a FastAPI service. Spring Boot calls it over HTTP.

## Architecture

```
[Spring Boot] --POST /predict/fraud--> [FastAPI + RandomForest]
     |                                          |
Feature Engineering (Java)          Inference (Python/sklearn)
Business Logic (Java)               Model versioning (Python)
```

## What this demonstrates

- Feature engineering in the Java layer (where domain knowledge lives)
- Typed REST contract between Java and Python using Pydantic + Jackson records
- Async non-blocking call with `WebClient` and a 200ms SLA timeout
- Graceful fallback when the ML service is unavailable
- Model health and metrics endpoints

## How to Run

### 1. Start the Python ML service

```bash
cd python-ml-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
# API docs: http://localhost:8000/docs
```

### 2. Start the Spring Boot client

```bash
cd java-spring-client
mvn spring-boot:run
```

### 3. Test it

```bash
# Demo endpoint (pre-built high-risk transaction)
curl http://localhost:8080/api/fraud/demo

# Custom transaction
curl -X POST http://localhost:8080/api/fraud/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-001",
    "accountId": "ACC-123",
    "amount": 1850.00,
    "timestamp": "2026-05-26T03:15:00Z",
    "merchantId": "MERCH-9999",
    "merchantCategory": "CRYPTO",
    "location": "NG",
    "ipAddress": "192.168.1.1"
  }'
```

## Key Files

| File | Purpose |
|------|---------|
| `python-ml-service/main.py` | FastAPI server — trains demo model on startup, serves `/predict/fraud` |
| `java-spring-client/.../service/FeatureEngineer.java` | All feature extraction — domain logic stays in Java |
| `java-spring-client/.../service/FraudDetectionService.java` | Calls the ML service with timeout + fallback |
| `java-spring-client/.../controller/FraudController.java` | REST endpoints including async variant |

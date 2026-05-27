# 05 — Full ML Pipeline (Kafka + ONNX + Docker Compose)

**The complete picture.** End-to-end streaming fraud detection pipeline with all the pieces:
Kafka consumer → feature engineering → ML scoring (ONNX or REST) → fraud alert.

## Architecture

```
[Payment System]
     |
     ▼  (Kafka topic: "transactions")
[TransactionConsumer] — Spring @KafkaListener
     |
     ▼
[FeatureEngineer] — Java feature extraction
     |
     ▼
[FraudScoringService] — ONNX in-JVM (fast path)
     |                  REST fallback (if no .onnx file)
     ▼
[FraudAlertService] — publishes to Kafka "fraud-alerts" topic
     |
     ▼  (Kafka topic: "fraud-alerts")
[Downstream] — CRM, Risk Ops, Compliance, Account Freeze
```

## How to Run

### Option A — Docker Compose (full stack, recommended)

```bash
docker-compose up --build
# Starts: Zookeeper, Kafka, Python ML service, Java pipeline, Kafka UI

# Watch the logs
docker-compose logs -f java-pipeline

# Send a test transaction via the REST API
curl -X POST "http://localhost:8084/api/pipeline/test/send?merchantCategory=CRYPTO&countryCode=NG&amount=1800"

# View fraud alerts
curl http://localhost:8084/api/pipeline/alerts

# Kafka UI — visualize topics and messages
open http://localhost:9090
```

### Option B — Local (Java only, REST fallback scoring)

```bash
# Start Kafka locally or point to an existing broker
# Start the Python ML service (see example 01)
cd python-ml-service && uvicorn main:app --port 8000

# Run Java pipeline
cd java-pipeline && mvn spring-boot:run
```

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET  /api/pipeline/alerts` | Recent fraud alerts since startup |
| `GET  /api/pipeline/status` | Shows ONNX vs REST scoring mode |
| `POST /api/pipeline/test/send` | Publish a test transaction to Kafka |
| `GET  /actuator/health` | Spring Boot health check |

## Live Demo Script (for the talk)

```bash
# 1. Start everything
docker-compose up --build

# 2. Check scoring mode
curl http://localhost:8084/api/pipeline/status

# 3. Send a high-risk transaction
curl -X POST "http://localhost:8084/api/pipeline/test/send?merchantCategory=CRYPTO&countryCode=NG&amount=2500&txnId=LIVE-DEMO-001"

# 4. Check the alert was raised
curl http://localhost:8084/api/pipeline/alerts

# 5. Open Kafka UI to see the "fraud-alerts" topic
open http://localhost:9090
```

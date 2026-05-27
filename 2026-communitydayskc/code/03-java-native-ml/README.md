# 03 — Java-Native ML (Weka)

**Pattern:** Train and serve a machine learning classifier entirely in Java — no Python, no ONNX, no external model files.

## Architecture

```
[Spring Boot startup]
    |
    ▼
CreditRiskClassifier.initialize()
    |
    ├── Builds Weka Instances structure (feature schema)
    ├── Generates synthetic training data
    └── Trains RandomForest via Weka API
         |
         ▼
    Model lives in JVM memory — ready to predict instantly
```

## Why Java-Native ML?

| Use Case | Why this fits |
|----------|---------------|
| Air-gapped/regulated environments | Zero external dependencies |
| Simple tabular models | Don't need Python for a RandomForest |
| Compliance requirements | Full auditability in the Java codebase |
| Teams without Python expertise | Everyone can own and modify it |

## How to Run

```bash
mvn spring-boot:run
```

No setup required — the model trains automatically at startup.

## Test it

```bash
# Strong application — expect APPROVE
curl http://localhost:8082/api/credit/demo/approve

# Weak application — expect DENY
curl http://localhost:8082/api/credit/demo/deny

# Borderline — expect REVIEW
curl http://localhost:8082/api/credit/demo/review

# Custom application
curl -X POST http://localhost:8082/api/credit/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "applicantId": "APP-999",
    "creditScore": 690,
    "annualIncome": 65000,
    "loanAmount": 22000,
    "employmentYears": 4,
    "debtToIncomeRatio": 0.38
  }'
```

## Sample Response

```json
{
  "decision": "REVIEW",
  "approveProb": 0.28,
  "denyProb": 0.18,
  "reviewProb": 0.54,
  "loanToIncomeRatio": 0.338,
  "explanation": "Borderline case — loan-to-income 0.34 requires manual review."
}
```

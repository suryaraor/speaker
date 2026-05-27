# 04 — Feature Engineering in Java

**The most important example.** This is the pattern that makes ML integration work in enterprise systems.
Feature engineering is where the domain knowledge lives — and that knowledge belongs in Java.

## What this demonstrates

| Pattern | Class | Description |
|---------|-------|-------------|
| Temporal features | `FeatureEngineer` | Hour of day, day of week — time-based risk signals |
| Categorical encoding | `FeatureEngineer` | Merchant category → risk float via domain lookup |
| Velocity features | `VelocityStore` | Rolling 1h / 24h / 30d transaction counts |
| Amount normalization | `FeatureEngineer` | log1p(amount) — reduces skew from large outliers |
| Derived ratios | `FeatureEngineer` | amount / rolling_avg — detects abnormal amounts |
| Explainability metadata | `TransactionFeatures` | Raw values preserved alongside normalized features |

## Key insight from the talk

> Feature engineering belongs in Java because:
> 1. Domain knowledge is already in Java code (merchant risk tables, geo risk, business rules)
> 2. Business data access (databases, caches) is already wired up in Java
> 3. The ML model should only see clean, numeric features — not raw domain objects

## How to Run

```bash
mvn spring-boot:run
```

## Test it

```bash
# Single transaction feature extraction
curl http://localhost:8083/api/features/demo

# Watch velocity features grow across 5 rapid transactions
curl http://localhost:8083/api/features/demo/velocity

# Custom transaction
curl -X POST http://localhost:8083/api/features/extract \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-001",
    "accountId": "ACC-100",
    "amount": 750.0,
    "timestamp": "2026-05-26T02:30:00Z",
    "merchantCategory": "ELECTRONICS",
    "countryCode": "RO",
    "channel": "ONLINE"
  }'
```

## Sample Response

```json
{
  "amountNormalized": 6.621,
  "hourOfDay": 2,
  "dayOfWeek": 1,
  "merchantRiskScore": 0.25,
  "channelRiskScore": 0.20,
  "geoRiskScore": 0.65,
  "txn1h": 1,
  "txn24h": 1,
  "txn30d": 1,
  "avgAmount7d": 750.0,
  "amountToAvgRatio": 1.0,
  "metadata": {
    "raw_amount": 750.0,
    "is_overnight": true,
    "is_weekend": false,
    "merchant_category": "ELECTRONICS",
    "country_code": "RO",
    "channel": "ONLINE"
  }
}
```

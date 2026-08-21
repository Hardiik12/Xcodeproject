# Phase 7.6 — ML Prediction Serving & API Architecture

## Executive Summary

This document describes the prediction layer architecture, feature validation rules, batch size bounds, ModelRegistry integration, preprocessor transform isolation, and security controls enforced by `PredictionService` and the `POST /api/v1/ml/predict` API endpoint.

---

## 1. Prediction Architecture & Pipeline Flow

```
POST /api/v1/ml/predict (Raw HTTP Request)
               │
               ▼
   1. Payload & Pydantic Schema Validation (Batch size <= 100)
               │
               ▼
   2. Target & PII Isolation Check (Forbidden: target_*, email, phone, etc.)
               │
               ▼
   3. Active Model Discovery (ModelRegistry.get_active_model())
               │
               ▼
   4. Readiness & Contract Check (features-v1 & analytics-contract-v1)
               │
               ▼
   5. Preprocessor Ingestion (Transform-only; NO fit/fit_transform)
               │
               ▼
   6. Estimator Inference & Output Finite Validation (Non-negative float)
               │
               ▼
   7. Structured Response Construction (includes request_id & metrics)
```

---

## 2. API Endpoints & Contracts

### `POST /api/v1/ml/predict`

#### Request Payload Schema
```json
{
  "records": [
    {
      "content_id": 101,
      "date": "2026-08-20",
      "platform": "IOS",
      "sessions": 150.0,
      "plays": 120.0,
      "unique_viewers": 110.0,
      "watch_time_seconds": 14400.0,
      "completed_plays": 90.0,
      "completion_rate": 0.75
    }
  ]
}
```

#### Successful Response Schema (HTTP 200)
```json
{
  "success": true,
  "model_name": "plays_predictor",
  "model_version": "plays-forecast-v1",
  "algorithm": "ridge_regression",
  "target": "target_next_day_plays",
  "prediction_count": 1,
  "predictions": [
    {
      "content_id": 101,
      "date": "2026-08-20",
      "platform": "IOS",
      "predicted_next_day_plays": 134.125
    }
  ],
  "request_id": "req-98f21a42-8c11-4702"
}
```

---

## 3. Validation & Error Handling Contracts

| HTTP Status | Error Code | Trigger Condition |
| :--- | :--- | :--- |
| **400 Bad Request** | `BATCH_SIZE_EXCEEDED` | Request records count > 100 |
| **400 Bad Request** | `TARGET_COLUMN_PRESENT` | Request contains `target_next_day_plays` |
| **400 Bad Request** | `PII_SUPPLIED` | Request contains `user_id`, `email`, `phone`, etc. |
| **400 Bad Request** | `FEATURE_VALIDATION_FAILED` | Negative metrics or invalid completion rate |
| **503 Service Unavailable** | `MODEL_UNAVAILABLE` | No active validated model in registry |
| **503 Service Unavailable** | `MODEL_INCOMPATIBLE` | Mismatch in feature schema, contract, or target |
| **500 Internal Error** | `MODEL_OUTPUT_INVALID` | Model produced NaN or Infinity predictions |

---

## 4. Security & Isolation Invariants

- **No Data Mutation**: Preprocessor `fit()` and `fit_transform()` are NEVER called during inference.
- **Zero Database Access**: No PostgreSQL, Redis, or MinIO network queries executed during prediction.
- **Zero PII Leakage**: Technical exceptions are caught; raw Python stack traces and file paths are stripped from HTTP responses.
- **Deterministic**: Inferences use fixed random states and deterministic matrix transformations.

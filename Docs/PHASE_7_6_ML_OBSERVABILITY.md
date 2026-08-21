# Phase 7.6 — ML Serving Hardening & Observability Architecture

## Executive Summary

This document details the operational hardening, health probes, readiness validation, structured logging, in-process prediction metrics, request ID correlation, and failure modes implemented in the CommunityOTT Analytics Python service.

---

## 1. Health & Readiness Probes

### Liveness Probe: `GET /api/v1/analytics/health`
- **Purpose**: Verifies that the FastAPI process is alive and accepting connections.
- **Contract**: HTTP 200 `{"status": "UP"}`.
- **Independence**: Does NOT depend on ML model loading or artifact availability. If model initialization fails, liveness remains HTTP 200 `UP`.

### Readiness Probe: `GET /api/v1/analytics/ready`
- **Purpose**: Verifies that the service can safely serve predictions.
- **Integrity Invariants**:
  1. Active model entry exists in `ModelRegistry`.
  2. Persistent model `.joblib` and preprocessor `.joblib` exist on disk.
  3. SHA-256 checksum validation matches registered manifest.
  4. Feature schema version equals `features-v1`.
  5. Analytics contract version equals `analytics-contract-v1`.
  6. Target contract equals `target_next_day_plays`.
- **Response**:
  - Valid: HTTP 200 `{"status": "READY", "model": "plays_predictor", "model_version": "plays-forecast-v1"}`.
  - Invalid / Unavailable: HTTP 503 `{"status": "NOT_READY", "reason": "MODEL_UNAVAILABLE"}`.

---

## 2. In-Process Prediction Metrics (`GET /api/v1/ml/metrics`)

Thread-safe operational metrics tracked via `PredictionMetricsTracker`:
```json
{
  "prediction_requests": {
    "total": 120,
    "successful": 118,
    "failed": 2
  },
  "latency_ms": {
    "count": 118,
    "average": 14.32,
    "min": 4.12,
    "max": 38.50
  },
  "batch_size": {
    "count": 118,
    "average": 4.50,
    "max": 50
  }
}
```
- **Privacy Assurance**: Omits individual feature vectors, raw predictions, user IDs, or filesystem paths.

---

## 3. Safe Structured Logging & Request IDs

- **Request ID Propagation**: Reuses `X-Request-ID` header if present; generates UUID if omitted. Appears in response headers, structured logs, and structured error envelopes.
- **Log Privacy Controls**: Never logs passwords, tokens, Authorization headers, PII (`user_id`, `email`, `phone`, `device_id`, `session_id`), or raw input/output payloads.

---

## 4. Startup & Failure Modes

| State | Liveness | Readiness | Prediction Endpoint |
| :--- | :--- | :--- | :--- |
| **Normal Operation** | HTTP 200 `UP` | HTTP 200 `READY` | HTTP 200 Prediction Response |
| **Model Artifact Missing** | HTTP 200 `UP` | HTTP 503 `NOT_READY` | HTTP 503 `MODEL_UNAVAILABLE` |
| **Schema/Contract Mismatch** | HTTP 200 `UP` | HTTP 503 `NOT_READY` | HTTP 503 `MODEL_INCOMPATIBLE` |
| **Invalid Output (NaN/Inf)** | HTTP 200 `UP` | HTTP 200 `READY` | HTTP 500 `MODEL_OUTPUT_INVALID` |

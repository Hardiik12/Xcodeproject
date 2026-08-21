# Phase 7.6 — Model Registry & Artifact Persistence Architecture

## Executive Summary

This document describes the versioned model registry, artifact package layout, SHA-256 checksum verification, schema compatibility enforcement, and atomic activation flow implemented in the CommunityOTT Analytics Python service.

---

## 1. Registry Architecture & Folder Layout

The `ModelRegistry` operates on local file-based storage configured via `MODEL_ARTIFACT_DIR` environment settings.

### Artifact Package Directory Structure
```
artifacts/models/
├── registry.json                    # Central manifest listing model versions
└── plays-predictor-v1/              # Versioned model package
    ├── model.joblib                 # Trained Scikit-Learn estimator
    ├── preprocessor.joblib          # ColumnTransformer preprocessing pipeline
    ├── model_card.json              # Model card governance metadata
    ├── metadata.json                # Metadata contract & split statistics
    └── checksums.json               # SHA-256 checksum manifest
```

---

## 2. SHA-256 Checksum Strategy & Integrity Verification

Every registered model package contains `checksums.json`:
```json
{
  "algorithm": "SHA-256",
  "files": {
    "model.joblib": "<sha256-hex>",
    "preprocessor.joblib": "<sha256-hex>",
    "model_card.json": "<sha256-hex>",
    "metadata.json": "<sha256-hex>"
  }
}
```

### Pre-Activation Integrity Protocol
Before any model becomes `ACTIVE` or is loaded into memory:
1. Verify package directory and files exist.
2. Re-compute SHA-256 hex digest for all package files.
3. Compare against registered `checksums.json` and registry manifest hashes.
4. If hash mismatch occurs, set state `MODEL_INVALID` and reject activation.

---

## 3. Compatibility Enforcement

The registry validates 3 required invariants before deserializing artifacts:
1. **Feature Schema**: Must equal `features-v1`.
2. **Analytics Contract**: Must equal `analytics-contract-v1`.
3. **Target Contract**: Must equal `target_next_day_plays`.

If any contract version or target name differs, `ModelCompatibilityError` is raised and readiness probe reports `status="NOT_READY"`.

---

## 4. Atomic Activation & Rollback Safety

Activation follows a zero-downtime, safe sequence:
```
Candidate Package
      │
      ▼
Integrity Check (SHA-256)
      │
      ▼
Contract & Schema Check
      │
      ▼
Deserialization Load Test
      │
      ├── (Pass) ──► Mark ACTIVE (Previous ACTIVE ──► ARCHIVED)
      └── (Fail) ──► Keep Previous Active Model (Zero Mutation)
```

---

## 5. Security & Privacy Boundaries

- **Zero PII**: No `user_id`, `email`, `phone`, `name`, IP addresses, or session tokens in registry metadata.
- **No Path Exposure**: API endpoints (`/api/v1/ml/registry` and `/api/v1/ml/registry/verify`) omit absolute local filesystem paths.
- **Arbitrary Path Protection**: Registry loading accepts only `(model_name, model_version)` identifiers; user-supplied filesystem paths are rejected.

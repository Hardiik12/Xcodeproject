# Phase 7.6 — ML Model Benchmarking Report

## Executive Summary

This document presents the reproducible benchmark comparing forecasting approaches for predicting `target_next_day_plays` across time-series data partitions in the CommunityOTT Analytics Python service.

Target: `target_next_day_plays`  
Data Source: `SYNTHETIC_FIXTURE`  
Production Data Available: `false`  
Production Evaluation Available: `false`  

---

## 1. Evaluated Models

1. **Naive Baseline** (`naive_previous_day_plays`):
   - Formula: $\hat{y}_{t+1} = y_t$ (predicts current day's plays for next day)
   - Parameters: Non-parametric baseline

2. **Tree Ensemble** (`RandomForestRegressor`):
   - Hyperparameters: `n_estimators=200`, `max_depth=10`, `min_samples_leaf=2`, `random_state=42`, `n_jobs=-1`
   - Preprocessing: `ColumnTransformer` (`StandardScaler` + `OneHotEncoder`) fit strictly on $X_{\text{train}}$

3. **Linear Model** (`Ridge Regression`):
   - Hyperparameters: `alpha=1.0`, `random_state=42`
   - Preprocessing: `ColumnTransformer` (`StandardScaler` + `OneHotEncoder`) fit strictly on $X_{\text{train}}$

---

## 2. Dataset Partitioning & Leakage Controls

- **Chronological Split**: 70% Train / 15% Validation / 15% Test based on UTC dates.
- **Split Invariant**: $\max(\text{train\_date}) < \min(\text{val\_date}) < \min(\text{test\_date})$
- **Target Isolation**: Target columns (`target_next_day_plays`, etc.) strictly removed from feature inputs $X$.
- **Preprocessing Isolation**: `ColumnTransformer` fitted ONLY on $X_{\text{train}}$. Validation and test features transformed using training scaling parameters without target or future leakage.
- **PII Compliance**: Excludes sensitive attributes (`user_id`, `email`, `phone`, `name`, IP addresses).

---

## 3. Comparative Benchmark Results

### Validation Set Performance

| Model | MAE | RMSE | $R^2$ | Validation MAE Improvement vs Baseline |
| :--- | ---: | ---: | ---: | ---: |
| **Naive Baseline** | $12.0000$ | $12.0000$ | $0.9632$ | $0.00\%$ |
| **Random Forest** | $15.8550$ | $19.3409$ | $0.9045$ | $-32.13\%$ |
| **Ridge Regression** | **$0.8295$** | **$0.9381$** | **$0.9998$** | **$+93.09\%$** |

### Test Set Performance (Final Unbiased Evaluation)

| Model | MAE | RMSE | $R^2$ | Test MAE Improvement vs Baseline | Generalization Status |
| :--- | ---: | ---: | ---: | ---: | :--- |
| **Naive Baseline** | $12.0000$ | $12.0000$ | $0.9701$ | $0.00\%$ | N/A |
| **Random Forest** | $43.7100$ | $45.4542$ | $0.5709$ | $-264.25\%$ | `GENERALIZATION_RISK` |
| **Ridge Regression** | **$1.2091$** | **$1.2909$** | **$0.9913$** | **$+89.92\%$** | `STABLE` |

---

## 4. Diagnostic Analysis

### Tree Ensemble Extrapolation Limits
- Decision tree splits segment input space into axis-aligned hyperplanes.
- Consequently, `RandomForestRegressor` predictions are bounded by $\max(y_{\text{train}}) = 306.0$.
- In the linearly increasing synthetic fixture, Random Forest severely underpredicts future unseen time horizons, resulting in large test set degradation.

### Linear Model Trend Representation
- `Ridge(alpha=1.0)` extrapolates the positive linear trajectory across feature combinations (e.g. `plays`, `sessions`, `1d lags`), maintaining high accuracy into future time windows.
- Test degradation ratio: $1.2091 / 0.8295 = 1.4576 \le 1.50$ (Threshold PASS: `STABLE`).

---

## 5. Model Selection Recommendation

**Recommended Candidate**: `Ridge Regression` (`ridge_regression`)  
**Selection Reason**: Selected based strictly on lowest Validation MAE ($0.8295$) with $+93.09\%$ improvement over Naive baseline.

---

## 6. Limitations & Disclosure

> [!IMPORTANT]
> All metrics reported in this benchmark are derived strictly from `SYNTHETIC_FIXTURE` data. Production data is NOT available. Production model deployment and evaluation have NOT occurred.

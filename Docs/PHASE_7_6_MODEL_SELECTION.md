# Phase 7.6 — Model Candidate Selection & Model Card Specification

## Executive Summary

This document describes the validation-driven selection framework, baseline protection rules, tie-breaking criteria, model card structure, and privacy controls implemented for model candidate selection in the CommunityOTT Analytics Python service.

---

## 1. Selection Methodology & Rules

Model selection is strictly **validation-driven**. The test partition is reserved for final evaluation ONLY and is NEVER accessed during selection decision-making.

### Rule 1: Validation Metric Ranking
1. **Primary Metric**: Lowest Validation MAE.
2. **Secondary Metric (Tie-breaker 1)**: Lowest Validation RMSE.
3. **Tertiary Metric (Tie-breaker 2)**: Highest Validation $R^2$.

### Rule 2: Baseline Protection (5% Improvement Threshold)
A learned model is selected over the Naive Baseline (`naive_previous_day_plays`) ONLY if it achieves at least a 5% relative MAE improvement on the validation set:
$$\text{learned\_model\_MAE} < \text{baseline\_MAE} \times 0.95$$

If no learned model beats the baseline by $\ge 5\%$, the baseline is retained:
- `selection_status = "BASELINE_RETAINED"`
- `production_status = "BASELINE RETAINED"`

### Rule 3: Model Simplicity & Tie-Breaking (1% MAE Tolerance)
If candidate models are effectively tied within a 1% absolute validation MAE tolerance:
$$\frac{|\text{MAE}_A - \text{MAE}_B|}{\max(\text{MAE}_A, \text{MAE}_B)} \le 0.01$$

The simpler model is selected according to the complexity order:
1. **Naive Baseline** (`naive_previous_day_plays`)
2. **Ridge Regression** (`ridge_regression`)
3. **Random Forest** (`random_forest`)

---

## 2. Test Set Protection & Generalization Stability

- **Test Partition Isolation**: Selection logic operates exclusively on `X_val` / `y_val`.
- **Degradation Ratio**: Post-selection, test MAE is evaluated once:
$$\text{degradation\_ratio} = \frac{\text{Test MAE}}{\text{Validation MAE}}$$
- **Stability Threshold**: `degradation_ratio <= 2.0` is designated `STABLE`. Otherwise flagged as `POTENTIAL_INSTABILITY`.

---

## 3. Selected Model Candidate Summary

- **Selected Model**: `ridge_regression`
- **Algorithm**: `Ridge(alpha=1.0, random_state=42)`
- **Selection Status**: `LEARNED_MODEL_SELECTED`
- **Production Status**: `PROVISIONAL MODEL CANDIDATE`
- **Validation MAE**: $0.8295$ vs Baseline $12.0000$ (+93.09% improvement)
- **Test MAE**: $1.2091$ vs Baseline $12.0000$ (+89.92% improvement)
- **Degradation Ratio**: $1.4576 \le 2.0$ (`STABLE`)

---

## 4. Model Card Governance & Privacy Controls

The `ModelCard` schema documents metadata, target contracts, feature schema versions, data sources, and known limitations.

### Privacy Enforcement
- **Zero PII Exposure**: No `user_id`, `email`, `phone`, `name`, IP address, device ID, or session tokens.
- **No Raw Data Leakage**: Exposes only aggregate statistical metrics and model metadata.
- **No Credentials**: Exposes no database connections, secrets, or file paths.

---

## 5. Synthetic Data Disclosure & Known Limitations

> [!IMPORTANT]
> - `training_data_source = SYNTHETIC_FIXTURE`
> - `production_data_available = false`
> - `production_evaluation_available = false`
> - Tree ensemble (`RandomForestRegressor`) cannot extrapolate beyond maximum training target values ($306.0$).
> - Candidate selection is based on synthetic deterministic data and is not deployed to production.

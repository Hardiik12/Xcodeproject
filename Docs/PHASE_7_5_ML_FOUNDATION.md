# CommunityOTT — Phase 7.5: Machine Learning Foundation & Feature Engineering

## 1. Scope & ML Boundaries

Phase 7.5 establishes the **Machine Learning Foundation and Feature Engineering Layer** for the CommunityOTT analytics service.

> [!IMPORTANT]
> **Production Boundary Declaration**:
> Phase 7.5 establishes ML-ready feature datasets, chronological validation splitting, and scikit-learn preprocessing pipelines, but **does NOT implement production model training or inference**.
> The recommendation system, personalized feed rankings, and predictive endpoints are deferred to subsequent ML phases.

```
Spring Boot Monolith
        │
        │ HTTP GET /api/v1/analytics/export (analytics-contract-v1)
        ▼
AnalyticsClient (app/clients/)
        │
        ▼
Pydantic Contract Validation (app/schemas/contract.py)
        │
        ▼
Pandas / NumPy Data Processing Layer (app/processing/)
        │
        ▼
Phase 7.5 Feature Engineering (app/ml/features/)
        ├── engagement_features.py (plays/viewer, watch/session, error rates)
        ├── temporal_features.py   (UTC day/month/quarter/weekend)
        ├── content_features.py    (relative market shares)
        └── feature_builder.py     (lags, 7d rolling, growth, leakage validation)
        │
        ▼
Chronological Train / Val / Test Splitter (app/ml/datasets/dataset_builder.py)
        │
        ├── Train Split (70% earliest dates)  ───► Fits ColumnTransformer Preprocessor
        ├── Val Split   (15% middle dates)    ───► Transformed by fitted Preprocessor
        └── Test Split  (15% latest dates)    ───► Transformed by fitted Preprocessor
        │
        ▼
Preprocessed ML-Ready Feature Arrays (X_train, X_val, X_test) + Metadata
```

---

## 2. Feature Schema & Grain

- **Base Analytical Grain**: `content_id + date + platform` (No user-level rows).
- **Feature Schema Version**: `features-v1`
- **Data Ingestion Standard**: Versioned `analytics-contract-v1` stream only.

### Registered Feature Inventory

| Group | Feature Name | Dtype | Range | Description / Formula |
| :--- | :--- | :--- | :--- | :--- |
| **Volume** | `sessions` | `int64` | $\ge 0$ | Total playback sessions |
| **Volume** | `plays` | `int64` | $\ge 0$ | Total play initiations |
| **Volume** | `unique_viewers` | `int64` | $\ge 0$ | Distinct viewer count |
| **Volume** | `watch_time_seconds`| `int64` | $\ge 0$ | Cumulative watch time |
| **Volume** | `completed_plays` | `int64` | $\ge 0$ | Completed plays |
| **Volume** | `buffering_events` | `int64` | $\ge 0$ | Player buffering stalls |
| **Volume** | `playback_errors` | `int64` | $\ge 0$ | Playback error count |
| **Volume** | `quality_changes` | `int64` | $\ge 0$ | Bitrate switch events |
| **Engagement** | `plays_per_session` | `float64` | $\ge 0.0$ | $\text{plays} / \text{sessions}$ |
| **Engagement** | `plays_per_viewer` | `float64` | $\ge 0.0$ | $\text{plays} / \text{unique\_viewers}$ |
| **Engagement** | `watch_time_per_play`| `float64` | $\ge 0.0$ | $\text{watch\_time\_seconds} / \text{plays}$ |
| **Engagement** | `watch_time_per_session`| `float64`| $\ge 0.0$ | $\text{watch\_time\_seconds} / \text{sessions}$ |
| **Engagement** | `completion_rate` | `float64` | $[0.0, 1.0]$| $\text{completed\_plays} / \text{plays}$ |
| **Engagement** | `completed_plays_ratio`| `float64`| $[0.0, 1.0]$| $\text{completed\_plays} / \text{sessions}$ |
| **Engagement** | `buffering_rate` | `float64` | $\ge 0.0$ | $\text{buffering\_events} / \text{plays}$ |
| **Engagement** | `error_rate` | `float64` | $\ge 0.0$ | $\text{playback\_errors} / \text{plays}$ |
| **Engagement** | `quality_change_rate`| `float64` | $\ge 0.0$ | $\text{quality\_changes} / \text{plays}$ |
| **Temporal** | `day_of_week` | `int64` | $0 \dots 6$ | ISO day of week in UTC ($0=\text{Monday}, 6=\text{Sunday}$) |
| **Temporal** | `day_of_month` | `int64` | $1 \dots 31$ | Day of month in UTC |
| **Temporal** | `day_of_year` | `int64` | $1 \dots 366$| Day of year in UTC |
| **Temporal** | `week_of_year` | `int64` | $1 \dots 53$ | ISO calendar week in UTC |
| **Temporal** | `month` | `int64` | $1 \dots 12$ | Calendar month in UTC |
| **Temporal** | `quarter` | `int64` | $1 \dots 4$ | Calendar quarter in UTC |
| **Temporal** | `is_weekend` | `int64` | $0, 1$ | 1 if Saturday or Sunday, else 0 |
| **Content** | `content_play_share` | `float64` | $[0.0, 1.0]$| $\text{plays} / \sum \text{daily\_plays}$ |
| **Content** | `content_watch_time_share`| `float64`| $[0.0, 1.0]$| $\text{watch\_time} / \sum \text{daily\_watch\_time}$ |
| **Content** | `content_viewer_share`| `float64` | $[0.0, 1.0]$| $\text{viewers} / \sum \text{daily\_viewers}$ |
| **Lag (1D)** | `plays_lag_1d` | `float64` | $\ge 0.0$ | Prior day plays for $(\text{content\_id}, \text{platform})$ |
| **Lag (1D)** | `watch_time_lag_1d` | `float64` | $\ge 0.0$ | Prior day watch time for $(\text{content\_id}, \text{platform})$ |
| **Lag (1D)** | `viewers_lag_1d` | `float64` | $\ge 0.0$ | Prior day viewers for $(\text{content\_id}, \text{platform})$ |
| **Lag (1D)** | `completion_rate_lag_1d`| `float64`| $[0.0, 1.0]$| Prior day completion rate |
| **Rolling (7D)**| `plays_rolling_7d` | `float64` | $\ge 0.0$ | 7-day cumulative plays (current day + 6 past days) |
| **Rolling (7D)**| `watch_time_rolling_7d`| `float64`| $\ge 0.0$ | 7-day cumulative watch time |
| **Rolling (7D)**| `viewers_rolling_7d`| `float64` | $\ge 0.0$ | 7-day cumulative unique viewers |
| **Rolling (7D)**| `completion_rate_rolling_7d`| `float64`| $[0.0, 1.0]$| 7-day cumulative completed plays / 7-day plays |
| **Growth** | `plays_growth_1d` | `float64` | $\ge -1.0$ | Day-over-day plays growth ratio |
| **Growth** | `watch_time_growth_1d`| `float64` | $\ge -1.0$ | Day-over-day watch time growth ratio |
| **Growth** | `viewer_growth_1d` | `float64` | $\ge -1.0$ | Day-over-day viewers growth ratio |

---

## 3. Data Leakage Prevention & Chronological Splitting

### A. Chronological Splitting Strategy
Random splits (`train_test_split`) are **strictly prohibited** in time-series forecasting.
- Datasets are sorted strictly by calendar date in UTC.
- Splitting enforces:
  $$\max(\text{Train Dates}) < \min(\text{Validation Dates}) \le \max(\text{Validation Dates}) < \min(\text{Test Dates})$$
- Default split proportions: **70% Train, 15% Validation, 15% Test**.

### B. Preprocessing Fit Isolation
- The `scikit-learn` `ColumnTransformer` is **fit ONLY on the Training split**.
- The Validation and Test splits are transformed using the fitted Training statistics (mean, variance, mode).

### C. Target Variable Isolation
Optional supervised target columns (`target_next_day_plays`, `target_next_day_watch_time`, `target_next_day_completion_rate`) are computed via `shift(-1)`:
- Target columns are **strictly forbidden** in input feature matrices.
- `validate_features(df, is_input_features_only=True)` actively raises `MLFeatureError` if any target column is detected in model input datasets.

---

## 4. Preprocessing Pipeline Specification

Built with `scikit-learn` (`ColumnTransformer`):
- **Numeric Pipeline**:
  - Imputation: `SimpleImputer(strategy="median")`
  - Scaling: `StandardScaler()`
- **Categorical Pipeline**:
  - Imputation: `SimpleImputer(strategy="most_frequent")`
  - Encoding: `OneHotEncoder(handle_unknown="ignore", sparse_output=False)`

---

## 5. Metadata API

Exposes immutable feature definitions and versions without exposing raw training rows or PII:

- **Endpoint**: `GET /api/v1/ml/features/metadata`
- **Schema**:
  ```json
  {
    "feature_schema_version": "features-v1",
    "source_contract_version": "analytics-contract-v1",
    "generated_at": "2026-08-20T00:00:00Z",
    "from": "2026-08-01",
    "to": "2026-08-15",
    "row_count": 1400,
    "feature_count": 39,
    "features": [ ... ]
  }
  ```

---

## 6. Privacy & Security Bounds

- **Zero PII**: No user identifiers, device IDs, IP addresses, tokens, or emails.
- **Zero Database Connections**: Operates solely as an HTTP client to Spring Boot's `GET /api/v1/analytics/export`.

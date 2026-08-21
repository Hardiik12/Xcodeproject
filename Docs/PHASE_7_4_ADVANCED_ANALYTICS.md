# CommunityOTT — Phase 7.4: Advanced Statistical & Business Analytics

## 1. Overview & Architectural Boundaries

Phase 7.4 introduces the **Advanced Statistical and Business Analytics Layer** for the CommunityOTT platform.

```
Spring Boot Monolith
        |
        | GET /api/v1/analytics/export (analytics-contract-v1)
        v
AnalyticsClient (app/clients/)
        |
        v
Pydantic Contract Validation (app/schemas/contract.py)
        |
        v
Pandas/NumPy Processing Layer (app/processing/)
        |
        v
Phase 7.4 Advanced Analytics Layer (app/analytics/)
        ├── engagement.py     (Viewing metrics & reliability rates)
        ├── performance.py    (Composite content performance scoring)
        ├── growth.py         (Period-over-period & daily trends)
        ├── distributions.py  (Sample statistics & percentiles P25-P95)
        ├── anomalies.py      (Deterministic IQR outlier detection)
        └── insights.py       (Rule-based catalog & platform heuristics)
        |
        v
AnalyticsAdvancedService (app/services/analytics_advanced_service.py)
        |
        v
FastAPI Endpoints (/api/v1/analytics/advanced/*)
```

> [!IMPORTANT]
> **Machine Learning Boundary**: This phase implements **strictly deterministic statistical algorithms and business analytics heuristics** using Pandas and NumPy.
> **No Machine Learning frameworks** (such as scikit-learn, TensorFlow, PyTorch, XGBoost, LightGBM, Dask, or Spark) are present or utilized.
> **Zero Database Access**: The Python service operates entirely as an HTTP consumer of `analytics-contract-v1` and holds no database connections to PostgreSQL, Redis, or MinIO.

---

## 2. Mathematical Formulas & Conventions

### A. Engagement & Reliability Analytics (`app/analytics/engagement.py`)
All division operations use zero-denominator guards returning `0.0`:

- **Overall Completion Rate**:
  $$\text{completion\_rate} = \frac{\sum \text{completed\_plays}}{\sum \text{plays}} \quad (\text{or } 0.0 \text{ if } \sum \text{plays} = 0)$$
- **Average Watch Time per Play**:
  $$\text{avg\_watch\_per\_play} = \frac{\sum \text{watch\_time\_seconds}}{\sum \text{plays}} \quad (\text{or } 0.0)$$
- **Average Watch Time per Session**:
  $$\text{avg\_watch\_per\_session} = \frac{\sum \text{watch\_time\_seconds}}{\sum \text{sessions}} \quad (\text{or } 0.0)$$
- **Plays per Unique Viewer**:
  $$\text{plays\_per\_viewer} = \frac{\sum \text{plays}}{\sum \text{unique\_viewers}} \quad (\text{or } 0.0)$$
- **Sessions per Unique Viewer**:
  $$\text{sessions\_per\_viewer} = \frac{\sum \text{sessions}}{\sum \text{unique\_viewers}} \quad (\text{or } 0.0)$$
- **Buffering Rate**:
  $$\text{buffering\_rate} = \frac{\sum \text{buffering\_events}}{\sum \text{plays}} \quad (\text{or } 0.0)$$
- **Playback Error Rate**:
  $$\text{error\_rate} = \frac{\sum \text{playback\_errors}}{\sum \text{plays}} \quad (\text{or } 0.0)$$
- **Quality Change Rate**:
  $$\text{quality\_change\_rate} = \frac{\sum \text{quality\_changes}}{\sum \text{plays}} \quad (\text{or } 0.0)$$

---

### B. Content Performance Scoring (`app/analytics/performance.py`)
Each content asset's raw metric $x \in \{\text{plays}, \text{watch\_time\_seconds}, \text{unique\_viewers}\}$ is min-max normalized across all distinct assets in the dataset:

$$\text{normalized}(x) = \begin{cases} \frac{x - \min(x)}{\max(x) - \min(x)} & \text{if } \max(x) > \min(x) \\ 0.0 & \text{if } \max(x) = \min(x) \end{cases}$$

The composite **Performance Score** (bounded in $[0.0, 100.0]$) is computed deterministically:
$$\text{performance\_score} = \left(0.35 \cdot \text{norm\_plays} + 0.25 \cdot \text{norm\_watch\_time} + 0.20 \cdot \text{norm\_viewers} + 0.20 \cdot \text{completion\_rate}\right) \times 100.0$$

**Tie-Breaking**: Rankings sort by target metric descending, then by `content_id` ascending.

---

### C. Period-over-Period Growth (`app/analytics/growth.py`)
$$\text{growth\_percentage} = \begin{cases} 
\frac{\text{current} - \text{previous}}{\text{previous}} \times 100.0 & \text{if } \text{previous} > 0 \\
100.0 & \text{if } \text{previous} = 0 \text{ and } \text{current} > 0 \\
0.0 & \text{if } \text{previous} = 0 \text{ and } \text{current} = 0 \\
-100.0 & \text{if } \text{previous} > 0 \text{ and } \text{current} = 0
\end{cases}$$

**Trend Direction**:
- $\text{growth} > 0.0 \implies \text{UP}$
- $\text{growth} < 0.0 \implies \text{DOWN}$
- $\text{growth} = 0.0 \implies \text{FLAT}$

---

### D. Statistical Distributions & Percentiles (`app/analytics/distributions.py`)
- **Standard Deviation Convention**: **Sample Standard Deviation** ($ddof=1$):
  $$s = \sqrt{\frac{1}{N - 1} \sum_{i=1}^N (x_i - \bar{x})^2} \quad (\text{defined as } 0.0 \text{ when } N \le 1)$$
- **Percentiles**: Calculated via NumPy linear interpolation for $P_{25}, P_{50} (\text{Median}), P_{75}, P_{90}, P_{95}$.

---

### E. Anomaly Detection via Interquartile Range (`app/analytics/anomalies.py`)
Daily time-series records are analyzed for statistical outliers using robust IQR bounds:
1. $Q_1 = P_{25}$, $Q_3 = P_{75}$
2. $\text{IQR} = Q_3 - Q_1$
3. $\text{lower\_bound} = \max(0.0, Q_1 - 1.5 \cdot \text{IQR})$
4. $\text{upper\_bound} = Q_3 + 1.5 \cdot \text{IQR}$

**Severity**:
- Extreme Outlier ($\text{value} > Q_3 + 3.0 \cdot \text{IQR}$ or $\text{value} < Q_1 - 3.0 \cdot \text{IQR}$) $\implies \text{HIGH}$
- Moderate Outlier ($\text{value} > Q_3 + 1.5 \cdot \text{IQR}$ or $\text{value} < Q_1 - 1.5 \cdot \text{IQR}$) $\implies \text{MEDIUM}$

*Outlier Statement: Detected records are statistical variance outliers only, not fraud or security breaches.*

---

### F. Business Health Heuristics (`app/analytics/insights.py`)
Configurable thresholds in `app/core/config.py`:
- `HIGH_ENGAGEMENT_COMPLETION_THRESHOLD` = `0.70` (70%)
- `LOW_COMPLETION_RATE_THRESHOLD` = `0.30` (30%)
- `HIGH_BUFFERING_RATE_THRESHOLD` = `0.05` (5%)
- `HIGH_ERROR_RATE_THRESHOLD` = `0.02` (2%)
- `GROWTH_ALERT_THRESHOLD` = `25.0` (+25%)
- `DECLINING_CONTENT_THRESHOLD` = `-20.0` (-20%)

---

## 3. Advanced Analytics REST API Endpoints

All endpoints are mounted under `/api/v1/analytics/advanced`:

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/engagement` | Holistic viewer engagement and streaming quality metrics |
| `GET` | `/content` | Top content ranked by performance score or metric ($1 \le N \le 100$) |
| `GET` | `/growth` | Period-over-period growth with directional trends |
| `GET` | `/trends` | Daily time-series trends with day-over-day tracking |
| `GET` | `/platforms` | Platform comparison with playback market shares |
| `GET` | `/categories` | Category comparison with distribution shares |
| `GET` | `/languages` | Language comparison with distribution shares |
| `GET` | `/distributions` | Sample statistics and percentiles (P25 to P95) |
| `GET` | `/anomalies` | Statistical outlier detection using IQR |
| `GET` | `/insights` | Actionable business and platform health insights |

---

## 4. Privacy & Security Constraints

- **Zero PII Exposure**: All endpoints output aggregated or catalog-level summaries.
- **Strict Validation Guard**: Incoming payloads containing user identifier columns (`user_id`, `email`, `phone`, `ip_address`, `device_id`, etc.) trigger immediate `SensitiveDataError` and HTTP 422 rejection.
- **No Credentials in Storage**: Zero credentials or tokens are saved to disk or exposed in metadata/logs.

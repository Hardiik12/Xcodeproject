"""Model Card representation for ML model documentation and governance."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from app.ml.models.baseline import BaselineMetricScores


@dataclass
class ModelCard:
    """Encapsulates model metadata, performance metrics, contracts, and known limitations."""

    model_name: str
    algorithm: str
    target: str
    feature_schema_version: str
    training_data_source: str
    selection_metric: str
    selection_rule: str
    validation_metrics: BaselineMetricScores
    test_metrics: BaselineMetricScores
    baseline_metrics: BaselineMetricScores
    feature_count: int
    training_row_count: int
    validation_row_count: int
    test_row_count: int
    known_limitations: List[str]
    production_status: str
    parameters: Dict[str, Any] = field(default_factory=dict)

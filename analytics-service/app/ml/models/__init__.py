"""ML Models subpackage."""

from app.ml.models.baseline import (
    BaselineEvaluationResult,
    BaselineMetricScores,
    BaselinePredictor,
    calculate_mae,
    calculate_r2,
    calculate_rmse,
    evaluate_baseline,
)
from app.ml.models.model_benchmark import (
    ModelBenchmarkResult,
    ModelBenchmarkRunner,
    ModelPredictionDiagnostics,
    SingleModelBenchmark,
)
from app.ml.models.model_card import ModelCard
from app.ml.models.model_diagnostics import (
    DistributionStats,
    FeatureCorrelation,
    GroupedMetrics,
    ModelDiagnosticReport,
    PredictionBiasSummary,
    ResidualSummary,
    analyze_prediction_bias,
    analyze_residuals,
    analyze_target_distributions,
    calculate_feature_target_correlations,
    calculate_grouped_metrics,
    calculate_residuals,
    run_model_diagnostics,
)
from app.ml.models.model_selector import (
    ModelSelectionResult,
    select_production_candidate,
)
from app.ml.models.model_trainer import (
    ImprovementMetrics,
    MLTrainingResult,
    ModelTrainer,
    calculate_improvement_pct,
)
from app.ml.models.target_builder import TARGET_COLUMN_PLAYS, build_next_day_target
from app.ml.models.training_dataset import (
    IDENTIFIER_COLUMNS,
    InsufficientTrainingDataError,
    PreparedMLDataset,
    prepare_training_dataset,
)

__all__ = [
    "TARGET_COLUMN_PLAYS",
    "build_next_day_target",
    "IDENTIFIER_COLUMNS",
    "InsufficientTrainingDataError",
    "PreparedMLDataset",
    "prepare_training_dataset",
    "BaselineEvaluationResult",
    "BaselineMetricScores",
    "BaselinePredictor",
    "calculate_mae",
    "calculate_r2",
    "calculate_rmse",
    "evaluate_baseline",
    "ImprovementMetrics",
    "MLTrainingResult",
    "ModelTrainer",
    "calculate_improvement_pct",
    "DistributionStats",
    "FeatureCorrelation",
    "GroupedMetrics",
    "ModelDiagnosticReport",
    "PredictionBiasSummary",
    "ResidualSummary",
    "analyze_prediction_bias",
    "analyze_residuals",
    "analyze_target_distributions",
    "calculate_feature_target_correlations",
    "calculate_grouped_metrics",
    "calculate_residuals",
    "run_model_diagnostics",
    "ModelBenchmarkResult",
    "ModelBenchmarkRunner",
    "ModelPredictionDiagnostics",
    "SingleModelBenchmark",
    "ModelCard",
    "ModelSelectionResult",
    "select_production_candidate",
]

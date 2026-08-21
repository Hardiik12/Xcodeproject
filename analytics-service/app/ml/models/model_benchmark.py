"""Time-series ML model benchmark suite evaluating Naive, Random Forest, and Ridge Regression."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge

from app.ml.models.baseline import (
    BaselineEvaluationResult,
    BaselineMetricScores,
    BaselinePredictor,
    calculate_mae,
    calculate_r2,
    calculate_rmse,
    evaluate_baseline,
)
from app.ml.models.model_trainer import (
    ImprovementMetrics,
    ModelTrainer,
    calculate_improvement_pct,
)
from app.ml.models.training_dataset import (
    IDENTIFIER_COLUMNS,
    InsufficientTrainingDataError,
    PreparedMLDataset,
)
from app.ml.preprocessing.pipeline import (
    DEFAULT_CATEGORICAL_FEATURES,
    DEFAULT_NUMERIC_FEATURES,
    fit_and_transform_splits,
)
from app.processing.data_validator import SENSITIVE_COLUMNS
from app.processing.errors import SensitiveDataError


@dataclass
class ModelPredictionDiagnostics:
    """Diagnostic statistics for a model's prediction distribution."""

    model: str
    mean: float
    min_val: float
    max_val: float
    negative_prediction_count: int
    extrapolates_beyond_train_max: bool


@dataclass
class SingleModelBenchmark:
    """Benchmark evaluation for a single model across validation and test partitions."""

    model: str
    validation_metrics: BaselineMetricScores
    test_metrics: BaselineMetricScores
    val_improvement_vs_naive: ImprovementMetrics
    test_improvement_vs_naive: ImprovementMetrics
    diagnostics_val: ModelPredictionDiagnostics
    diagnostics_test: ModelPredictionDiagnostics


@dataclass
class ModelBenchmarkResult:
    """Comprehensive benchmark comparison across Naive, Random Forest, and Ridge models."""

    models: Dict[str, SingleModelBenchmark]
    validation_best_model: str
    validation_selection_reason: str
    test_ranking: List[str]
    data_source: str
    production_evaluation: str


class ModelBenchmarkRunner:
    """Orchestrates time-series model benchmarking without lookahead or data leakage."""

    def __init__(self, ridge_alpha: float = 1.0, random_state: int = 42):
        self.ridge_alpha = ridge_alpha
        self.random_state = random_state

    def run_benchmark(
        self,
        prepared_dataset: PreparedMLDataset,
        data_source: str = "SYNTHETIC_FIXTURE",
    ) -> ModelBenchmarkResult:
        """
        Run benchmark comparison across Naive Previous-Day, RandomForestRegressor, and Ridge.

        Rules:
            1. Preprocessing ColumnTransformer fit ONLY on X_train.
            2. Validation and Test splits transformed using X_train parameters.
            3. Validation performance determines validation_best_model.
            4. Test partition is final evaluation ONLY.

        Args:
            prepared_dataset: Structured dataset splits (X_train, y_train, X_val, y_val, X_test, y_test).
            data_source: Label designating data origin ("SYNTHETIC_FIXTURE" or "PRODUCTION").

        Returns:
            ModelBenchmarkResult with comprehensive metrics, rankings, and diagnostics.

        Raises:
            InsufficientTrainingDataError: If dataset splits are empty or invalid.
            SensitiveDataError: If PII columns are detected in features.
        """
        if prepared_dataset is None or prepared_dataset.X_train.empty or len(prepared_dataset.X_train) < 2:
            raise InsufficientTrainingDataError("Insufficient training data for model benchmarking.")

        # Check PII in dataset
        present_cols = set(c.lower() for c in prepared_dataset.X_train.columns)
        found_pii = present_cols.intersection(set(c.lower() for c in SENSITIVE_COLUMNS))
        if found_pii:
            raise SensitiveDataError(f"PII violation in benchmark input: {sorted(found_pii)}")

        X_train = prepared_dataset.X_train
        y_train = prepared_dataset.y_train.values.astype("float64")

        X_val = prepared_dataset.X_val
        y_val = prepared_dataset.y_val.values.astype("float64")

        X_test = prepared_dataset.X_test
        y_test = prepared_dataset.y_test.values.astype("float64")

        train_max_target = float(np.max(y_train)) if len(y_train) > 0 else 0.0

        # Check for forbidden targets in X
        forbidden_in_x = {"target_next_day_plays", "target_next_day_watch_time", "target_next_day_completion_rate"}
        found_forbidden = set(X_train.columns).intersection(forbidden_in_x)
        if found_forbidden:
            raise ValueError(f"Target leakage violation in benchmark: {sorted(found_forbidden)}")

        # Feature selection
        num_features = [col for col in X_train.columns if col in DEFAULT_NUMERIC_FEATURES]
        cat_features = [col for col in X_train.columns if col in DEFAULT_CATEGORICAL_FEATURES]

        # 1. Preprocessing fit ONLY on X_train
        pipeline, X_tr_proc, X_val_proc, X_te_proc = fit_and_transform_splits(
            train_df=X_train,
            val_df=X_val,
            test_df=X_test,
            numeric_features=num_features,
            categorical_features=cat_features,
        )

        # 2. Evaluate Naive Baseline Model
        naive_baseline = evaluate_baseline(prepared_dataset)
        val_naive_scores = naive_baseline.validation
        test_naive_scores = naive_baseline.test

        naive_predictor = BaselinePredictor()
        val_naive_preds = naive_predictor.predict(X_val)
        test_naive_preds = naive_predictor.predict(X_test)

        naive_diag_val = self._build_diagnostics("naive_previous_day_plays", val_naive_preds, train_max_target)
        naive_diag_test = self._build_diagnostics("naive_previous_day_plays", test_naive_preds, train_max_target)

        naive_bm = SingleModelBenchmark(
            model="naive_previous_day_plays",
            validation_metrics=val_naive_scores,
            test_metrics=test_naive_scores,
            val_improvement_vs_naive=ImprovementMetrics(0.0, 0.0),
            test_improvement_vs_naive=ImprovementMetrics(0.0, 0.0),
            diagnostics_val=naive_diag_val,
            diagnostics_test=naive_diag_test,
        )

        # 3. Train & Evaluate RandomForestRegressor
        rf_trainer = ModelTrainer(random_state=self.random_state)
        rf_result = rf_trainer.train_and_evaluate(prepared_dataset, data_source_label=data_source)

        rf_val_preds = rf_result.model_instance.predict(X_val_proc) if X_val_proc is not None else np.array([])
        rf_test_preds = rf_result.model_instance.predict(X_te_proc) if X_te_proc is not None else np.array([])

        rf_diag_val = self._build_diagnostics("random_forest", rf_val_preds, train_max_target)
        rf_diag_test = self._build_diagnostics("random_forest", rf_test_preds, train_max_target)

        rf_bm = SingleModelBenchmark(
            model="random_forest",
            validation_metrics=rf_result.validation_metrics,
            test_metrics=rf_result.test_metrics,
            val_improvement_vs_naive=rf_result.improvement_validation,
            test_improvement_vs_naive=rf_result.improvement_test,
            diagnostics_val=rf_diag_val,
            diagnostics_test=rf_diag_test,
        )

        # 4. Train & Evaluate Ridge Regression
        try:
            ridge_model = Ridge(alpha=self.ridge_alpha, random_state=self.random_state)
        except TypeError:
            ridge_model = Ridge(alpha=self.ridge_alpha)

        ridge_model.fit(X_tr_proc, y_train)

        ridge_val_preds = ridge_model.predict(X_val_proc) if X_val_proc is not None else np.array([])
        ridge_test_preds = ridge_model.predict(X_te_proc) if X_te_proc is not None else np.array([])

        ridge_val_mae = calculate_mae(y_val, ridge_val_preds)
        ridge_val_rmse = calculate_rmse(y_val, ridge_val_preds)
        ridge_val_r2 = calculate_r2(y_val, ridge_val_preds)
        ridge_val_metrics = BaselineMetricScores(mae=ridge_val_mae, rmse=ridge_val_rmse, r2=ridge_val_r2)

        ridge_test_mae = calculate_mae(y_test, ridge_test_preds)
        ridge_test_rmse = calculate_rmse(y_test, ridge_test_preds)
        ridge_test_r2 = calculate_r2(y_test, ridge_test_preds)
        ridge_test_metrics = BaselineMetricScores(mae=ridge_test_mae, rmse=ridge_test_rmse, r2=ridge_test_r2)

        ridge_val_imp = ImprovementMetrics(
            mae_improvement_pct=calculate_improvement_pct(val_naive_scores.mae, ridge_val_mae),
            rmse_improvement_pct=calculate_improvement_pct(val_naive_scores.rmse, ridge_val_rmse),
        )
        ridge_test_imp = ImprovementMetrics(
            mae_improvement_pct=calculate_improvement_pct(test_naive_scores.mae, ridge_test_mae),
            rmse_improvement_pct=calculate_improvement_pct(test_naive_scores.rmse, ridge_test_rmse),
        )

        ridge_diag_val = self._build_diagnostics("ridge_regression", ridge_val_preds, train_max_target)
        ridge_diag_test = self._build_diagnostics("ridge_regression", ridge_test_preds, train_max_target)

        ridge_bm = SingleModelBenchmark(
            model="ridge_regression",
            validation_metrics=ridge_val_metrics,
            test_metrics=ridge_test_metrics,
            val_improvement_vs_naive=ridge_val_imp,
            test_improvement_vs_naive=ridge_test_imp,
            diagnostics_val=ridge_diag_val,
            diagnostics_test=ridge_diag_test,
        )

        models_map = {
            "naive_previous_day_plays": naive_bm,
            "random_forest": rf_bm,
            "ridge_regression": ridge_bm,
        }

        # 5. Validation-Based Selection (Selection MUST use VALIDATION MAE with RMSE tie-breaker)
        sorted_by_val = sorted(
            models_map.keys(),
            key=lambda m: (models_map[m].validation_metrics.mae, models_map[m].validation_metrics.rmse),
        )
        val_best = sorted_by_val[0]
        val_reason = f"Selected '{val_best}' based on lowest Validation MAE ({models_map[val_best].validation_metrics.mae:.4f}) and RMSE."

        # 6. Test Ranking (Final evaluation reporting ONLY)
        sorted_by_test = sorted(
            models_map.keys(),
            key=lambda m: (models_map[m].test_metrics.mae, models_map[m].test_metrics.rmse),
        )

        return ModelBenchmarkResult(
            models=models_map,
            validation_best_model=val_best,
            validation_selection_reason=val_reason,
            test_ranking=sorted_by_test,
            data_source=data_source,
            production_evaluation="NOT_AVAILABLE",
        )

    def _build_diagnostics(
        self, model_name: str, preds: np.ndarray, train_max_target: float
    ) -> ModelPredictionDiagnostics:
        """Construct prediction distribution diagnostics."""
        if len(preds) == 0:
            return ModelPredictionDiagnostics(
                model=model_name, mean=0.0, min_val=0.0, max_val=0.0,
                negative_prediction_count=0, extrapolates_beyond_train_max=False
            )

        mean_val = float(np.mean(preds))
        min_val = float(np.min(preds))
        max_val = float(np.max(preds))
        neg_count = int(np.sum(preds < 0.0))
        extrapolates = max_val > train_max_target

        return ModelPredictionDiagnostics(
            model=model_name,
            mean=mean_val,
            min_val=min_val,
            max_val=max_val,
            negative_prediction_count=neg_count,
            extrapolates_beyond_train_max=extrapolates,
        )

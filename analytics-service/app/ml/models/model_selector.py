"""Validation-driven model selection, stability evaluation, and model card generation."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd

FEATURE_SCHEMA_VERSION = "features-v1"
from app.ml.models.baseline import BaselineMetricScores, evaluate_baseline
from app.ml.models.model_benchmark import ModelBenchmarkResult, ModelBenchmarkRunner, SingleModelBenchmark
from app.ml.models.model_card import ModelCard
from app.ml.models.model_trainer import ImprovementMetrics, calculate_improvement_pct
from app.ml.models.training_dataset import IDENTIFIER_COLUMNS, InsufficientTrainingDataError, PreparedMLDataset
from app.processing.data_validator import SENSITIVE_COLUMNS
from app.processing.errors import SensitiveDataError


@dataclass
class ModelSelectionResult:
    """Result of validation-driven model candidate selection and final test evaluation."""

    selected_model: str
    selection_reason: str
    selection_status: str  # "LEARNED_MODEL_SELECTED", "BASELINE_RETAINED", or "NO_VALID_MODEL"
    validation_metrics: BaselineMetricScores
    final_test_metrics: BaselineMetricScores
    baseline_test_metrics: BaselineMetricScores
    test_mae_improvement_pct: float
    test_rmse_improvement_pct: float
    degradation_ratio: Optional[float]
    stability_status: str  # "STABLE" or "POTENTIAL_INSTABILITY"
    production_status: str  # "PROVISIONAL MODEL CANDIDATE", "BASELINE RETAINED", or "NOT SUITABLE"
    feature_schema_version: str
    feature_count: int
    model_card: ModelCard
    benchmark_result: ModelBenchmarkResult


def select_production_candidate(
    prepared_dataset: PreparedMLDataset,
    ridge_alpha: float = 1.0,
    random_state: int = 42,
    degradation_threshold: float = 2.0,
    min_mae_improvement_threshold: float = 0.05,  # 5% required improvement over baseline
    tie_tolerance_pct: float = 0.01,  # 1% difference considered effectively tied
    data_source: str = "SYNTHETIC_FIXTURE",
) -> ModelSelectionResult:
    """
    Select the optimal model candidate based strictly on validation split performance.

    Rules:
        1. Primary: Lowest Validation MAE.
        2. Minimum 5% Validation MAE improvement over baseline required to select a learned model.
        3. Tie-breaker: If models are within 1% MAE tolerance, prefer simpler model (Naive < Ridge < RF).
        4. Test split is UNTOUCHED during selection and evaluated ONCE post-selection.

    Args:
        prepared_dataset: Chronologically split dataset bundle.
        ridge_alpha: Alpha hyper-parameter for Ridge Regression.
        random_state: Seed for reproducibility.
        degradation_threshold: Maximum allowable test_mae / val_mae ratio before reporting instability.
        min_mae_improvement_threshold: 5% minimum MAE improvement required.
        tie_tolerance_pct: 1% tolerance for tie-breaking simplicity.
        data_source: Dataset origin label.

    Returns:
        ModelSelectionResult with selected model, model card, stability stats, and production status.
    """
    if prepared_dataset is None or prepared_dataset.X_train.empty or len(prepared_dataset.X_train) < 2:
        raise InsufficientTrainingDataError("Insufficient training data for model candidate selection.")

    # Validate PII in dataset
    present_cols = set(c.lower() for c in prepared_dataset.X_train.columns)
    found_pii = present_cols.intersection(set(c.lower() for c in SENSITIVE_COLUMNS))
    if found_pii:
        raise SensitiveDataError(f"PII violation in model selector input: {sorted(found_pii)}")

    # 1. Run Benchmark across Naive, Random Forest, and Ridge models
    runner = ModelBenchmarkRunner(ridge_alpha=ridge_alpha, random_state=random_state)
    bm_res = runner.run_benchmark(prepared_dataset, data_source=data_source)
    models_map = bm_res.models

    naive_val_mae = models_map["naive_previous_day_plays"].validation_metrics.mae
    rf_val_mae = models_map["random_forest"].validation_metrics.mae
    ridge_val_mae = models_map["ridge_regression"].validation_metrics.mae

    # Complexity priority order
    complexity_order = ["naive_previous_day_plays", "ridge_regression", "random_forest"]

    # 2. Check 5% improvement threshold (learned_model_MAE must be < baseline_MAE * 0.95)
    learned_candidates = []
    for name in ["ridge_regression", "random_forest"]:
        val_mae = models_map[name].validation_metrics.mae
        if naive_val_mae > 0.0:
            imp_pct = (naive_val_mae - val_mae) / naive_val_mae
            if imp_pct >= min_mae_improvement_threshold:
                learned_candidates.append(name)
        elif val_mae < naive_val_mae:
            learned_candidates.append(name)

    if not learned_candidates:
        selected_name = "naive_previous_day_plays"
        selection_status = "BASELINE_RETAINED"
        prod_status = "BASELINE RETAINED"
        selection_reason = (
            "No learned model achieved the required 5% validation MAE improvement "
            "over the naive baseline. Baseline retained."
        )
    else:
        # Sort learned candidates by validation MAE
        learned_candidates.sort(key=lambda m: (models_map[m].validation_metrics.mae, models_map[m].validation_metrics.rmse))
        best_learned = learned_candidates[0]
        best_val_mae = models_map[best_learned].validation_metrics.mae

        # Apply 1% tie-breaking simplicity rule
        tied_candidates = []
        for cand in learned_candidates:
            cand_mae = models_map[cand].validation_metrics.mae
            diff_pct = abs(cand_mae - best_val_mae) / max(best_val_mae, 1e-9)
            if diff_pct <= tie_tolerance_pct:
                tied_candidates.append(cand)

        # Select simplest model among effectively tied candidates
        selected_name = sorted(tied_candidates, key=lambda m: complexity_order.index(m))[0]
        selection_status = "LEARNED_MODEL_SELECTED"
        prod_status = "PROVISIONAL MODEL CANDIDATE"
        val_mae = models_map[selected_name].validation_metrics.mae
        val_rmse = models_map[selected_name].validation_metrics.rmse
        selection_reason = (
            f"Selected '{selected_name}' based strictly on validation split performance: "
            f"Validation MAE={val_mae:.4f}, Validation RMSE={val_rmse:.4f}."
        )

    selected_bm = models_map[selected_name]

    # 3. Final Test Evaluation (Untouched Test Split)
    final_test_metrics = selected_bm.test_metrics
    baseline_test_metrics = models_map["naive_previous_day_plays"].test_metrics

    test_mae_imp = calculate_improvement_pct(baseline_test_metrics.mae, final_test_metrics.mae)
    test_rmse_imp = calculate_improvement_pct(baseline_test_metrics.rmse, final_test_metrics.rmse)

    # 4. Stability Check (degradation_ratio = test_mae / val_mae)
    val_mae_val = selected_bm.validation_metrics.mae
    if val_mae_val == 0.0:
        degradation_ratio = None
        stability_status = "STABLE"
    else:
        degradation_ratio = float(final_test_metrics.mae / val_mae_val)
        stability_status = "STABLE" if degradation_ratio <= degradation_threshold else "POTENTIAL_INSTABILITY"

    # 5. Build Model Card
    limitations = [
        "Evaluated on synthetic deterministic fixture data.",
        "Limited sample size in verification environment.",
        "Test partition reserved for final evaluation only; model not updated post-test.",
        "Model artifact is not deployed to production endpoint.",
    ]
    if stability_status == "POTENTIAL_INSTABILITY":
        limitations.append("Test MAE degradation ratio exceeds threshold (> 2.0).")

    alg_map = {
        "naive_previous_day_plays": "Naive 1-Day Lag Baseline",
        "random_forest": "RandomForestRegressor(n_estimators=200, random_state=42)",
        "ridge_regression": "Ridge(alpha=1.0, random_state=42)",
    }

    feature_cols = [c for c in prepared_dataset.X_train.columns if c not in {"target_next_day_plays", "target_next_day_watch_time", "target_next_day_completion_rate"}]

    card = ModelCard(
        model_name=f"{selected_name}-v1",
        algorithm=alg_map.get(selected_name, selected_name),
        target="target_next_day_plays",
        feature_schema_version=FEATURE_SCHEMA_VERSION,
        training_data_source=data_source,
        selection_metric="Validation MAE",
        selection_rule="Lowest Validation MAE with RMSE tie-breaker and 5% threshold",
        validation_metrics=selected_bm.validation_metrics,
        test_metrics=final_test_metrics,
        baseline_metrics=baseline_test_metrics,
        feature_count=len(feature_cols),
        training_row_count=prepared_dataset.train_rows,
        validation_row_count=prepared_dataset.val_rows,
        test_row_count=prepared_dataset.test_rows,
        known_limitations=limitations,
        production_status=prod_status,
        parameters={"alpha": ridge_alpha} if selected_name == "ridge_regression" else {},
    )

    return ModelSelectionResult(
        selected_model=selected_name,
        selection_reason=selection_reason,
        selection_status=selection_status,
        validation_metrics=selected_bm.validation_metrics,
        final_test_metrics=final_test_metrics,
        baseline_test_metrics=baseline_test_metrics,
        test_mae_improvement_pct=test_mae_imp,
        test_rmse_improvement_pct=test_rmse_imp,
        degradation_ratio=degradation_ratio,
        stability_status=stability_status,
        production_status=prod_status,
        feature_schema_version=FEATURE_SCHEMA_VERSION,
        feature_count=len(feature_cols),
        model_card=card,
        benchmark_result=bm_res,
    )

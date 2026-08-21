"""RandomForestRegressor trainer, evaluation, baseline comparison, and feature importance."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor

from app.ml.models.baseline import (
    BaselineEvaluationResult,
    BaselineMetricScores,
    calculate_mae,
    calculate_r2,
    calculate_rmse,
    evaluate_baseline,
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


@dataclass
class ImprovementMetrics:
    """Improvement metrics comparing Random Forest model against Naive Baseline."""

    mae_improvement_pct: float
    rmse_improvement_pct: float


@dataclass
class MLTrainingResult:
    """Encapsulates trained model metadata, evaluation metrics, and baseline comparisons."""

    algorithm: str
    parameters: Dict[str, Any]
    train_rows: int
    validation_rows: int
    test_rows: int
    validation_metrics: BaselineMetricScores
    test_metrics: BaselineMetricScores
    baseline_validation_metrics: BaselineMetricScores
    baseline_test_metrics: BaselineMetricScores
    improvement_validation: ImprovementMetrics
    improvement_test: ImprovementMetrics
    feature_importance: List[Dict[str, Any]]
    data_source: str
    model_instance: Optional[Any] = field(default=None, repr=False)
    pipeline_instance: Optional[Any] = field(default=None, repr=False)


def calculate_improvement_pct(baseline_score: float, model_score: float) -> float:
    """
    Calculate metric improvement percentage.

    Formula:
        ((baseline - model) / baseline) * 100

    Returns:
        0.0 if baseline_score == 0.
    """
    if baseline_score <= 0.0:
        return 0.0
    return float(((baseline_score - model_score) / baseline_score) * 100.0)


class ModelTrainer:
    """Trains and evaluates RandomForestRegressor for next-day plays prediction."""

    def __init__(
        self,
        n_estimators: int = 200,
        random_state: int = 42,
        n_jobs: int = -1,
        max_depth: Optional[int] = 10,
        min_samples_leaf: int = 2,
    ):
        self.n_estimators = n_estimators
        self.random_state = random_state
        self.n_jobs = n_jobs
        self.max_depth = max_depth
        self.min_samples_leaf = min_samples_leaf

    def train_and_evaluate(
        self,
        prepared_dataset: PreparedMLDataset,
        data_source_label: str = "SYNTHETIC FIXTURE RESULTS",
    ) -> MLTrainingResult:
        """
        Train RandomForestRegressor on TRAIN split and evaluate on Validation and Test splits.

        Chronological Preprocessing Invariant:
            Preprocessing ColumnTransformer is fit strictly on X_train.
            X_val and X_test are transformed using X_train parameters.

        Args:
            prepared_dataset: Structured dataset containing train, val, and test splits.
            data_source_label: Label designating data origin ("SYNTHETIC FIXTURE RESULTS" or "PRODUCTION").

        Returns:
            MLTrainingResult with model performance, baseline comparison, and top feature importances.

        Raises:
            InsufficientTrainingDataError: If X_train is empty or has < 3 rows.
            ValueError: If target columns are detected in input feature matrix X.
        """
        if prepared_dataset is None or prepared_dataset.X_train.empty or len(prepared_dataset.X_train) < 2:
            raise InsufficientTrainingDataError("Insufficient training data for model training.")

        X_train = prepared_dataset.X_train
        y_train = prepared_dataset.y_train.values.astype("float64")

        X_val = prepared_dataset.X_val
        y_val = prepared_dataset.y_val.values.astype("float64")

        X_test = prepared_dataset.X_test
        y_test = prepared_dataset.y_test.values.astype("float64")

        # 1. Feature selection (Separate model features from identifiers)
        forbidden_in_x = {"target_next_day_plays", "target_next_day_watch_time", "target_next_day_completion_rate"}
        found_forbidden = set(X_train.columns).intersection(forbidden_in_x)
        if found_forbidden:
            raise ValueError(f"Target leakage violation: target column(s) {sorted(found_forbidden)} in X_train!")

        num_features = [col for col in X_train.columns if col in DEFAULT_NUMERIC_FEATURES]
        cat_features = [col for col in X_train.columns if col in DEFAULT_CATEGORICAL_FEATURES]

        # 2. Preprocessing: Fit ONLY on X_train, transform X_val and X_test
        pipeline, X_tr_proc, X_val_proc, X_te_proc = fit_and_transform_splits(
            train_df=X_train,
            val_df=X_val,
            test_df=X_test,
            numeric_features=num_features,
            categorical_features=cat_features,
        )

        # 3. Model Training: RandomForestRegressor fit ONLY on train
        params = {
            "n_estimators": self.n_estimators,
            "random_state": self.random_state,
            "n_jobs": self.n_jobs,
            "max_depth": self.max_depth,
            "min_samples_leaf": self.min_samples_leaf,
        }

        model = RandomForestRegressor(**params)
        model.fit(X_tr_proc, y_train)

        # 4. Generate Predictions on Validation and Test
        val_preds = model.predict(X_val_proc) if X_val_proc is not None and len(X_val_proc) > 0 else np.array([])
        test_preds = model.predict(X_te_proc) if X_te_proc is not None and len(X_te_proc) > 0 else np.array([])

        # 5. Model Evaluation Metrics
        val_mae = calculate_mae(y_val, val_preds)
        val_rmse = calculate_rmse(y_val, val_preds)
        val_r2 = calculate_r2(y_val, val_preds)
        val_metrics = BaselineMetricScores(mae=val_mae, rmse=val_rmse, r2=val_r2)

        test_mae = calculate_mae(y_test, test_preds)
        test_rmse = calculate_rmse(y_test, test_preds)
        test_r2 = calculate_r2(y_test, test_preds)
        test_metrics = BaselineMetricScores(mae=test_mae, rmse=test_rmse, r2=test_r2)

        # 6. Baseline Model Evaluation & Comparison
        baseline_res = evaluate_baseline(prepared_dataset)
        base_val = baseline_res.validation
        base_test = baseline_res.test

        val_imp = ImprovementMetrics(
            mae_improvement_pct=calculate_improvement_pct(base_val.mae, val_mae),
            rmse_improvement_pct=calculate_improvement_pct(base_val.rmse, val_rmse),
        )
        test_imp = ImprovementMetrics(
            mae_improvement_pct=calculate_improvement_pct(base_test.mae, test_mae),
            rmse_improvement_pct=calculate_improvement_pct(base_test.rmse, test_rmse),
        )

        # 7. Extract Feature Importances
        try:
            feature_names_out = pipeline.get_feature_names_out()
        except AttributeError:
            feature_names_out = [f"feature_{i}" for i in range(X_tr_proc.shape[1])]

        importances = model.feature_importances_
        importance_pairs = []
        for name, score in zip(feature_names_out, importances):
            # Clean feature names (remove transformer prefixes like num__ or cat__)
            clean_name = name.split("__")[-1] if "__" in name else name
            importance_pairs.append({"feature": clean_name, "importance": float(score)})

        importance_pairs.sort(key=lambda x: x["importance"], reverse=True)
        top_10_importance = importance_pairs[:10]

        return MLTrainingResult(
            algorithm="RandomForestRegressor",
            parameters=params,
            train_rows=len(X_train),
            validation_rows=len(X_val),
            test_rows=len(X_test),
            validation_metrics=val_metrics,
            test_metrics=test_metrics,
            baseline_validation_metrics=base_val,
            baseline_test_metrics=base_test,
            improvement_validation=val_imp,
            improvement_test=test_imp,
            feature_importance=top_10_importance,
            data_source=data_source_label,
            model_instance=model,
            pipeline_instance=pipeline,
        )

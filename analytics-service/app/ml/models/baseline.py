"""Naive previous-day baseline predictor and evaluation metrics."""

from dataclasses import dataclass
from typing import Any, Dict, Optional, Union
import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

from app.ml.models.training_dataset import PreparedMLDataset


@dataclass
class BaselineMetricScores:
    """Encapsulates MAE, RMSE, and optional R^2 metric scores."""

    mae: float
    rmse: float
    r2: Optional[float]


@dataclass
class BaselineEvaluationResult:
    """Structured baseline evaluation across validation and test partitions."""

    model: str
    validation: BaselineMetricScores
    test: BaselineMetricScores


class BaselinePredictor:
    """Naive baseline predicting next day's plays equal to current day's plays."""

    def __init__(self, plays_column: str = "plays"):
        self.plays_column = plays_column

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        """
        Generate naive predictions using current day's plays.

        Args:
            X: Input feature DataFrame containing the 'plays' column.

        Returns:
            NumPy array of baseline predictions.

        Raises:
            ValueError: If 'plays' column is missing from input features.
        """
        if X is None or X.empty:
            return np.array([], dtype="float64")

        if self.plays_column not in X.columns:
            raise ValueError(f"Input features X must contain '{self.plays_column}' column for baseline prediction.")

        return X[self.plays_column].values.astype("float64")


def calculate_mae(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """Calculate Mean Absolute Error (MAE)."""
    if len(y_true) == 0:
        return 0.0
    return float(mean_absolute_error(y_true, y_pred))


def calculate_rmse(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """Calculate Root Mean Squared Error (RMSE)."""
    if len(y_true) == 0:
        return 0.0
    mse = mean_squared_error(y_true, y_pred)
    return float(np.sqrt(mse))


def calculate_r2(y_true: np.ndarray, y_pred: np.ndarray) -> Optional[float]:
    """
    Calculate Coefficient of Determination (R^2).

    Edge Case Handling:
        If y_true is constant (SST == 0), R^2 is mathematically undefined.
        Returns None instead of NaN or Infinity.
    """
    if len(y_true) < 2:
        return None

    # Check if target is constant
    if np.all(y_true == y_true[0]):
        return None

    score = r2_score(y_true, y_pred)
    if np.isnan(score) or np.isinf(score):
        return None

    return float(score)


def evaluate_baseline(prepared_dataset: PreparedMLDataset) -> BaselineEvaluationResult:
    """
    Evaluate Naive Baseline predictor on validation and test partitions separately.

    Args:
        prepared_dataset: Structured dataset containing X_val, y_val, X_test, y_test.

    Returns:
        BaselineEvaluationResult containing validation and test metric scores.

    Raises:
        ValueError: If dataset is invalid or empty.
    """
    predictor = BaselinePredictor()

    # Validation evaluation
    y_val_true = prepared_dataset.y_val.values.astype("float64")
    y_val_pred = predictor.predict(prepared_dataset.X_val)

    val_mae = calculate_mae(y_val_true, y_val_pred)
    val_rmse = calculate_rmse(y_val_true, y_val_pred)
    val_r2 = calculate_r2(y_val_true, y_val_pred)

    val_metrics = BaselineMetricScores(mae=val_mae, rmse=val_rmse, r2=val_r2)

    # Test evaluation
    y_test_true = prepared_dataset.y_test.values.astype("float64")
    y_test_pred = predictor.predict(prepared_dataset.X_test)

    test_mae = calculate_mae(y_test_true, y_test_pred)
    test_rmse = calculate_rmse(y_test_true, y_test_pred)
    test_r2 = calculate_r2(y_test_true, y_test_pred)

    test_metrics = BaselineMetricScores(mae=test_mae, rmse=test_rmse, r2=test_r2)

    return BaselineEvaluationResult(
        model="naive_previous_day_plays",
        validation=val_metrics,
        test=test_metrics,
    )

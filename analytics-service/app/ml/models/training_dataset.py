"""Supervised training dataset preparation with chronological splitting and leakage protection."""

from dataclasses import dataclass
from datetime import date, datetime
from typing import Any, Dict, List, Optional, Set, Tuple
import numpy as np
import pandas as pd

from app.ml.features.feature_builder import TARGET_COLUMNS, validate_features
from app.ml.models.target_builder import TARGET_COLUMN_PLAYS, build_next_day_target
from app.processing.data_validator import SENSITIVE_COLUMNS
from app.processing.errors import SensitiveDataError


class InsufficientTrainingDataError(Exception):
    """Raised when available dataset is insufficient or empty for training ML model."""
    pass


IDENTIFIER_COLUMNS: List[str] = ["content_id", "date", "platform"]


@dataclass
class PreparedMLDataset:
    """Encapsulates supervised ML training, validation, and test splits with metadata."""

    X_train: pd.DataFrame
    y_train: pd.Series
    X_val: pd.DataFrame
    y_val: pd.Series
    X_test: pd.DataFrame
    y_test: pd.Series
    train_from: Optional[str]
    train_to: Optional[str]
    val_from: Optional[str]
    val_to: Optional[str]
    test_from: Optional[str]
    test_to: Optional[str]
    total_rows: int
    train_rows: int
    val_rows: int
    test_rows: int
    feature_names: List[str]
    target_name: str
    identifier_names: List[str]


def prepare_training_dataset(
    df: pd.DataFrame,
    target_col: str = TARGET_COLUMN_PLAYS,
    train_ratio: float = 0.70,
    val_ratio: float = 0.15,
    test_ratio: float = 0.15,
) -> PreparedMLDataset:
    """
    Prepare supervised training dataset from Phase 7.5 feature DataFrame.

    Responsibilities:
      1. PII and target leakage validation.
      2. Next-day target generation (if not already present).
      3. Removal of rows with unavailable target (target_next_day_plays is NaN).
      4. Target y validation (numeric, non-negative, non-null).
      5. Input features X selection (strictly excluding target and future-derived columns).
      6. Chronological 70/15/15 train/validation/test split by date boundaries.
      7. Return structured PreparedMLDataset.

    Args:
        df: Input DataFrame containing features and/or raw metrics.
        target_col: Target column name. Default "target_next_day_plays".
        train_ratio: Proportion of dates for training split. Default 0.70.
        val_ratio: Proportion of dates for validation split. Default 0.15.
        test_ratio: Proportion of dates for test split. Default 0.15.

    Returns:
        PreparedMLDataset with X and y splits, date boundaries, and metadata.

    Raises:
        InsufficientTrainingDataError: If dataset is empty or rows after cleanup < 3.
        SensitiveDataError: If forbidden PII columns are detected.
        ValueError: If target values are invalid or negative.
    """
    if df is None or df.empty:
        raise InsufficientTrainingDataError("Dataset is empty. Cannot prepare training dataset.")

    # 1. PII Check
    present_cols = set(c.lower() for c in df.columns)
    found_pii = present_cols.intersection(set(c.lower() for c in SENSITIVE_COLUMNS))
    if found_pii:
        raise SensitiveDataError(f"Dataset contains forbidden PII column(s): {sorted(found_pii)}")

    # 2. Target Generation (if not already present)
    working_df = df.copy()
    if target_col not in working_df.columns:
        working_df = build_next_day_target(working_df, target_col=target_col)

    # 3. Target Cleanup (Remove rows where target is NaN)
    rows_before = len(working_df)
    clean_df = working_df.dropna(subset=[target_col]).copy()
    rows_after = len(clean_df)

    if rows_after < 3:
        raise InsufficientTrainingDataError(
            f"Insufficient training observations after target cleanup. "
            f"Required at least 3 rows, but only {rows_after} available (from {rows_before} total)."
        )

    # 4. Target y Validation
    y_all = clean_df[target_col]
    if not pd.api.types.is_numeric_dtype(y_all):
        raise ValueError(f"Target column '{target_col}' must be numeric.")

    if y_all.isna().any():
        raise ValueError(f"Target column '{target_col}' contains illegal NaN values after cleanup.")

    if (y_all < 0).any():
        raise ValueError(f"Target column '{target_col}' contains illegal negative values.")

    # 5. Input features X selection (Exclude all target columns)
    forbidden_targets = set(TARGET_COLUMNS).union({target_col, "target_next_day_watch_time", "target_next_day_completion_rate"})
    feature_cols = [col for col in clean_df.columns if col not in forbidden_targets]

    X_all = clean_df[feature_cols].copy()
    y_all = clean_df[target_col].copy()

    # 6. Chronological Sort & Date Splitting
    clean_df["_date_parsed"] = pd.to_datetime(clean_df["date"], utc=True).dt.date
    clean_df = clean_df.sort_values(["_date_parsed", "content_id", "platform"]).reset_index(drop=True)

    unique_dates = sorted(clean_df["_date_parsed"].unique())
    n_dates = len(unique_dates)

    if n_dates < 3:
        # Assign to train/val/test partitions cleanly when dates are limited
        train_dates = unique_dates[:1]
        val_dates = unique_dates[1:2] if n_dates > 1 else []
        test_dates = unique_dates[2:] if n_dates > 2 else []
    else:
        train_end_idx = max(1, int(n_dates * train_ratio))
        val_end_idx = max(train_end_idx + 1, int(n_dates * (train_ratio + val_ratio)))
        val_end_idx = min(val_end_idx, n_dates - 1)

        train_dates = unique_dates[:train_end_idx]
        val_dates = unique_dates[train_end_idx:val_end_idx]
        test_dates = unique_dates[val_end_idx:]

    train_mask = clean_df["_date_parsed"].isin(train_dates)
    val_mask = clean_df["_date_parsed"].isin(val_dates)
    test_mask = clean_df["_date_parsed"].isin(test_dates)

    df_train = clean_df[train_mask]
    df_val = clean_df[val_mask]
    df_test = clean_df[test_mask]

    X_train = df_train[feature_cols].copy()
    y_train = df_train[target_col].copy()

    X_val = df_val[feature_cols].copy()
    y_val = df_val[target_col].copy()

    X_test = df_test[feature_cols].copy()
    y_test = df_test[target_col].copy()

    # Helpers for date range reporting
    def fmt_range(date_list: List[Any]) -> Tuple[Optional[str], Optional[str]]:
        if not date_list:
            return None, None
        return str(min(date_list)), str(max(date_list))

    train_from, train_to = fmt_range(train_dates)
    val_from, val_to = fmt_range(val_dates)
    test_from, test_to = fmt_range(test_dates)

    return PreparedMLDataset(
        X_train=X_train,
        y_train=y_train,
        X_val=X_val,
        y_val=y_val,
        X_test=X_test,
        y_test=y_test,
        train_from=train_from,
        train_to=train_to,
        val_from=val_from,
        val_to=val_to,
        test_from=test_from,
        test_to=test_to,
        total_rows=rows_after,
        train_rows=len(X_train),
        val_rows=len(X_val),
        test_rows=len(X_test),
        feature_names=feature_cols,
        target_name=target_col,
        identifier_names=[col for col in IDENTIFIER_COLUMNS if col in feature_cols],
    )

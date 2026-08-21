"""Scikit-Learn preprocessing pipeline for numerical standardization and categorical encoding."""

from typing import List, Optional, Tuple
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

DEFAULT_NUMERIC_FEATURES: List[str] = [
    "sessions",
    "plays",
    "unique_viewers",
    "watch_time_seconds",
    "completed_plays",
    "completion_rate",
    "buffering_events",
    "playback_errors",
    "quality_changes",
    "plays_per_session",
    "plays_per_viewer",
    "watch_time_per_play",
    "watch_time_per_session",
    "completed_plays_ratio",
    "buffering_rate",
    "error_rate",
    "quality_change_rate",
    "day_of_week",
    "day_of_month",
    "day_of_year",
    "week_of_year",
    "month",
    "quarter",
    "is_weekend",
    "content_play_share",
    "content_watch_time_share",
    "content_viewer_share",
    "plays_lag_1d",
    "watch_time_lag_1d",
    "viewers_lag_1d",
    "completion_rate_lag_1d",
    "plays_rolling_7d",
    "watch_time_rolling_7d",
    "viewers_rolling_7d",
    "completion_rate_rolling_7d",
    "plays_growth_1d",
    "watch_time_growth_1d",
    "viewer_growth_1d",
]

DEFAULT_CATEGORICAL_FEATURES: List[str] = [
    "platform",
    "category_id",
    "language_id",
]


def create_preprocessing_pipeline(
    numeric_features: Optional[List[str]] = None,
    categorical_features: Optional[List[str]] = None,
) -> ColumnTransformer:
    """Create a reusable scikit-learn ColumnTransformer preprocessing pipeline.

    Transformations:
        - Numeric Pipeline: SimpleImputer(strategy='median') -> StandardScaler()
        - Categorical Pipeline: SimpleImputer(strategy='most_frequent') -> OneHotEncoder(handle_unknown='ignore', sparse_output=False)
    """
    num_cols = numeric_features if numeric_features is not None else DEFAULT_NUMERIC_FEATURES
    cat_cols = categorical_features if categorical_features is not None else DEFAULT_CATEGORICAL_FEATURES

    numeric_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]
    )

    categorical_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]
    )

    preprocessor = ColumnTransformer(
        transformers=[
            ("num", numeric_transformer, num_cols),
            ("cat", categorical_transformer, cat_cols),
        ],
        remainder="drop",
    )

    return preprocessor


def fit_and_transform_splits(
    train_df: pd.DataFrame,
    val_df: Optional[pd.DataFrame] = None,
    test_df: Optional[pd.DataFrame] = None,
    numeric_features: Optional[List[str]] = None,
    categorical_features: Optional[List[str]] = None,
) -> Tuple[ColumnTransformer, np.ndarray, Optional[np.ndarray], Optional[np.ndarray]]:
    """Fit preprocessing pipeline strictly on TRAIN data and transform validation/test splits.

    CRITICAL LEAKAGE SAFETY:
        The ColumnTransformer is fit ONLY on train_df.
        Validation and Test splits are transformed using the fitted train parameters,
        preventing any lookahead or distribution leakage into training representations.
    """
    pipeline = create_preprocessing_pipeline(
        numeric_features=numeric_features,
        categorical_features=categorical_features,
    )

    # 1. Fit ONLY on train
    X_train = pipeline.fit_transform(train_df)

    # 2. Transform validation
    X_val = pipeline.transform(val_df) if val_df is not None and not val_df.empty else None

    # 3. Transform test
    X_test = pipeline.transform(test_df) if test_df is not None and not test_df.empty else None

    return pipeline, X_train, X_val, X_test

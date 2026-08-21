"""Dataset builder orchestrating feature extraction, chronological train/val/test splits, and preprocessing."""

from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer

from app.clients.analytics_client import AnalyticsClient
from app.core.config import Settings, get_settings
from app.ml.features.feature_builder import (
    BASE_FEATURE_COLUMNS,
    MLFeatureError,
    TARGET_COLUMNS,
    build_ml_features,
    validate_features,
)
from app.ml.metadata.feature_metadata import get_feature_registry
from app.ml.preprocessing.pipeline import fit_and_transform_splits
from app.ml.schemas.feature_schema import FEATURE_SCHEMA_VERSION, FeatureMetadataResponse
from app.processing.data_cleaner import clean_dataframe
from app.processing.data_validator import validate_dataframe
from app.processing.dataframe_builder import build_dataframe
from app.schemas.contract import PlatformEnum


@dataclass
class MLDeliveryBundle:
    """Encapsulates raw feature DataFrames, preprocessed numeric arrays, fitted pipeline, and metadata."""

    train_df: pd.DataFrame
    val_df: pd.DataFrame
    test_df: pd.DataFrame
    X_train: np.ndarray
    X_val: Optional[np.ndarray]
    X_test: Optional[np.ndarray]
    preprocessor: ColumnTransformer
    metadata: FeatureMetadataResponse
    split_info: Dict[str, Any]


class MLDatasetBuilder:
    """Orchestrates end-to-end dataset preparation for future machine learning pipelines."""

    def __init__(
        self,
        client: Optional[AnalyticsClient] = None,
        settings: Optional[Settings] = None,
    ):
        self.settings = settings or get_settings()
        self.client = client or AnalyticsClient(settings=self.settings)

    async def fetch_and_build_features(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        include_targets: bool = False,
        auth_token: Optional[str] = None,
    ) -> pd.DataFrame:
        """Fetch records from Spring Boot, clean, and construct verified ML feature dataset."""
        records = await self.client.fetch_all(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            auth_token=auth_token,
        )
        raw_df = build_dataframe(records)
        validate_dataframe(raw_df)
        cleaned_df = clean_dataframe(raw_df)

        features_df = build_ml_features(cleaned_df, include_targets=include_targets)
        # Validate data leakage safeguards
        validate_features(features_df, is_input_features_only=not include_targets)
        return features_df

    def split_chronologically(
        self,
        df: pd.DataFrame,
        train_ratio: float = 0.70,
        val_ratio: float = 0.15,
        test_ratio: float = 0.15,
    ) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, Dict[str, Any]]:
        """Split dataset into chronological Train, Validation, and Test partitions.

        Rule:
            Sorting is strictly by UTC calendar date.
            All training observation dates precede validation dates,
            and all validation dates precede test dates.
            No random permutations or cross-validation shuffling is used.
        """
        if df.empty:
            empty = df.copy()
            return empty, empty, empty, {"train_count": 0, "val_count": 0, "test_count": 0}

        # Normalize dates for grouping
        date_series = pd.to_datetime(df["date"], utc=True).dt.date
        unique_dates = sorted(date_series.unique())
        n_dates = len(unique_dates)

        if n_dates == 1:
            # Single day cannot be partitioned chronologically; assign all to train
            train_df = df.copy()
            val_df = df.iloc[0:0].copy()
            test_df = df.iloc[0:0].copy()
            split_info = {
                "train_dates": [str(unique_dates[0])],
                "val_dates": [],
                "test_dates": [],
                "train_count": len(train_df),
                "val_count": 0,
                "test_count": 0,
            }
            return train_df, val_df, test_df, split_info

        if n_dates == 2:
            # 2 days: day 1 to train, day 2 to test
            train_df = df[date_series == unique_dates[0]].copy()
            val_df = df.iloc[0:0].copy()
            test_df = df[date_series == unique_dates[1]].copy()
            split_info = {
                "train_dates": [str(unique_dates[0])],
                "val_dates": [],
                "test_dates": [str(unique_dates[1])],
                "train_count": len(train_df),
                "val_count": 0,
                "test_count": len(test_df),
            }
            return train_df, val_df, test_df, split_info

        # General chronological split (n_dates >= 3)
        train_end_idx = max(1, int(n_dates * train_ratio))
        val_end_idx = max(train_end_idx + 1, int(n_dates * (train_ratio + val_ratio)))
        val_end_idx = min(val_end_idx, n_dates - 1)  # Ensure at least 1 day for test if possible

        train_dates = set(unique_dates[:train_end_idx])
        val_dates = set(unique_dates[train_end_idx:val_end_idx])
        test_dates = set(unique_dates[val_end_idx:])

        train_df = df[date_series.isin(train_dates)].copy()
        val_df = df[date_series.isin(val_dates)].copy()
        test_df = df[date_series.isin(test_dates)].copy()

        split_info = {
            "train_dates": [str(d) for d in sorted(train_dates)],
            "val_dates": [str(d) for d in sorted(val_dates)],
            "test_dates": [str(d) for d in sorted(test_dates)],
            "train_count": len(train_df),
            "val_count": len(val_df),
            "test_count": len(test_df),
        }
        return train_df, val_df, test_df, split_info

    def get_metadata(
        self,
        row_count: int = 0,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
    ) -> FeatureMetadataResponse:
        """Construct the immutable feature schema metadata response."""
        features = get_feature_registry()
        return FeatureMetadataResponse(
            feature_schema_version=FEATURE_SCHEMA_VERSION,
            source_contract_version=self.settings.ANALYTICS_CONTRACT_VERSION,
            generated_at=datetime.now(timezone.utc),
            from_date=from_date,
            to_date=to_date,
            row_count=row_count,
            feature_count=len(features),
            features=features,
        )

    async def prepare_ml_bundle(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        include_targets: bool = False,
        auth_token: Optional[str] = None,
    ) -> MLDeliveryBundle:
        """Build feature dataset, chronologically split, fit preprocessor on train, and return bundle."""
        features_df = await self.fetch_and_build_features(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            include_targets=include_targets,
            auth_token=auth_token,
        )

        train_df, val_df, test_df, split_info = self.split_chronologically(features_df)

        preprocessor, X_train, X_val, X_test = fit_and_transform_splits(
            train_df=train_df,
            val_df=val_df if not val_df.empty else None,
            test_df=test_df if not test_df.empty else None,
        )

        meta = self.get_metadata(
            row_count=len(features_df),
            from_date=from_date,
            to_date=to_date,
        )

        return MLDeliveryBundle(
            train_df=train_df,
            val_df=val_df,
            test_df=test_df,
            X_train=X_train,
            X_val=X_val,
            X_test=X_test,
            preprocessor=preprocessor,
            metadata=meta,
            split_info=split_info,
        )

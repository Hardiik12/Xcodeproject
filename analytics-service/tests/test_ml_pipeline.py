"""Comprehensive tests for ML dataset building, chronological splitting, Scikit-Learn preprocessing, and API metadata endpoint."""

from datetime import date
from unittest.mock import AsyncMock, patch
import numpy as np
import pandas as pd
import pytest
from httpx import ASGITransport, AsyncClient

from app.clients.errors import AnalyticsContractError
from app.main import app
from app.ml.datasets.dataset_builder import MLDatasetBuilder
from app.ml.features.feature_builder import BASE_FEATURE_COLUMNS, build_ml_features
from app.ml.preprocessing.pipeline import (
    create_preprocessing_pipeline,
    fit_and_transform_splits,
)
from app.ml.schemas.feature_schema import FEATURE_SCHEMA_VERSION


@pytest.fixture
def multi_week_feature_df() -> pd.DataFrame:
    """Fixture returning 14 days of multi-content feature dataset."""
    dates = [f"2026-08-{i:02d}" for i in range(1, 15)]  # 14 days
    records = []
    for d in dates:
        for cid in [1, 2]:
            for plat in ["IOS", "ANDROID", "WEB"]:
                records.append(
                    {
                        "date": d,
                        "content_id": cid,
                        "category_id": 1,
                        "language_id": 1,
                        "platform": plat,
                        "sessions": 50,
                        "plays": 40,
                        "unique_viewers": 30,
                        "watch_time_seconds": 1200,
                        "completed_plays": 20,
                        "completion_rate": 0.50,
                        "buffering_events": 1,
                        "playback_errors": 0,
                        "quality_changes": 2,
                    }
                )
    raw_df = pd.DataFrame(records)
    return build_ml_features(raw_df)


def test_chronological_split_ratios_and_order(multi_week_feature_df: pd.DataFrame):
    """Verify chronological split strictly enforces Train Dates < Val Dates < Test Dates."""
    builder = MLDatasetBuilder()
    train_df, val_df, test_df, split_info = builder.split_chronologically(
        multi_week_feature_df, train_ratio=0.70, val_ratio=0.15, test_ratio=0.15
    )

    assert not train_df.empty
    assert not val_df.empty
    assert not test_df.empty

    train_dates = set(train_df["date"].unique())
    val_dates = set(val_df["date"].unique())
    test_dates = set(test_df["date"].unique())

    # Ensure mutually exclusive dates
    assert train_dates.isdisjoint(val_dates)
    assert val_dates.isdisjoint(test_dates)
    assert train_dates.isdisjoint(test_dates)

    # Ensure strict chronological ordering
    assert max(train_dates) < min(val_dates)
    assert max(val_dates) < min(test_dates)


def test_chronological_split_single_day():
    """Verify single-day dataset assigns all records to train with empty val and test."""
    df_single = pd.DataFrame(
        [
            {
                "date": "2026-08-01",
                "content_id": 1,
                "platform": "IOS",
                "plays": 10,
            }
        ]
    )
    builder = MLDatasetBuilder()
    train_df, val_df, test_df, split_info = builder.split_chronologically(df_single)
    assert len(train_df) == 1
    assert val_df.empty
    assert test_df.empty


def test_chronological_split_two_days():
    """Verify 2-day dataset partitions day 1 to train and day 2 to test."""
    df_two = pd.DataFrame(
        [
            {"date": "2026-08-01", "content_id": 1, "platform": "IOS", "plays": 10},
            {"date": "2026-08-02", "content_id": 1, "platform": "IOS", "plays": 20},
        ]
    )
    builder = MLDatasetBuilder()
    train_df, val_df, test_df, split_info = builder.split_chronologically(df_two)
    assert len(train_df) == 1
    assert len(test_df) == 1
    assert val_df.empty
    assert train_df.iloc[0]["date"] == "2026-08-01"
    assert test_df.iloc[0]["date"] == "2026-08-02"


def test_preprocessing_pipeline_fit_only_on_train(multi_week_feature_df: pd.DataFrame):
    """Verify ColumnTransformer fits only on training split and transforms validation and test."""
    builder = MLDatasetBuilder()
    train_df, val_df, test_df, _ = builder.split_chronologically(multi_week_feature_df)

    preprocessor, X_train, X_val, X_test = fit_and_transform_splits(
        train_df=train_df,
        val_df=val_df,
        test_df=test_df,
    )

    assert isinstance(X_train, np.ndarray)
    assert isinstance(X_val, np.ndarray)
    assert isinstance(X_test, np.ndarray)

    assert X_train.shape[0] == len(train_df)
    assert X_val.shape[0] == len(val_df)
    assert X_test.shape[0] == len(test_df)
    assert X_train.shape[1] == X_val.shape[1] == X_test.shape[1]

    # Preprocessor must have fitted attributes
    assert hasattr(preprocessor, "transformers_")


def test_preprocessing_handles_unknown_categories():
    """Verify OneHotEncoder ignores unseen platform or category categories during inference."""
    train_df = pd.DataFrame(
        [
            {"platform": "IOS", "category_id": 1, "language_id": 1, "plays": 10, "sessions": 10},
            {"platform": "ANDROID", "category_id": 1, "language_id": 1, "plays": 20, "sessions": 20},
        ]
    )
    # Test DataFrame contains unseen platform/category
    test_df = pd.DataFrame(
        [
            {"platform": "WEB", "category_id": 99, "language_id": 99, "plays": 15, "sessions": 15},
        ]
    )

    preprocessor = create_preprocessing_pipeline(
        numeric_features=["plays", "sessions"],
        categorical_features=["platform", "category_id", "language_id"],
    )

    X_train = preprocessor.fit_transform(train_df)
    # Transforming test_df must not raise error despite unseen categories
    X_test = preprocessor.transform(test_df)

    assert X_test.shape[0] == 1
    assert X_test.shape[1] == X_train.shape[1]
    assert not np.isnan(X_test).any()


def test_preprocessing_handles_missing_values():
    """Verify SimpleImputer handles NaN/None in numeric and categorical features."""
    train_df = pd.DataFrame(
        [
            {"platform": "IOS", "category_id": 1, "language_id": None, "plays": 10.0, "sessions": 20.0},
            {"platform": None, "category_id": None, "language_id": 1, "plays": np.nan, "sessions": 30.0},
            {"platform": "ANDROID", "category_id": 2, "language_id": 2, "plays": 40.0, "sessions": np.nan},
        ]
    )
    preprocessor = create_preprocessing_pipeline(
        numeric_features=["plays", "sessions"],
        categorical_features=["platform", "category_id", "language_id"],
    )
    X = preprocessor.fit_transform(train_df)
    assert not np.isnan(X).any()
    assert X.shape[0] == 3


def test_metadata_registry_serialization():
    """Verify FeatureMetadataResponse validates and serializes to valid JSON."""
    builder = MLDatasetBuilder()
    meta = builder.get_metadata(row_count=500, from_date=date(2026, 8, 1), to_date=date(2026, 8, 15))

    assert meta.feature_schema_version == FEATURE_SCHEMA_VERSION
    assert meta.row_count == 500
    assert meta.feature_count >= len(BASE_FEATURE_COLUMNS)
    assert meta.from_date == date(2026, 8, 1)

    json_dict = meta.model_dump(by_alias=True)
    assert json_dict["feature_schema_version"] == "features-v1"
    assert "features" in json_dict
    assert isinstance(json_dict["features"], list)


@pytest.mark.asyncio
async def test_api_features_metadata_endpoint_success():
    """Test GET /api/v1/ml/features/metadata returns valid metadata and 200 OK."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        with patch.object(
            MLDatasetBuilder,
            "fetch_and_build_features",
            new_callable=AsyncMock,
        ) as mock_fetch:
            mock_fetch.return_value = pd.DataFrame([{"dummy": 1}] * 25)

            resp = await client.get("/api/v1/ml/features/metadata?from=2026-08-01&to=2026-08-07")
            assert resp.status_code == 200
            data = resp.json()

            assert data["feature_schema_version"] == "features-v1"
            assert data["source_contract_version"] == "analytics-contract-v1"
            assert data["row_count"] == 25
            assert "features" in data
            assert len(data["features"]) >= len(BASE_FEATURE_COLUMNS)


@pytest.mark.asyncio
async def test_api_features_metadata_invalid_date_range():
    """Test GET /api/v1/ml/features/metadata returns 400 Bad Request when from > to."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/api/v1/ml/features/metadata?from=2026-08-10&to=2026-08-01")
        assert resp.status_code == 400
        assert "cannot be after" in resp.json()["error"]["message"]


@pytest.mark.asyncio
async def test_api_features_metadata_upstream_contract_error():
    """Test GET /api/v1/ml/features/metadata returns 502 Bad Gateway on upstream contract error."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        with patch.object(
            MLDatasetBuilder,
            "fetch_and_build_features",
            new_callable=AsyncMock,
        ) as mock_fetch:
            mock_fetch.side_effect = AnalyticsContractError("Invalid schema envelope")

            resp = await client.get("/api/v1/ml/features/metadata?from=2026-08-01&to=2026-08-07")
            assert resp.status_code == 502
            assert "Contract validation failure" in resp.json()["error"]["message"]


def test_prepare_bundle_smoke_execution(multi_week_feature_df: pd.DataFrame):
    """Smoke test dataset bundle preparation ensuring all splits and arrays are generated properly."""
    builder = MLDatasetBuilder()
    with patch.object(builder, "fetch_and_build_features", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = multi_week_feature_df

        train_df, val_df, test_df, split_info = builder.split_chronologically(multi_week_feature_df)
        preprocessor, X_train, X_val, X_test = fit_and_transform_splits(
            train_df=train_df, val_df=val_df, test_df=test_df
        )

        assert X_train is not None
        assert X_val is not None
        assert X_test is not None
        assert X_train.ndim == 2


def test_pipeline_transforms_validation_with_train_mean_std():
    """Verify standard scaler uses training statistics when scaling validation split."""
    train_df = pd.DataFrame(
        [
            {"platform": "IOS", "category_id": 1, "language_id": 1, "plays": 100.0, "sessions": 100.0},
            {"platform": "IOS", "category_id": 1, "language_id": 1, "plays": 200.0, "sessions": 200.0},
        ]
    )
    val_df = pd.DataFrame(
        [
            {"platform": "IOS", "category_id": 1, "language_id": 1, "plays": 150.0, "sessions": 150.0},
        ]
    )

    preprocessor, X_train, X_val, _ = fit_and_transform_splits(
        train_df=train_df,
        val_df=val_df,
        numeric_features=["plays", "sessions"],
        categorical_features=["platform"],
    )

    # In train: mean of plays is 150.0. For val (value 150.0), normalized value should be 0.0
    assert abs(X_val[0, 0]) < 1e-4  # index 0 is plays (normalized with train mean 150)


def test_dataset_builder_split_info_contents(multi_week_feature_df: pd.DataFrame):
    """Verify split_info dictionary contains comprehensive date and row count tracking."""
    builder = MLDatasetBuilder()
    _, _, _, split_info = builder.split_chronologically(multi_week_feature_df)
    assert "train_dates" in split_info
    assert "val_dates" in split_info
    assert "test_dates" in split_info
    assert "train_count" in split_info
    assert split_info["train_count"] > 0
    assert split_info["val_count"] > 0
    assert split_info["test_count"] > 0


@pytest.mark.asyncio
async def test_api_features_metadata_empty_range():
    """Test GET /api/v1/ml/features/metadata with no dates defaults cleanly."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        with patch.object(
            MLDatasetBuilder,
            "fetch_and_build_features",
            new_callable=AsyncMock,
        ) as mock_fetch:
            mock_fetch.return_value = pd.DataFrame()

            resp = await client.get("/api/v1/ml/features/metadata")
            assert resp.status_code == 200
            data = resp.json()
            assert data["row_count"] == 0
            assert len(data["features"]) > 0


def test_end_to_end_ml_feature_generation_flow(multi_week_feature_df: pd.DataFrame):
    """Verify entire flow from feature building to preprocessed array generation."""
    preprocessor = create_preprocessing_pipeline()
    X = preprocessor.fit_transform(multi_week_feature_df)
    assert X.shape[0] == len(multi_week_feature_df)
    assert not np.isnan(X).any()


def test_chronological_split_empty_dataframe():
    """Verify split_chronologically safely returns empty slices on empty dataframe."""
    builder = MLDatasetBuilder()
    train_df, val_df, test_df, split_info = builder.split_chronologically(pd.DataFrame())
    assert train_df.empty
    assert val_df.empty
    assert test_df.empty
    assert split_info["train_count"] == 0


def test_feature_metadata_response_defaults():
    """Verify FeatureMetadataResponse sets default schema version properly."""
    builder = MLDatasetBuilder()
    meta = builder.get_metadata(row_count=10)
    assert meta.feature_schema_version == "features-v1"
    assert meta.feature_count > 0


@pytest.mark.asyncio
async def test_dataset_builder_fetch_and_build_features_with_targets():
    """Verify MLDatasetBuilder builds features including target columns when requested."""
    from app.schemas.contract import AnalyticsExportRecord

    builder = MLDatasetBuilder()
    with patch.object(builder.client, "fetch_all", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = [
            AnalyticsExportRecord(
                date=date(2026, 8, 1),
                content_id=1,
                category_id=1,
                language_id=1,
                platform="IOS",
                sessions=10,
                plays=10,
                unique_viewers=10,
                watch_time_seconds=600,
                completed_plays=5,
                completion_rate=0.5,
                buffering_events=0,
                playback_errors=0,
                quality_changes=0,
            ),
            AnalyticsExportRecord(
                date=date(2026, 8, 2),
                content_id=1,
                category_id=1,
                language_id=1,
                platform="IOS",
                sessions=20,
                plays=20,
                unique_viewers=20,
                watch_time_seconds=1200,
                completed_plays=10,
                completion_rate=0.5,
                buffering_events=0,
                playback_errors=0,
                quality_changes=0,
            ),
        ]
        df = await builder.fetch_and_build_features(include_targets=True)
        assert len(df) == 2
        assert "target_next_day_plays" in df.columns
        assert df.iloc[0]["target_next_day_plays"] == 20.0



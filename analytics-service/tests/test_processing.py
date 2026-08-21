"""Comprehensive unit tests for DataFrame builder, validation, cleaning, aggregations, and statistics."""

import datetime as dt
import numpy as np
import pandas as pd
import pytest

from app.processing import (
    DataValidationError,
    SensitiveDataError,
    aggregate_by_category,
    aggregate_by_content,
    aggregate_by_date,
    aggregate_by_language,
    aggregate_by_platform,
    build_dataframe,
    calculate_metric_statistics,
    clean_dataframe,
    get_top_content,
    validate_dataframe,
)
from app.schemas.contract import AnalyticsExportRecord, PlatformEnum


def create_sample_record(
    content_id=101,
    metric_date="2026-08-18",
    category_id=5,
    language_id=2,
    platform=PlatformEnum.IOS,
    sessions=100,
    plays=80,
    unique_viewers=60,
    watch_time_seconds=16000,
    completed_plays=40,
    completion_rate=0.5000,
    buffering_events=5,
    playback_errors=1,
    quality_changes=10,
):
    return AnalyticsExportRecord(
        date=dt.date.fromisoformat(metric_date) if isinstance(metric_date, str) else metric_date,
        content_id=content_id,
        category_id=category_id,
        language_id=language_id,
        platform=platform,
        sessions=sessions,
        plays=plays,
        unique_viewers=unique_viewers,
        watch_time_seconds=watch_time_seconds,
        completed_plays=completed_plays,
        completion_rate=completion_rate,
        buffering_events=buffering_events,
        playback_errors=playback_errors,
        quality_changes=quality_changes,
    )


# 1. DataFrame creation
def test_dataframe_creation():
    records = [create_sample_record()]
    df = build_dataframe(records)
    assert isinstance(df, pd.DataFrame)
    assert len(df) == 1
    assert df["content_id"].iloc[0] == 101


# 2. DataFrame columns
def test_dataframe_columns():
    records = [create_sample_record()]
    df = build_dataframe(records)
    expected = [
        "date", "content_id", "category_id", "language_id", "platform",
        "sessions", "plays", "unique_viewers", "watch_time_seconds",
        "completed_plays", "completion_rate", "buffering_events",
        "playback_errors", "quality_changes"
    ]
    assert list(df.columns) == expected


# 3. Data types
def test_dataframe_data_types():
    records = [create_sample_record()]
    df = build_dataframe(records)
    assert pd.api.types.is_datetime64_any_dtype(df["date"])
    assert df["content_id"].dtype == np.int64
    assert pd.api.types.is_extension_array_dtype(df["category_id"])  # Int64 nullable
    assert pd.api.types.is_extension_array_dtype(df["language_id"])  # Int64 nullable
    assert pd.api.types.is_string_dtype(df["platform"])
    assert df["sessions"].dtype == np.int64
    assert df["plays"].dtype == np.int64
    assert pd.api.types.is_float_dtype(df["completion_rate"])


# 4. Missing required column
def test_missing_required_column_rejection():
    df = build_dataframe([create_sample_record()])
    df_missing = df.drop(columns=["plays"])
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df_missing)
    assert "Missing required columns" in str(exc.value)


# 5. Negative metric rejection
def test_negative_metric_rejection():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "sessions"] = -1
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "Negative values detected" in str(exc.value)


# 6. Invalid completion rate
def test_invalid_completion_rate_rejection():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "completion_rate"] = 1.2
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "completion_rate must be bounded" in str(exc.value)


# 7. NaN rejection in non-nullable field
def test_nan_rejection_in_metric():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "plays"] = None
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "Null or NaN values detected" in str(exc.value)


# 8. Infinity rejection
def test_infinity_rejection():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "completion_rate"] = np.inf
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "Infinite value detected" in str(exc.value)


# 9. Invalid platform rejection
def test_invalid_platform_rejection():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "platform"] = "SMART_TV"
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "Invalid platform values detected" in str(exc.value)


# 10. completed_plays > plays rejection
def test_completed_plays_exceeds_plays_rejection():
    df = build_dataframe([create_sample_record()])
    df.loc[0, "plays"] = 10
    df.loc[0, "completed_plays"] = 15
    with pytest.raises(DataValidationError) as exc:
        validate_dataframe(df)
    assert "completed_plays exceeds plays" in str(exc.value)


# 11. Aggregation by content
def test_aggregate_by_content():
    rec1 = create_sample_record(content_id=101, plays=50, completed_plays=25)
    rec2 = create_sample_record(content_id=101, plays=50, completed_plays=15)
    rec3 = create_sample_record(content_id=102, plays=10, completed_plays=5)

    df = build_dataframe([rec1, rec2, rec3])
    agg = aggregate_by_content(df)

    assert len(agg) == 2
    row_101 = agg[agg["content_id"] == 101].iloc[0]
    assert row_101["plays"] == 100
    assert row_101["completed_plays"] == 40
    assert row_101["completion_rate"] == 0.4000


# 12. Aggregation by category
def test_aggregate_by_category():
    rec1 = create_sample_record(category_id=5, plays=100, completed_plays=50)
    rec2 = create_sample_record(category_id=5, plays=50, completed_plays=25)
    rec3 = create_sample_record(category_id=None, plays=20, completed_plays=10)

    df = build_dataframe([rec1, rec2, rec3])
    agg = aggregate_by_category(df)

    assert len(agg) == 2
    row_cat5 = agg[agg["category_id"] == 5].iloc[0]
    assert row_cat5["plays"] == 150
    assert row_cat5["completion_rate"] == 0.5000


# 13. Aggregation by language
def test_aggregate_by_language():
    rec1 = create_sample_record(language_id=2, plays=80, completed_plays=40)
    rec2 = create_sample_record(language_id=3, plays=40, completed_plays=20)

    df = build_dataframe([rec1, rec2])
    agg = aggregate_by_language(df)

    assert len(agg) == 2
    row_lang2 = agg[agg["language_id"] == 2].iloc[0]
    assert row_lang2["plays"] == 80
    assert row_lang2["completion_rate"] == 0.5000


# 14. Aggregation by platform
def test_aggregate_by_platform():
    rec1 = create_sample_record(platform=PlatformEnum.IOS, plays=100, completed_plays=50)
    rec2 = create_sample_record(platform=PlatformEnum.IOS, plays=50, completed_plays=25)
    rec3 = create_sample_record(platform=PlatformEnum.ANDROID, plays=30, completed_plays=15)

    df = build_dataframe([rec1, rec2, rec3])
    agg = aggregate_by_platform(df)

    assert len(agg) == 2
    ios_row = agg[agg["platform"] == "IOS"].iloc[0]
    assert ios_row["plays"] == 150
    assert ios_row["completion_rate"] == 0.5000


# 15. Aggregation by date
def test_aggregate_by_date():
    rec1 = create_sample_record(metric_date="2026-08-18", plays=50, completed_plays=25)
    rec2 = create_sample_record(metric_date="2026-08-18", plays=50, completed_plays=25)
    rec3 = create_sample_record(metric_date="2026-08-19", plays=70, completed_plays=35)

    df = build_dataframe([rec1, rec2, rec3])
    agg = aggregate_by_date(df)

    assert len(agg) == 2
    assert agg["plays"].sum() == 170


# 16. Completion rate recalculation formula
def test_completion_rate_recalculation():
    # Individual rates: 1.0 (10/10) and 0.0 (0/100). Average would be 0.50, but correct recalculated is 10/110 = 0.0909
    rec1 = create_sample_record(content_id=1, plays=10, completed_plays=10, completion_rate=1.0)
    rec2 = create_sample_record(content_id=1, plays=100, completed_plays=0, completion_rate=0.0)

    df = build_dataframe([rec1, rec2])
    agg = aggregate_by_content(df)

    assert agg["completion_rate"].iloc[0] == 0.0909  # 10 / 110


# 17. Zero-play handling
def test_zero_play_handling():
    rec1 = create_sample_record(content_id=1, plays=0, completed_plays=0, completion_rate=0.0)
    df = build_dataframe([rec1])
    agg = aggregate_by_content(df)

    assert agg["completion_rate"].iloc[0] == 0.0


# 18. Mean calculation
def test_mean_calculation():
    rec1 = create_sample_record(plays=10)
    rec2 = create_sample_record(plays=20)
    rec3 = create_sample_record(plays=30)

    df = build_dataframe([rec1, rec2, rec3])
    stats = calculate_metric_statistics(df)

    assert stats["plays"]["mean"] == 20.0
    assert stats["plays"]["count"] == 3


# 19. Median calculation
def test_median_calculation():
    rec1 = create_sample_record(plays=10)
    rec2 = create_sample_record(plays=50)
    rec3 = create_sample_record(plays=100)

    df = build_dataframe([rec1, rec2, rec3])
    stats = calculate_metric_statistics(df)

    assert stats["plays"]["median"] == 50.0


# 20. Standard deviation (sample ddof=1)
def test_standard_deviation():
    # values: [10, 20, 30] -> sample variance = ((10-20)^2 + (20-20)^2 + (30-20)^2) / 2 = 200 / 2 = 100 -> std = 10.0
    rec1 = create_sample_record(plays=10)
    rec2 = create_sample_record(plays=20)
    rec3 = create_sample_record(plays=30)

    df = build_dataframe([rec1, rec2, rec3])
    stats = calculate_metric_statistics(df)

    assert stats["plays"]["standard_deviation"] == 10.0

    # Single sample case: std must equal 0.0
    single_df = build_dataframe([rec1])
    single_stats = calculate_metric_statistics(single_df)
    assert single_stats["plays"]["standard_deviation"] == 0.0


# 21. Top content ranking
def test_top_content_ranking():
    rec1 = create_sample_record(content_id=1, plays=10)
    rec2 = create_sample_record(content_id=2, plays=50)
    rec3 = create_sample_record(content_id=3, plays=30)

    df = build_dataframe([rec1, rec2, rec3])
    top = get_top_content(df, by="plays", limit=2)

    assert len(top) == 2
    assert top.iloc[0]["content_id"] == 2
    assert top.iloc[1]["content_id"] == 3


# 22. Deterministic tie-breaking
def test_deterministic_tie_breaking():
    # Same plays=50, should order by content_id ASC (101 before 105)
    rec1 = create_sample_record(content_id=105, plays=50)
    rec2 = create_sample_record(content_id=101, plays=50)

    df = build_dataframe([rec1, rec2])
    top = get_top_content(df, by="plays", limit=2)

    assert top.iloc[0]["content_id"] == 101
    assert top.iloc[1]["content_id"] == 105


# 23. Empty dataset handling
def test_empty_dataset_handling():
    df = build_dataframe([])
    validate_dataframe(df)
    stats = calculate_metric_statistics(df)
    assert stats["plays"]["count"] == 0
    assert stats["plays"]["mean"] == 0.0

    top = get_top_content(df)
    assert top.empty


# 24. Controlled data cleaning & deduplication
def test_controlled_cleaning():
    rec1 = create_sample_record(content_id=101, metric_date="2026-08-18", platform=PlatformEnum.IOS, plays=50)
    rec2 = create_sample_record(content_id=101, metric_date="2026-08-18", platform=PlatformEnum.IOS, plays=50)

    df = build_dataframe([rec1, rec2])
    assert len(df) == 2
    cleaned = clean_dataframe(df)
    assert len(cleaned) == 1


# 25. PII column rejection
def test_pii_column_rejection():
    df = build_dataframe([create_sample_record()])
    df["user_id"] = 12345
    with pytest.raises(SensitiveDataError) as exc:
        validate_dataframe(df)
    assert "Sensitive PII columns detected" in str(exc.value)

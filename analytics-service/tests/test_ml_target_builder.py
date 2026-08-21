"""Tests for Phase 7.6 Checkpoint 2: Next-Day Target Generation."""

from datetime import date
import numpy as np
import pandas as pd
import pytest

from app.ml.models.target_builder import TARGET_COLUMN_PLAYS, build_next_day_target


def test_1_basic_next_day_target_generation():
    """Verify basic next-day target generation for a single series."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 3), "plays": 30},
    ])
    res = build_next_day_target(df)
    assert list(res[TARGET_COLUMN_PLAYS])[:2] == [20.0, 30.0]
    assert pd.isna(res[TARGET_COLUMN_PLAYS].iloc[2])


def test_2_last_observation_has_null_target():
    """Verify last observation for a content/platform group has NaN target."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 100},
    ])
    res = build_next_day_target(df)
    assert pd.isna(res[TARGET_COLUMN_PLAYS].iloc[0])


def test_3_multiple_content_ids_isolation():
    """Verify content IDs are isolated during target calculation."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
        {"content_id": 2, "platform": "IOS", "date": date(2026, 8, 1), "plays": 100},
        {"content_id": 2, "platform": "IOS", "date": date(2026, 8, 2), "plays": 200},
    ])
    res = build_next_day_target(df)
    
    c1 = res[res["content_id"] == 1]
    c2 = res[res["content_id"] == 2]

    assert c1.iloc[0][TARGET_COLUMN_PLAYS] == 20.0
    assert pd.isna(c1.iloc[1][TARGET_COLUMN_PLAYS])
    assert c2.iloc[0][TARGET_COLUMN_PLAYS] == 200.0
    assert pd.isna(c2.iloc[1][TARGET_COLUMN_PLAYS])


def test_4_multiple_platforms_isolation():
    """Verify platforms (e.g. IOS vs WEB) for the same content_id are isolated."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
        {"content_id": 1, "platform": "WEB", "date": date(2026, 8, 1), "plays": 500},
        {"content_id": 1, "platform": "WEB", "date": date(2026, 8, 2), "plays": 600},
    ])
    res = build_next_day_target(df)
    
    ios = res[res["platform"] == "IOS"]
    web = res[res["platform"] == "WEB"]

    assert ios.iloc[0][TARGET_COLUMN_PLAYS] == 20.0
    assert pd.isna(ios.iloc[1][TARGET_COLUMN_PLAYS])
    assert web.iloc[0][TARGET_COLUMN_PLAYS] == 600.0
    assert pd.isna(web.iloc[1][TARGET_COLUMN_PLAYS])


def test_5_input_ordering_handled_correctly():
    """Verify unordered input records are sorted chronologically before shifting."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 3), "plays": 30},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    res = build_next_day_target(df)
    
    # Dates must be ordered: 2026-08-01 -> 10 (target 20), 2026-08-02 -> 20 (target 30), 2026-08-03 -> 30 (target NaN)
    assert res.iloc[0]["plays"] == 10
    assert res.iloc[0][TARGET_COLUMN_PLAYS] == 20.0
    assert res.iloc[1]["plays"] == 20
    assert res.iloc[1][TARGET_COLUMN_PLAYS] == 30.0
    assert res.iloc[2]["plays"] == 30
    assert pd.isna(res.iloc[2][TARGET_COLUMN_PLAYS])


def test_6_chronological_ordering():
    """Verify string and date object dates order strictly chronologically."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": "2026-08-02", "plays": 20},
        {"content_id": 1, "platform": "IOS", "date": "2026-08-01", "plays": 10},
    ])
    res = build_next_day_target(df)
    assert res.iloc[0]["date"] == "2026-08-01"
    assert res.iloc[0][TARGET_COLUMN_PLAYS] == 20.0


def test_7_missing_dates_not_fabricated():
    """Verify date gaps (e.g. Aug 1 to Aug 5) use next available record target without inserting rows."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 5), "plays": 50},
    ])
    res = build_next_day_target(df)
    assert len(res) == 2
    assert res.iloc[0][TARGET_COLUMN_PLAYS] == 50.0


def test_8_zero_plays_preserved_correctly():
    """Verify next day target of 0 plays is preserved as 0.0, not converted to NaN."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 0},
    ])
    res = build_next_day_target(df)
    assert res.iloc[0][TARGET_COLUMN_PLAYS] == 0.0
    assert not pd.isna(res.iloc[0][TARGET_COLUMN_PLAYS])


def test_9_target_is_numeric():
    """Verify target_next_day_plays has float64 dtype."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    res = build_next_day_target(df)
    assert pd.api.types.is_float_dtype(res[TARGET_COLUMN_PLAYS])


def test_10_original_input_not_mutated():
    """Verify original input dataframe is unchanged (pure function)."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    original_cols = list(df.columns)
    _ = build_next_day_target(df)
    assert list(df.columns) == original_cols
    assert TARGET_COLUMN_PLAYS not in df.columns


def test_11_duplicate_handling_raises_value_error():
    """Verify duplicate (content_id, platform, date) records raise ValueError."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 15},
    ])
    with pytest.raises(ValueError, match="Duplicate"):
        build_next_day_target(df)


def test_12_empty_dataframe_handling():
    """Verify empty DataFrame returns empty DataFrame with target column present."""
    df = pd.DataFrame()
    res = build_next_day_target(df)
    assert res.empty
    assert TARGET_COLUMN_PLAYS in res.columns


def test_13_single_observation_handling():
    """Verify single observation returns 1 row with NaN target."""
    df = pd.DataFrame([
        {"content_id": 42, "platform": "ANDROID", "date": date(2026, 8, 1), "plays": 99},
    ])
    res = build_next_day_target(df)
    assert len(res) == 1
    assert pd.isna(res.iloc[0][TARGET_COLUMN_PLAYS])


def test_14_multiple_observations_handling():
    """Verify 5-day series target progression."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, i), "plays": i * 10}
        for i in range(1, 6)
    ])
    res = build_next_day_target(df)
    expected = np.array([20.0, 30.0, 40.0, 50.0, np.nan])
    np.testing.assert_equal(res[TARGET_COLUMN_PLAYS].values, expected)


def test_15_target_column_exact_name():
    """Verify target column name is exactly target_next_day_plays."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    res = build_next_day_target(df)
    assert TARGET_COLUMN_PLAYS in res.columns
    assert TARGET_COLUMN_PLAYS == "target_next_day_plays"


def test_16_no_future_feature_created():
    """Verify ONLY target_next_day_plays is created (no unintended extra columns)."""
    df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    res = build_next_day_target(df)
    added_cols = set(res.columns) - set(df.columns)
    assert added_cols == {TARGET_COLUMN_PLAYS}

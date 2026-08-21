"""DataFrame construction and schema enforcement from AnalyticsExportRecord instances."""

from typing import List
import pandas as pd
import numpy as np

from app.schemas.contract import AnalyticsExportRecord
from app.processing.data_validator import REQUIRED_COLUMNS


def build_dataframe(records: List[AnalyticsExportRecord]) -> pd.DataFrame:
    """Build and strongly type a pandas DataFrame from a list of validated AnalyticsExportRecords.

    Args:
        records: List of AnalyticsExportRecord models.

    Returns:
        pd.DataFrame with explicit schema and data types adhering to analytics-contract-v1.
    """
    if not records:
        # Create empty DataFrame with exact expected dtypes
        empty_df = pd.DataFrame(
            {
                "date": pd.Series(dtype="datetime64[ns]"),
                "content_id": pd.Series(dtype="int64"),
                "category_id": pd.Series(dtype="Int64"),
                "language_id": pd.Series(dtype="Int64"),
                "platform": pd.Series(dtype="string"),
                "sessions": pd.Series(dtype="int64"),
                "plays": pd.Series(dtype="int64"),
                "unique_viewers": pd.Series(dtype="int64"),
                "watch_time_seconds": pd.Series(dtype="int64"),
                "completed_plays": pd.Series(dtype="int64"),
                "completion_rate": pd.Series(dtype="float64"),
                "buffering_events": pd.Series(dtype="int64"),
                "playback_errors": pd.Series(dtype="int64"),
                "quality_changes": pd.Series(dtype="int64"),
            }
        )
        return empty_df[REQUIRED_COLUMNS]

    # Convert records to dict representations
    data = [record.model_dump() for record in records]
    df = pd.DataFrame(data)

    # Cast to explicit target dtypes
    df["date"] = pd.to_datetime(df["date"], utc=True).dt.tz_localize(None)
    df["content_id"] = df["content_id"].astype("int64")
    df["category_id"] = df["category_id"].astype("Int64")
    df["language_id"] = df["language_id"].astype("Int64")
    df["platform"] = df["platform"].astype(str).str.upper().astype("string")
    df["sessions"] = df["sessions"].astype("int64")
    df["plays"] = df["plays"].astype("int64")
    df["unique_viewers"] = df["unique_viewers"].astype("int64")
    df["watch_time_seconds"] = df["watch_time_seconds"].astype("int64")
    df["completed_plays"] = df["completed_plays"].astype("int64")
    df["completion_rate"] = df["completion_rate"].astype("float64")
    df["buffering_events"] = df["buffering_events"].astype("int64")
    df["playback_errors"] = df["playback_errors"].astype("int64")
    df["quality_changes"] = df["quality_changes"].astype("int64")

    return df[REQUIRED_COLUMNS]

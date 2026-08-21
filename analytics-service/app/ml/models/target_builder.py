"""Target builder for generating next-day prediction targets without data leakage."""

from typing import List, Optional
import numpy as np
import pandas as pd

TARGET_COLUMN_PLAYS = "target_next_day_plays"
REQUIRED_TARGET_BUILDER_COLUMNS = ["content_id", "platform", "date", "plays"]


def build_next_day_target(
    df: pd.DataFrame,
    target_col: str = TARGET_COLUMN_PLAYS,
) -> pd.DataFrame:
    """
    Generate target_next_day_plays for each content_id + platform group.

    Definition:
        For each content_id + platform unit, sort chronologically by date.
        target_next_day_plays = next chronological record's plays (via shift(-1)).
        The final chronological record for each content_id + platform unit will
        have a NaN/NULL target until training dataset preparation.

    Args:
        df: Input feature or raw analytics DataFrame.
        target_col: Output target column name. Default "target_next_day_plays".

    Returns:
        New DataFrame copy with the target column appended.

    Raises:
        ValueError: If required columns are missing or if duplicate
                    (content_id, platform, date) records are detected.
    """
    if df.empty:
        result = df.copy()
        result[target_col] = pd.Series(dtype="float64")
        return result

    # 1. Validate required columns
    missing_cols = [col for col in REQUIRED_TARGET_BUILDER_COLUMNS if col not in df.columns]
    if missing_cols:
        raise ValueError(
            f"Target generation requires column(s): {missing_cols}. Provided columns: {list(df.columns)}"
        )

    # 2. Duplicate checking Invariant: (content_id, platform, date) must be unique
    duplicates = df.duplicated(subset=["content_id", "platform", "date"], keep=False)
    if duplicates.any():
        dup_rows = df[duplicates][["content_id", "platform", "date"]]
        raise ValueError(
            f"Duplicate (content_id, platform, date) records detected in input dataset:\n{dup_rows.head()}"
        )

    # 3. Create independent copy & sort chronologically by content_id, platform, date
    result_df = df.copy()
    result_df["_date_sort"] = pd.to_datetime(result_df["date"])
    result_df = result_df.sort_values(["content_id", "platform", "_date_sort"]).reset_index(drop=True)

    # 4. Calculate next-day target via shift(-1) per group
    result_df[target_col] = (
        result_df.groupby(["content_id", "platform"])["plays"]
        .shift(-1)
        .astype("float64")
    )

    # Clean up temporary sort key
    result_df = result_df.drop(columns=["_date_sort"])

    return result_df

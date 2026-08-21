"""Controlled data cleaning transformations for analytics DataFrames."""

import logging
import pandas as pd

logger = logging.getLogger("communityott.analytics.processing.cleaner")


def clean_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    """Apply controlled and deterministic cleaning transformations to an analytics DataFrame.

    Transformations performed:
    1. Removes exact duplicate contract metric rows if present (deduplication on grain:
       date, content_id, platform).
    2. Strips whitespace and forces uppercase on 'platform' strings.
    3. Normalizes category_id and language_id to pandas nullable Int64 dtype.
    4. Converts 'date' column to datetime64[ns] in UTC without timezone shifting.

    Returns:
        Cleaned pandas.DataFrame.
    """
    if df.empty:
        return df.copy()

    cleaned = df.copy()

    # 1. Platform normalization
    if "platform" in cleaned.columns:
        cleaned["platform"] = cleaned["platform"].astype(str).str.strip().str.upper()

    # 2. Exact contract grain deduplication
    grain_columns = ["date", "content_id", "platform"]
    if all(col in cleaned.columns for col in grain_columns):
        initial_count = len(cleaned)
        cleaned = cleaned.drop_duplicates(subset=grain_columns, keep="last")
        dedup_count = initial_count - len(cleaned)
        if dedup_count > 0:
            logger.info(f"Deduplicated {dedup_count} identical records along grain {grain_columns}")

    # 3. Category and language nullable integer normalization
    if "category_id" in cleaned.columns:
        cleaned["category_id"] = cleaned["category_id"].astype("Int64")
    if "language_id" in cleaned.columns:
        cleaned["language_id"] = cleaned["language_id"].astype("Int64")

    # 4. Date normalization to UTC datetime
    if "date" in cleaned.columns:
        cleaned["date"] = pd.to_datetime(cleaned["date"], errors="coerce")

    return cleaned

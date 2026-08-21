"""In-process thread-safe operational metrics tracker for prediction serving."""

import threading
from typing import Any, Dict, List


class PredictionMetricsTracker:
    """Tracks in-memory prediction request counts, latency statistics, and batch sizes."""

    def __init__(self, max_history: int = 10000):
        self._lock = threading.Lock()
        self.max_history = max_history
        self.total_prediction_requests: int = 0
        self.successful_prediction_requests: int = 0
        self.failed_prediction_requests: int = 0
        self.latency_ms_history: List[float] = []
        self.batch_size_history: List[int] = []

    def record_prediction(self, success: bool, latency_ms: float, batch_size: int) -> None:
        """Record prediction execution metrics thread-safely."""
        with self._lock:
            self.total_prediction_requests += 1
            if success:
                self.successful_prediction_requests += 1
            else:
                self.failed_prediction_requests += 1

            self.latency_ms_history.append(float(latency_ms))
            if len(self.latency_ms_history) > self.max_history:
                self.latency_ms_history.pop(0)

            self.batch_size_history.append(int(batch_size))
            if len(self.batch_size_history) > self.max_history:
                self.batch_size_history.pop(0)

    def get_metrics_summary(self) -> Dict[str, Any]:
        """Return operational summary without exposing individual feature or prediction values."""
        with self._lock:
            total = self.total_prediction_requests
            succ = self.successful_prediction_requests
            fail = self.failed_prediction_requests

            lats = self.latency_ms_history
            lat_count = len(lats)
            lat_avg = float(sum(lats) / lat_count) if lat_count > 0 else 0.0
            lat_min = float(min(lats)) if lat_count > 0 else 0.0
            lat_max = float(max(lats)) if lat_count > 0 else 0.0

            batches = self.batch_size_history
            batch_count = len(batches)
            batch_avg = float(sum(batches) / batch_count) if batch_count > 0 else 0.0
            batch_max = int(max(batches)) if batch_count > 0 else 0

            return {
                "prediction_requests": {
                    "total": total,
                    "successful": succ,
                    "failed": fail,
                },
                "latency_ms": {
                    "count": lat_count,
                    "average": round(lat_avg, 2),
                    "min": round(lat_min, 2),
                    "max": round(lat_max, 2),
                },
                "batch_size": {
                    "count": batch_count,
                    "average": round(batch_avg, 2),
                    "max": batch_max,
                },
            }

    def reset(self) -> None:
        """Reset metrics (useful for testing)."""
        with self._lock:
            self.total_prediction_requests = 0
            self.successful_prediction_requests = 0
            self.failed_prediction_requests = 0
            self.latency_ms_history.clear()
            self.batch_size_history.clear()


# Global metrics tracker instance
metrics_tracker = PredictionMetricsTracker()

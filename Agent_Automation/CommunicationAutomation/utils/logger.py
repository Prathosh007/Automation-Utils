import os
import sys
from datetime import datetime


class Logger:
    """Logger matching the C# Logger pattern: file logging + GOAT console output."""

    show_failures_only = True

    def __init__(self, log_dir: str = "Logs"):
        self._log_dir = log_dir
        os.makedirs(log_dir, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self._log_file = os.path.join(log_dir, f"execution-{timestamp}.log")
        self.log_to_file("Logger initialized")

    def log_to_file(self, message: str) -> None:
        """Write a timestamped message to the log file only."""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        try:
            with open(self._log_file, "a", encoding="utf-8") as f:
                f.write(f"[{timestamp}] {message}\n")
        except OSError:
            pass

    @staticmethod
    def output_remark(test_case_id: str, passed: bool, remarks: str) -> None:
        """Output a GOAT-compatible step result line to stdout."""
        if Logger.show_failures_only and passed:
            return
        status = "PASSED" if passed else "FAILED"
        print(f"{test_case_id}|{status}|{remarks}", flush=True)

    @staticmethod
    def output_summary(total: int, passed: int, failed: int) -> None:
        """Output the GOAT-compatible summary line to stdout."""
        print(f"SUMMARY|Total:{total}|Passed:{passed}|Failed:{failed}", flush=True)

    @staticmethod
    def output_critical_error(message: str) -> None:
        """Output a critical error line to stdout."""
        print(f"CRITICAL_ERROR|{message}", flush=True)

    @staticmethod
    def output_file_not_found(file_path: str) -> None:
        """Output a file-not-found line to stdout."""
        print(f"FILE_NOT_FOUND|{file_path}", flush=True)

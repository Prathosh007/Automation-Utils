import json
import logging
import os
import tempfile
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

from utils.pattern_matcher import matches_pattern

logger = logging.getLogger(__name__)


@dataclass
class CapturedFlow:
    """A captured HTTP(S) request/response pair."""
    timestamp: datetime = field(default_factory=datetime.now)
    # Request
    request_url: str = ""
    request_method: str = ""
    request_headers: dict = field(default_factory=dict)
    request_body: str = ""
    # Response
    response_status: int = 0
    response_headers: dict = field(default_factory=dict)
    response_body: str = ""
    response_reason: str = ""

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp.isoformat(),
            "request": {
                "url": self.request_url,
                "method": self.request_method,
                "headers": self.request_headers,
                "body": self.request_body,
            },
            "response": {
                "status": self.response_status,
                "headers": self.response_headers,
                "body": self.response_body,
                "reason": self.response_reason,
            },
        }


class TrafficStore:
    """Thread-safe captured flow storage with wait/notify support.

    If ``live_capture_file`` is set, every captured flow is appended
    to that JSON file in real time so other processes can read the
    traffic without the proxy being in the same process.
    """

    def __init__(self, live_capture_file: str = ""):
        self._lock = threading.Lock()
        self._flows: list[CapturedFlow] = []
        self._waiters: list[tuple[str, str, threading.Event]] = []
        self._live_capture_file = live_capture_file
        if self._live_capture_file:
            os.makedirs(os.path.dirname(self._live_capture_file) or ".", exist_ok=True)
            # Start with an empty JSON array
            with open(self._live_capture_file, "w", encoding="utf-8") as f:
                json.dump([], f)

    def add_flow(self, flow: CapturedFlow) -> None:
        """Add a captured flow, notify matching waiters, and write to live file."""
        with self._lock:
            self._flows.append(flow)

            # Write to live capture file
            if self._live_capture_file:
                self._write_live_capture()

            # Notify waiters whose pattern matches
            remaining = []
            for url_pattern, method, event in self._waiters:
                if self._flow_matches(flow, url_pattern, method):
                    event.set()
                else:
                    remaining.append((url_pattern, method, event))
            self._waiters = remaining

    def wait_for_request(self, url_pattern: str, method: str = "",
                         timeout: float = 60.0) -> Optional[CapturedFlow]:
        """Block until a matching request arrives or timeout. Returns the flow or None."""
        # Check existing flows first
        with self._lock:
            for flow in reversed(self._flows):
                if self._flow_matches(flow, url_pattern, method):
                    return flow

            # Register a waiter
            event = threading.Event()
            self._waiters.append((url_pattern, method, event))

        # Wait outside the lock
        if event.wait(timeout=timeout):
            # Find the matching flow
            with self._lock:
                for flow in reversed(self._flows):
                    if self._flow_matches(flow, url_pattern, method):
                        return flow
        return None

    def get_matching_flows(self, url_pattern: str = "", method: str = "") -> list[CapturedFlow]:
        """Return all flows matching the given pattern."""
        with self._lock:
            if not url_pattern and not method:
                return list(self._flows)
            return [f for f in self._flows if self._flow_matches(f, url_pattern, method)]

    def get_latest_flow(self, url_pattern: str = "", method: str = "") -> Optional[CapturedFlow]:
        """Return the most recent matching flow."""
        with self._lock:
            for flow in reversed(self._flows):
                if self._flow_matches(flow, url_pattern, method):
                    return flow
        return None

    def clear(self) -> int:
        """Clear all captured flows. Returns count of cleared flows."""
        with self._lock:
            count = len(self._flows)
            self._flows.clear()
            return count

    def count(self, url_pattern: str = "", method: str = "") -> int:
        """Count flows matching the given pattern."""
        with self._lock:
            if not url_pattern and not method:
                return len(self._flows)
            return sum(1 for f in self._flows if self._flow_matches(f, url_pattern, method))

    def get_all_flows(self) -> list[CapturedFlow]:
        """Return a copy of all captured flows."""
        with self._lock:
            return list(self._flows)

    @staticmethod
    def _flow_matches(flow: CapturedFlow, url_pattern: str, method: str) -> bool:
        if url_pattern and not matches_pattern(flow.request_url, url_pattern):
            return False
        if method and flow.request_method.upper() != method.upper():
            return False
        return True

    def switch_live_capture_file(self, new_path: str, clear_flows: bool = True) -> str:
        """Switch the live capture file at runtime.

        Args:
            new_path: New file path for live traffic capture.
            clear_flows: If True (default), clears in-memory flows and starts
                         the new file fresh. If False, carries existing flows
                         into the new file.

        Returns:
            The previous live capture file path.
        """
        with self._lock:
            old_path = self._live_capture_file
            self._live_capture_file = new_path
            if new_path:
                os.makedirs(os.path.dirname(new_path) or ".", exist_ok=True)
                if clear_flows:
                    self._flows.clear()
                    with open(new_path, "w", encoding="utf-8") as f:
                        json.dump([], f)
                else:
                    self._write_live_capture()
            return old_path

    def _write_live_capture(self) -> None:
        """Write all flows to the live capture file atomically. Must be called under lock.

        Uses write-to-temp-then-rename to prevent readers from seeing
        truncated / partial JSON. Retries on Windows sharing violations.
        """
        if not self._live_capture_file:
            return
        max_retries = 3
        retry_delay = 0.1  # seconds
        target = self._live_capture_file
        dir_name = os.path.dirname(target) or "."

        for attempt in range(max_retries):
            fd = None
            tmp_path = None
            try:
                data = [f.to_dict() for f in self._flows]
                # Write to a temp file in the same directory (same filesystem for rename)
                fd, tmp_path = tempfile.mkstemp(
                    suffix=".tmp", prefix=".traffic_", dir=dir_name)
                with os.fdopen(fd, "w", encoding="utf-8") as f:
                    fd = None  # os.fdopen takes ownership
                    json.dump(data, f, indent=2, ensure_ascii=False)
                # Atomic replace (on Windows, os.replace handles cross-process safely)
                os.replace(tmp_path, target)
                return
            except PermissionError:
                # Windows sharing violation — reader has the file open
                if tmp_path and os.path.exists(tmp_path):
                    try:
                        os.remove(tmp_path)
                    except OSError:
                        pass
                if attempt < max_retries - 1:
                    time.sleep(retry_delay)
                    retry_delay *= 2
                else:
                    logger.warning(
                        "Failed to write live capture after %d retries "
                        "(file likely locked by reader): %s", max_retries, target)
            except Exception as e:
                logger.debug("Live capture write error: %s", e)
                if tmp_path and os.path.exists(tmp_path):
                    try:
                        os.remove(tmp_path)
                    except OSError:
                        pass
                return
            finally:
                if fd is not None:
                    try:
                        os.close(fd)
                    except OSError:
                        pass

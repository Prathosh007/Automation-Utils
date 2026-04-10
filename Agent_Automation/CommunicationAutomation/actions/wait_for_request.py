import json
import os
import time
from datetime import datetime

from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.traffic_store import TrafficStore, CapturedFlow
from utils.pattern_matcher import matches_pattern


class WaitForRequestAction(BaseAction):
    """Block until a matching request arrives (with timeout).

    Works in two modes:

    1. **In-process** – polls the in-memory ``TrafficStore`` (when proxy
       runs in the same process).
    2. **File-polling** – if ``captureFile`` is set *or* no in-memory store
       exists, periodically re-reads the live capture JSON written by a
       background proxy and checks for a matching flow.  Once found, all
       flows are loaded into ``context["traffic_store"]`` so subsequent
       ``validate_request`` / ``validate_response`` / ``assert_*`` actions
       work transparently.
    """

    POLL_INTERVAL = 3  # seconds between file re-reads

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        timeout = command.timeout or 60

        # Decide mode: file-polling when captureFile is given OR no in-memory store
        capture_file = command.capture_file
        store = context.get("traffic_store")

        if capture_file or not store:
            return self._wait_file(command, result, context, capture_file, timeout)

        # In-process mode: delegate to TrafficStore.wait_for_request
        flow = store.wait_for_request(
            url_pattern=command.url_pattern,
            method=command.method,
            timeout=float(timeout),
        )

        if flow:
            result.data = flow.to_dict()
            self.handle_success(
                command, result,
                f"[wait_for_request] Pattern: '{command.url_pattern}' | "
                f"Matched: {flow.request_method} {flow.request_url} | Result: PASSED"
            )
        else:
            self.handle_failure(
                command, result,
                f"[wait_for_request] Pattern: '{command.url_pattern}' | "
                f"Timeout: {timeout}s | Error: No matching request found | Result: FAILED"
            )
        return result

    # ---- file-polling mode ------------------------------------------------

    def _wait_file(self, command: Command, result, context: dict,
                   capture_file: str, timeout: int):
        """Poll a live capture JSON file until a matching flow appears."""
        if not capture_file:
            self.handle_failure(command, result,
                                "[wait_for_request] Error: No traffic store and no captureFile specified | Result: FAILED")
            return result

        if self.logger:
            self.logger.log_to_file(
                f"wait_for_request: file-polling mode on {capture_file} "
                f"(timeout {timeout}s, poll every {self.POLL_INTERVAL}s)")

        deadline = time.monotonic() + timeout
        matched_flow = None
        last_count = 0

        while time.monotonic() < deadline:
            flows = self._load_flows(capture_file)
            if flows is not None:
                # Only scan new flows (optimisation for large files)
                for flow in flows[last_count:]:
                    if self._flow_matches(flow, command.url_pattern, command.method):
                        matched_flow = flow
                        break
                last_count = len(flows)

            if matched_flow:
                break

            remaining = deadline - time.monotonic()
            time.sleep(min(self.POLL_INTERVAL, max(remaining, 0)))

        if matched_flow:
            # Load ALL flows into context so subsequent actions can validate
            self._populate_store(context, capture_file)
            result.data = matched_flow.to_dict()
            self.handle_success(
                command, result,
                f"[wait_for_request] Pattern: '{command.url_pattern}' | Mode: file-poll | "
                f"Matched: {matched_flow.request_method} {matched_flow.request_url} | Result: PASSED"
            )
        else:
            self.handle_failure(
                command, result,
                f"[wait_for_request] Pattern: '{command.url_pattern}' | Mode: file-poll | "
                f"File: '{capture_file}' | Timeout: {timeout}s | "
                f"Error: No matching request found | Result: FAILED"
            )
        return result

    def _load_flows(self, path: str) -> list[CapturedFlow] | None:
        """Read the live capture file and return parsed flows, or None on error.

        Retries on transient read failures (e.g. Windows sharing violation
        while the proxy is atomically replacing the file).
        """
        if not os.path.exists(path):
            return None
        max_retries = 3
        delay = 0.05
        for attempt in range(max_retries):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    raw = f.read()
                if not raw.strip():
                    return []
                data = json.loads(raw)
                if not isinstance(data, list):
                    return None
                flows = []
                for entry in data:
                    req = entry.get("request", {})
                    resp = entry.get("response", {})
                    flows.append(CapturedFlow(
                        timestamp=datetime.fromisoformat(
                            entry.get("timestamp", datetime.now().isoformat())),
                        request_url=req.get("url", ""),
                        request_method=req.get("method", ""),
                        request_headers=req.get("headers", {}),
                        request_body=req.get("body", ""),
                        response_status=resp.get("status", 0),
                        response_headers=resp.get("headers", {}),
                        response_body=resp.get("body", ""),
                        response_reason=resp.get("reason", ""),
                    ))
                return flows
            except (json.JSONDecodeError, PermissionError):
                # Transient: file being rewritten or partial read — retry
                if attempt < max_retries - 1:
                    time.sleep(delay)
                    delay *= 2
                    continue
                return None
            except (OSError, ValueError):
                return None

    def _populate_store(self, context: dict, path: str) -> None:
        """Load all flows from file into context traffic_store."""
        flows = self._load_flows(path)
        if not flows:
            return
        store = context.get("traffic_store")
        if not store:
            store = TrafficStore()
            context["traffic_store"] = store
        for flow in flows:
            store.add_flow(flow)

    @staticmethod
    def _flow_matches(flow: CapturedFlow, url_pattern: str, method: str) -> bool:
        if url_pattern and not matches_pattern(flow.request_url, url_pattern):
            return False
        if method and flow.request_method.upper() != method.upper():
            return False
        return True

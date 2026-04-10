import json
import os
import time
from abc import ABC, abstractmethod
from datetime import datetime, timedelta

from core.models import Command, CommandResult
from utils.logger import Logger


class BaseAction(ABC):
    """Abstract base class for all actions, mirroring BaseAction.cs."""

    def __init__(self):
        self.logger: Logger | None = None

    def set_logger(self, logger: Logger) -> None:
        self.logger = logger

    @abstractmethod
    def execute(self, command: Command, context: dict) -> CommandResult:
        """Execute the action. 'context' holds shared state (proxy_manager, traffic_store, etc.)."""
        ...

    def validate_command(self, command: Command) -> bool:
        """Validate that the command has required fields. Override in subclasses."""
        return bool(command.action)

    def create_result(self, command: Command) -> CommandResult:
        """Create a CommandResult pre-populated with command identity."""
        return CommandResult(
            command_id=command.id or "unknown",
            action=command.action,
        )

    def log_execution(self, command: Command) -> None:
        """Log the start of execution to the file log."""
        desc = command.description or command.id or command.action
        if self.logger:
            self.logger.log_to_file(f"EXECUTING: {command.action} - {desc}")

    def handle_success(self, command: Command, result: CommandResult,
                       remarks: str = "") -> None:
        """Mark result as success and emit OutputRemark."""
        result.success = True
        result.message = f"{command.action} completed successfully"
        remark_text = remarks or command.description or result.message
        Logger.output_remark(command.id or "unknown", True, remark_text)
        if self.logger:
            self.logger.log_to_file(f"SUCCESS: {command.id} - {remark_text}")

    def handle_failure(self, command: Command, result: CommandResult,
                       error: str) -> None:
        """Mark result as failure and emit OutputRemark."""
        result.success = False
        result.error_message = error
        result.message = f"Command failed: {error}"
        Logger.output_remark(command.id or "unknown", False, f"Failed: {error}")
        if self.logger:
            self.logger.log_to_file(f"FAILED: {command.id} - {error}")

    def _ensure_traffic_store(self, command: Command, context: dict):
        """Ensure a traffic store exists in context.

        If no store exists but the command specifies a ``capture_file``,
        load traffic from that file automatically so validation actions
        can work independently without a prior ``wait_for_request``.

        Returns the TrafficStore instance, or *None* if unavailable.
        """
        store = context.get("traffic_store")
        if store:
            return store

        capture_file = getattr(command, "capture_file", None) or ""
        if not capture_file:
            return None

        if not os.path.exists(capture_file):
            if self.logger:
                self.logger.log_to_file(
                    f"capture_file '{capture_file}' not found – cannot auto-load traffic")
            return None

        try:
            with open(capture_file, "r", encoding="utf-8") as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError) as exc:
            if self.logger:
                self.logger.log_to_file(f"Failed to read capture file: {exc}")
            return None

        if not isinstance(data, list):
            return None

        from proxy.traffic_store import TrafficStore, CapturedFlow

        store = TrafficStore()
        for entry in data:
            try:
                req = entry.get("request", {})
                resp = entry.get("response", {})
                flow = CapturedFlow(
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
                )
                store.add_flow(flow)
            except Exception:
                pass

        context["traffic_store"] = store
        if self.logger:
            self.logger.log_to_file(
                f"Auto-loaded {store.count()} flows from '{capture_file}'")
        return store

    def run(self, command: Command, context: dict) -> CommandResult:
        """Template method: validate, time, execute, handle errors."""
        result = self.create_result(command)
        if not self.validate_command(command):
            self.handle_failure(command, result, f"Validation failed for action '{command.action}'")
            return result

        self.log_execution(command)
        start = time.perf_counter()
        try:
            result = self.execute(command, context)
        except Exception as ex:
            self.handle_failure(command, result, str(ex))
        finally:
            elapsed = time.perf_counter() - start
            result.duration = timedelta(seconds=elapsed)
        return result

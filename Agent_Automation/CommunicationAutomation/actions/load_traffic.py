import json
import os
from datetime import datetime

from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.traffic_store import TrafficStore, CapturedFlow


class LoadTrafficAction(BaseAction):
    """Load previously captured traffic from a JSON file into the traffic store.

    This allows validation actions to run in a separate execution from the
    capture session. The capture file format matches what capture_traffic produces.

    If no traffic_store exists in the context (i.e. proxy not running), one is
    created automatically so validations can still work.
    """

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.capture_file)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        capture_file = command.capture_file
        if not os.path.exists(capture_file):
            self.handle_failure(command, result,
                                f"[load_traffic] File: '{capture_file}' | Error: File not found | Result: FAILED")
            return result

        # Read captured traffic JSON
        try:
            with open(capture_file, "r", encoding="utf-8") as f:
                data = json.load(f)
        except json.JSONDecodeError as e:
            self.handle_failure(command, result,
                                f"[load_traffic] File: '{capture_file}' | Error: Invalid JSON - {e} | Result: FAILED")
            return result
        except OSError as e:
            self.handle_failure(command, result,
                                f"[load_traffic] File: '{capture_file}' | Error: Read failed - {e} | Result: FAILED")
            return result

        if not isinstance(data, list):
            self.handle_failure(command, result,
                                f"[load_traffic] File: '{capture_file}' | "
                                "Error: File must contain a JSON array of flows | Result: FAILED")
            return result

        # Ensure a traffic store exists in context (create one if proxy not running)
        store = context.get("traffic_store")
        if not store:
            store = TrafficStore()
            context["traffic_store"] = store

        # Parse each flow and add to the traffic store
        loaded = 0
        for entry in data:
            try:
                req = entry.get("request", {})
                resp = entry.get("response", {})

                flow = CapturedFlow(
                    timestamp=datetime.fromisoformat(entry.get("timestamp", datetime.now().isoformat())),
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
                loaded += 1
            except Exception as e:
                if self.logger:
                    self.logger.log_to_file(f"Skipped malformed flow entry: {e}")

        result.data = {"file": capture_file, "loaded": loaded, "total": len(data)}
        self.handle_success(
            command, result,
            f"[load_traffic] File: '{capture_file}' | "
            f"Loaded: {loaded}/{len(data)} flows | Result: PASSED"
        )
        return result

import json
import os

from core.base_action import BaseAction
from core.models import Command, CommandResult


class CaptureTrafficAction(BaseAction):
    """Dump captured traffic to a JSON file."""

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        store = context.get("traffic_store")
        if not store:
            self.handle_failure(command, result,
                                "[capture_traffic] Error: No traffic store - proxy not started? | Result: FAILED")
            return result

        flows = store.get_all_flows()
        capture_file = command.capture_file or "Reports/traffic-capture.json"

        # Ensure directory exists
        os.makedirs(os.path.dirname(capture_file) or ".", exist_ok=True)

        data = [f.to_dict() for f in flows]

        try:
            with open(capture_file, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except OSError as e:
            self.handle_failure(command, result,
                                f"[capture_traffic] File: '{capture_file}' | "
                                f"Error: Failed to write - {e} | Result: FAILED")
            return result

        result.data = capture_file
        self.handle_success(
            command, result,
            f"[capture_traffic] File: '{capture_file}' | "
            f"Flows captured: {len(flows)} | Result: PASSED"
        )
        return result

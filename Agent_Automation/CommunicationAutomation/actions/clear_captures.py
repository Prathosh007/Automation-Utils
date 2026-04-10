import json
import os

from core.base_action import BaseAction
from core.models import Command, CommandResult


class ClearCapturesAction(BaseAction):
    """Reset the captured traffic buffer and optionally the capture file."""

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        remarks = []

        # Clear in-memory traffic store
        store = context.get("traffic_store")
        if store:
            count = store.clear()
            remarks.append(f"Cleared {count} flows from memory")
        else:
            remarks.append("No traffic store yet - nothing to clear in memory")

        # Clear the capture file on disk if specified
        capture_file = command.capture_file
        if capture_file and os.path.exists(capture_file):
            try:
                with open(capture_file, "w", encoding="utf-8") as f:
                    json.dump([], f)
                remarks.append(f"Cleared capture file: {capture_file}")
            except Exception as e:
                self.handle_failure(command, result,
                                    f"[clear_captures] Error: Failed to clear file '{capture_file}' - {e} | Result: FAILED")
                return result
        elif capture_file:
            remarks.append(f"Capture file not found (skipped): {capture_file}")

        self.handle_success(command, result,
                            f"[clear_captures] {'; '.join(remarks)} | Result: PASSED")
        return result

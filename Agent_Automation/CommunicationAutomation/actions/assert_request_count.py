from core.base_action import BaseAction
from core.models import Command, CommandResult


class AssertRequestCountAction(BaseAction):
    """Verify count of captured requests matching a pattern."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        store = self._ensure_traffic_store(command, context)
        if not store:
            self.handle_failure(command, result,
                                "No traffic store - proxy not started? "
                                "Provide 'captureFile' to load traffic from a capture file.")
            return result

        actual_count = store.count(url_pattern=command.url_pattern, method=command.method)
        result.data = actual_count

        pattern = command.url_pattern
        checks_applied = []

        # Check exact count
        if command.expected_count >= 0:
            checks_applied.append(f"exactly {command.expected_count}")
            if actual_count != command.expected_count:
                self.handle_failure(
                    command, result,
                    f"[assert_request_count] Pattern: '{pattern}' | "
                    f"Expected: exactly {command.expected_count} | "
                    f"Got: {actual_count} | Result: FAILED"
                )
                return result

        # Check min count
        if command.min_count > 0:
            checks_applied.append(f"at least {command.min_count}")
            if actual_count < command.min_count:
                self.handle_failure(
                    command, result,
                    f"[assert_request_count] Pattern: '{pattern}' | "
                    f"Expected: at least {command.min_count} | "
                    f"Got: {actual_count} | Result: FAILED (too few requests)"
                )
                return result

        # Check max count
        if command.max_count > 0:
            checks_applied.append(f"at most {command.max_count}")
            if actual_count > command.max_count:
                self.handle_failure(
                    command, result,
                    f"[assert_request_count] Pattern: '{pattern}' | "
                    f"Expected: at most {command.max_count} | "
                    f"Got: {actual_count} | Result: FAILED (too many requests)"
                )
                return result

        criteria = ", ".join(checks_applied) if checks_applied else "any count"
        self.handle_success(
            command, result,
            f"[assert_request_count] Pattern: '{pattern}' | "
            f"Expected: {criteria} | "
            f"Got: {actual_count} | Result: PASSED"
        )
        return result

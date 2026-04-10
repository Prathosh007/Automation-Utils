from core.base_action import BaseAction
from core.models import Command, CommandResult
from utils.pattern_matcher import validate_value


class ValidateResponseAction(BaseAction):
    """Assert response properties (status, headers, body)."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        pattern = command.url_pattern

        store = self._ensure_traffic_store(command, context)
        if not store:
            self.handle_failure(command, result,
                                f"[validate_response] Pattern: '{pattern}' | "
                                "Error: No traffic store - proxy not started? "
                                "Provide 'captureFile' to load traffic from a capture file. | Result: FAILED")
            return result

        flow = store.get_latest_flow(url_pattern=pattern, method=command.method)
        if not flow:
            self.handle_failure(
                command, result,
                f"[validate_response] Pattern: '{pattern}' | "
                f"Error: No captured response found | Result: FAILED"
            )
            return result

        # Validate status code
        if command.expected_status:
            if flow.response_status != command.expected_status:
                self.handle_failure(
                    command, result,
                    f"[validate_response] Pattern: '{pattern}' | Status | "
                    f"Expected: {command.expected_status} | Got: {flow.response_status} | Result: FAILED"
                )
                return result

        # Validate response header
        if command.header_name and command.expected_value:
            actual = flow.response_headers.get(command.header_name, "")
            ok, msg = validate_value(
                actual, command.expected_value,
                command.validation_type, command.case_sensitive
            )
            label = f"[validate_response] Pattern: '{pattern}' | Header: '{command.header_name}'"
            if not ok:
                self.handle_failure(command, result, f"{label} | {msg} | Result: FAILED")
                return result
            self.handle_success(command, result, f"{label} | {msg} | Result: PASSED")
            return result

        # Validate response body
        if command.body_pattern or command.expected_value:
            body_pattern = command.body_pattern or command.expected_value
            ok, msg = validate_value(
                flow.response_body, body_pattern,
                command.validation_type, command.case_sensitive
            )
            label = f"[validate_response] Pattern: '{pattern}' | Body"
            if not ok:
                self.handle_failure(command, result, f"{label} | {msg} | Result: FAILED")
                return result
            self.handle_success(command, result, f"{label} | {msg} | Result: PASSED")
            return result

        # Status-only or existence check
        self.handle_success(
            command, result,
            f"[validate_response] Pattern: '{pattern}' | "
            f"Status: {flow.response_status} | URL: {flow.request_url} | Result: PASSED"
        )
        return result

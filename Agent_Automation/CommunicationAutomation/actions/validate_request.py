from core.base_action import BaseAction
from core.models import Command, CommandResult
from utils.pattern_matcher import validate_value


class ValidateRequestAction(BaseAction):
    """Assert request properties (URL, headers, body, method)."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        pattern = command.url_pattern

        store = self._ensure_traffic_store(command, context)
        if not store:
            self.handle_failure(command, result,
                                f"[validate_request] Pattern: '{pattern}' | "
                                "Error: No traffic store - proxy not started? "
                                "Provide 'captureFile' to load traffic from a capture file. | Result: FAILED")
            return result

        flow = store.get_latest_flow(url_pattern=pattern, method=command.method)
        if not flow:
            self.handle_failure(
                command, result,
                f"[validate_request] Pattern: '{pattern}' | "
                f"Error: No captured request found | Result: FAILED"
            )
            return result

        # Validate based on what's specified
        if command.header_name and command.expected_value:
            actual = flow.request_headers.get(command.header_name, "")
            ok, msg = validate_value(
                actual, command.expected_value,
                command.validation_type, command.case_sensitive
            )
            label = f"[validate_request] Pattern: '{pattern}' | Header: '{command.header_name}'"
            if not ok:
                self.handle_failure(command, result, f"{label} | {msg} | Result: FAILED")
                return result
            self.handle_success(command, result, f"{label} | {msg} | Result: PASSED")
            return result

        if command.body_pattern:
            vtype = command.validation_type if command.validation_type else "contains"
            ok, msg = validate_value(
                flow.request_body, command.body_pattern,
                vtype, command.case_sensitive
            )
            label = f"[validate_request] Pattern: '{pattern}' | Body"
            if not ok:
                self.handle_failure(command, result, f"{label} | {msg} | Result: FAILED")
                return result
            self.handle_success(command, result, f"{label} | {msg} | Result: PASSED")
            return result

        if command.expected_value:
            ok, msg = validate_value(
                flow.request_url, command.expected_value,
                command.validation_type, command.case_sensitive
            )
            label = f"[validate_request] Pattern: '{pattern}' | URL"
            if not ok:
                self.handle_failure(command, result, f"{label} | {msg} | Result: FAILED")
                return result
            self.handle_success(command, result, f"{label} | {msg} | Result: PASSED")
            return result

        # If no specific validation, just confirm request exists
        self.handle_success(
            command, result,
            f"[validate_request] Pattern: '{pattern}' | "
            f"Request found: {flow.request_method} {flow.request_url} | Result: PASSED"
        )
        return result

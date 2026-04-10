from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.response_rules import ResponseRule


class ModifyResponseAction(BaseAction):
    """Inject/alter response for matching requests (one-shot or persistent)."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        rule_engine = context.get("rule_engine")
        if not rule_engine:
            self.handle_failure(command, result,
                                "[modify_response] Error: No rule engine - proxy not started? | Result: FAILED")
            return result

        rule = ResponseRule(
            url_pattern=command.url_pattern,
            method=command.method,
            inject_status=command.inject_status,
            inject_body=command.inject_body,
            inject_headers=command.inject_headers,
            body_replacements=command.body_replacements or {},
            persistent=command.persistent,
        )
        rule_engine.add_rule(rule)

        mode = "persistent" if command.persistent else "one-shot"
        status_desc = command.inject_status or "unchanged"
        self.handle_success(
            command, result,
            f"[modify_response] Pattern: '{command.url_pattern}' | "
            f"Mode: {mode} | Inject status: {status_desc} | Result: PASSED"
        )
        return result

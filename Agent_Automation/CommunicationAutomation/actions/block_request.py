from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.response_rules import ResponseRule


class BlockRequestAction(BaseAction):
    """Block or delay matching requests."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.url_pattern)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        rule_engine = context.get("rule_engine")
        if not rule_engine:
            self.handle_failure(command, result,
                                "[block_request] Error: No rule engine - proxy not started? | Result: FAILED")
            return result

        rule = ResponseRule(
            url_pattern=command.url_pattern,
            method=command.method,
            block=(command.block_action == "block"),
            delay_ms=command.delay_ms,
            persistent=command.persistent,
        )
        rule_engine.add_rule(rule)

        if command.block_action == "block":
            desc = (f"[block_request] Pattern: '{command.url_pattern}' | "
                    f"Action: block | Mode: {'persistent' if command.persistent else 'one-shot'} | Result: PASSED")
        else:
            desc = (f"[block_request] Pattern: '{command.url_pattern}' | "
                    f"Action: delay {command.delay_ms}ms | Mode: {'persistent' if command.persistent else 'one-shot'} | Result: PASSED")

        self.handle_success(command, result, desc)
        return result

from core.base_action import BaseAction
from core.models import Command, CommandResult
from utils.pattern_matcher import matches_pattern


class AssertRequestOrderAction(BaseAction):
    """Verify that captured requests arrived in a specific order."""

    def validate_command(self, command: Command) -> bool:
        return (super().validate_command(command)
                and isinstance(command.expected_order, list)
                and len(command.expected_order) >= 2)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        store = self._ensure_traffic_store(command, context)
        if not store:
            self.handle_failure(command, result,
                                "No traffic store - proxy not started? "
                                "Provide 'captureFile' to load traffic from a capture file.")
            return result

        all_flows = store.get_all_flows()
        if not all_flows:
            self.handle_failure(
                command, result,
                "[assert_request_order] Error: No captured traffic to verify order | Result: FAILED"
            )
            return result

        expected_str = " -> ".join(command.expected_order)

        # For each pattern in expected_order, find the index of its first occurrence
        pattern_indices = []
        for pattern in command.expected_order:
            found_idx = None
            for i, flow in enumerate(all_flows):
                if matches_pattern(flow.request_url, pattern):
                    found_idx = i
                    break
            if found_idx is None:
                self.handle_failure(
                    command, result,
                    f"[assert_request_order] Expected order: {expected_str} | "
                    f"Error: Pattern '{pattern}' not found in captured traffic | Result: FAILED"
                )
                return result
            pattern_indices.append((pattern, found_idx))

        # Verify indices are in ascending order
        for i in range(1, len(pattern_indices)):
            prev_pattern, prev_idx = pattern_indices[i - 1]
            curr_pattern, curr_idx = pattern_indices[i]
            if curr_idx <= prev_idx:
                actual_str = " -> ".join(p for p, _ in sorted(pattern_indices, key=lambda x: x[1]))
                self.handle_failure(
                    command, result,
                    f"[assert_request_order] Expected order: {expected_str} | "
                    f"Got order: {actual_str} | "
                    f"Violation: '{curr_pattern}' (pos {curr_idx}) appeared before "
                    f"'{prev_pattern}' (pos {prev_idx}) | Result: FAILED"
                )
                return result

        order_str = " -> ".join(p for p, _ in pattern_indices)
        self.handle_success(
            command, result,
            f"[assert_request_order] Expected order: {expected_str} | "
            f"Got order: {order_str} | Result: PASSED"
        )
        return result

import json
import os
import time
import uuid
from datetime import datetime

from core.action_registry import ActionRegistry
from core.models import Command, CommandResult, TestResult
from utils.logger import Logger
from utils.variable_context import VariableContext


class TestEngine:
    """Loads JSON test files, iterates commands, and outputs GOAT-compatible results."""

    def __init__(self, log_dir: str = "Logs"):
        self._logger = Logger(log_dir)
        self._registry = ActionRegistry()
        self._variable_context = VariableContext()
        self._context: dict = {}  # Shared state: proxy_manager, traffic_store, etc.

    def execute_test_suite(self, json_path: str) -> TestResult:
        """Load and execute a JSON test file. Returns a TestResult."""
        test_result = TestResult(
            test_id=str(uuid.uuid4()),
            test_name=os.path.basename(json_path),
            start_time=datetime.now(),
        )

        # Load JSON
        try:
            with open(json_path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except FileNotFoundError:
            Logger.output_file_not_found(json_path)
            test_result.success = False
            test_result.message = f"File not found: {json_path}"
            test_result.end_time = datetime.now()
            return test_result
        except json.JSONDecodeError as e:
            Logger.output_critical_error(f"Invalid JSON: {e}")
            test_result.success = False
            test_result.message = f"Invalid JSON: {e}"
            test_result.end_time = datetime.now()
            return test_result

        # Parse commands - support flat array or wrapped { "commands": [...] }
        if isinstance(data, list):
            raw_commands = data
        elif isinstance(data, dict) and "commands" in data:
            raw_commands = data["commands"]
        else:
            Logger.output_critical_error("JSON must be an array of commands or an object with 'commands' key")
            test_result.success = False
            test_result.message = "Invalid JSON structure"
            test_result.end_time = datetime.now()
            return test_result

        self._logger.log_to_file(f"Loaded {len(raw_commands)} commands from {json_path}")

        # Execute commands
        for i, raw_cmd in enumerate(raw_commands):
            # Variable substitution
            processed = self._variable_context.substitute_command(raw_cmd)
            command = Command.from_dict(processed)

            self._logger.log_to_file(
                f"Command [{i + 1}/{len(raw_commands)}]: {command.id} - {command.action}"
            )

            # Dispatch
            try:
                action = self._registry.get_action(command.action)
                action.set_logger(self._logger)
                cmd_result = action.run(command, self._context)
            except NotImplementedError as e:
                cmd_result = CommandResult(
                    command_id=command.id or "unknown",
                    action=command.action,
                    success=False,
                    error_message=str(e),
                    message=str(e),
                )
                Logger.output_remark(command.id or "unknown", False, str(e))
            except Exception as e:
                cmd_result = CommandResult(
                    command_id=command.id or "unknown",
                    action=command.action,
                    success=False,
                    error_message=str(e),
                    message=f"Critical error: {e}",
                )
                Logger.output_remark(command.id or "unknown", False, f"Critical error: {e}")
                self._logger.log_to_file(f"CRITICAL: {command.id} - {e}")

            test_result.command_results.append(cmd_result)

            # Store result for variable substitution
            if command.id:
                self._variable_context.store_command_result(command.id, cmd_result)

            # Break on failure unless continue_on_failure
            if not cmd_result.success and not command.continue_on_failure:
                self._logger.log_to_file(
                    f"Stopping execution: {command.id} failed and continueOnFailure=false"
                )
                break

            # Small delay between commands
            time.sleep(0.3)

        # Summary
        total = test_result.total_commands
        passed = test_result.passed_commands
        failed = test_result.failed_commands
        test_result.success = failed == 0
        test_result.message = f"Total:{total} Passed:{passed} Failed:{failed}"
        test_result.end_time = datetime.now()

        Logger.output_summary(total, passed, failed)
        self._logger.log_to_file(f"Test complete: {test_result.message}")

        return test_result

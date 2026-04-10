import subprocess

from core.base_action import BaseAction
from core.models import Command, CommandResult


class RunCommandAction(BaseAction):
    """Run an arbitrary command/executable and capture its output."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.command_line)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        timeout = command.timeout or 60

        if self.logger:
            self.logger.log_to_file(f"Running command: {command.command_line}")
            if command.working_dir:
                self.logger.log_to_file(f"Working dir: {command.working_dir}")

        try:
            r = subprocess.run(
                command.command_line,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=command.working_dir or None,
            )
            output = (r.stdout + r.stderr).strip()

            if self.logger:
                self.logger.log_to_file(f"Exit code: {r.returncode}")
                self.logger.log_to_file(f"Output: {output[:500]}")

            result.data = {"exit_code": r.returncode, "output": output}

            if r.returncode == 0:
                self.handle_success(command, result,
                                    f"[run_command] Command: '{command.command_line}' | "
                                    f"Exit code: 0 | Output: {output[:200]} | Result: PASSED")
            else:
                self.handle_failure(command, result,
                                    f"[run_command] Command: '{command.command_line}' | "
                                    f"Expected exit code: 0 | Got: {r.returncode} | "
                                    f"Output: {output[:200]} | Result: FAILED")
        except subprocess.TimeoutExpired:
            self.handle_failure(command, result,
                                f"[run_command] Command: '{command.command_line}' | "
                                f"Error: Timed out after {timeout}s | Result: FAILED")
        except Exception as e:
            self.handle_failure(command, result,
                                f"[run_command] Command: '{command.command_line}' | "
                                f"Error: {e} | Result: FAILED")

        return result

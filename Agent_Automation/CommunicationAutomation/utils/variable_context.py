import re
from typing import Any, Optional

from core.models import CommandResult


class VariableContext:
    """Variable storage and {{stepId.property}} substitution, mirroring VariableContext.cs."""

    def __init__(self):
        self._variables: dict[str, Any] = {}

    def store_command_result(self, step_id: str, result: CommandResult) -> None:
        """Store a command result and its properties as variables."""
        if not step_id:
            return
        self._variables[step_id] = result
        self._variables[f"{step_id}.data"] = result.data
        self._variables[f"{step_id}.success"] = str(result.success)
        self._variables[f"{step_id}.message"] = result.message
        self._variables[f"{step_id}.duration"] = str(result.duration)
        self._variables[f"{step_id}.commandid"] = result.command_id
        self._variables[f"{step_id}.action"] = result.action
        if result.error_message:
            self._variables[f"{step_id}.error"] = result.error_message

    def set_variable(self, name: str, value: Any) -> None:
        """Set a named variable."""
        self._variables[name] = value

    def get_variable(self, name: str) -> Optional[Any]:
        """Get a variable by name."""
        return self._variables.get(name)

    def substitute(self, text: str) -> str:
        """Replace all {{expression}} placeholders in the given text."""
        if not text or "{{" not in text:
            return text

        def _resolve(match: re.Match) -> str:
            expr = match.group(1).strip()
            value = self._variables.get(expr)
            if value is not None:
                return str(value)
            # Try case-insensitive lookup
            expr_lower = expr.lower()
            for key, val in self._variables.items():
                if key.lower() == expr_lower:
                    return str(val)
            return match.group(0)  # Leave unresolved

        return re.sub(r"\{\{([^}]+)\}\}", _resolve, text)

    def substitute_command(self, command_dict: dict) -> dict:
        """Apply variable substitution to all string fields in a command dict."""
        result = {}
        for key, value in command_dict.items():
            if isinstance(value, str):
                result[key] = self.substitute(value)
            else:
                result[key] = value
        return result

"""Unit tests for run_command action (mocked — no real commands executed)."""

import pytest
from unittest.mock import patch, MagicMock
import subprocess
from tests.conftest import make_command
from actions.run_command import RunCommandAction


@pytest.fixture
def action(logger):
    a = RunCommandAction()
    a.set_logger(logger)
    return a


class TestRunCommandSuccess:
    """Test successful command execution."""

    @patch("subprocess.run")
    def test_exit_zero(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stdout="hello world", stderr="")
        cmd = make_command(action="run_command", commandLine="echo hello world")
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert result.data["exit_code"] == 0
        assert "hello world" in result.data["output"]

    @patch("subprocess.run")
    def test_with_working_dir(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stdout="ok", stderr="")
        cmd = make_command(
            action="run_command",
            commandLine="dir",
            workingDir="C:\\Windows",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        mock_run.assert_called_once()
        assert mock_run.call_args.kwargs.get("cwd") == "C:\\Windows"


class TestRunCommandFailure:
    """Test command failures."""

    @patch("subprocess.run")
    def test_nonzero_exit(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=1, stdout="", stderr="error occurred")
        cmd = make_command(action="run_command", commandLine="failing_cmd")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert result.data["exit_code"] == 1

    @patch("subprocess.run", side_effect=subprocess.TimeoutExpired(cmd="test", timeout=5))
    def test_timeout(self, mock_run, action, empty_context):
        cmd = make_command(action="run_command", commandLine="long_cmd", timeout=5)
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "timed out" in result.error_message.lower()

    @patch("subprocess.run", side_effect=OSError("Command not found"))
    def test_os_error(self, mock_run, action, empty_context):
        cmd = make_command(action="run_command", commandLine="badcmd")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "error" in result.error_message.lower()


class TestRunCommandValidation:
    """Test validate_command."""

    def test_missing_command_line(self, action):
        cmd = make_command(action="run_command")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="run_command", commandLine="echo test")
        assert action.validate_command(cmd) is True

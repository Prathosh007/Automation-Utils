"""Unit tests for start_proxy action (mocked — no real proxy started)."""

import os
import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.start_proxy import StartProxyAction


@pytest.fixture
def action(logger):
    a = StartProxyAction()
    a.set_logger(logger)
    return a


class TestStartProxyValidation:
    """Test validate_command."""

    def test_valid_command(self, action):
        cmd = make_command(action="start_proxy", listenPort=8080)
        assert action.validate_command(cmd) is True

    def test_invalid_port_zero(self, action):
        cmd = make_command(action="start_proxy", listenPort=0)
        assert action.validate_command(cmd) is False


class TestStartProxyExistingPID:
    """Test when a proxy is already running."""

    @pytest.mark.skipif(True, reason="os.kill(os.getpid(), 0) causes test interaction issues on Windows")
    def test_already_running_pid(self, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with open(pid_file, "w") as f:
            f.write(str(os.getpid()))  # Current process PID = alive

        with patch.object(StartProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="start_proxy", listenPort=8080)
            result = action.execute(cmd, empty_context)
            assert result.success is False
            assert "already running" in result.message.lower() or "already running" in result.error_message.lower()

    def test_stale_pid_cleaned(self, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with open(pid_file, "w") as f:
            f.write("99999999")  # Very unlikely alive

        with patch.object(StartProxyAction, "PID_FILE", pid_file):
            with patch("subprocess.Popen") as mock_popen:
                with patch("time.sleep"):
                    proc_mock = MagicMock()
                    proc_mock.pid = 12345
                    proc_mock.poll.return_value = None  # Still running
                    mock_popen.return_value = proc_mock

                    cmd = make_command(action="start_proxy", listenPort=8080)
                    result = action.execute(cmd, empty_context)
                    # Should try to start (stale PID file removed)
                    assert mock_popen.called or not os.path.exists(pid_file)


class TestStartProxySpawn:
    """Test process spawning."""

    @patch("time.sleep")
    @patch("subprocess.Popen")
    def test_spawn_success(self, mock_popen, mock_sleep, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")

        proc_mock = MagicMock()
        proc_mock.pid = 54321
        proc_mock.poll.return_value = None
        proc_mock.returncode = None
        mock_popen.return_value = proc_mock

        with patch.object(StartProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="start_proxy", listenPort=8080)
            result = action.execute(cmd, empty_context)
            assert result.success is True
            assert empty_context.get("background_proxy_pid") == 54321

    @patch("time.sleep")
    @patch("subprocess.Popen")
    def test_spawn_immediate_exit(self, mock_popen, mock_sleep, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")

        proc_mock = MagicMock()
        proc_mock.pid = 54321
        proc_mock.poll.return_value = 1  # exited immediately
        proc_mock.returncode = 1
        mock_popen.return_value = proc_mock

        with patch.object(StartProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="start_proxy", listenPort=8080)
            result = action.execute(cmd, empty_context)
            assert result.success is False
            assert "exited immediately" in result.error_message.lower()

    @patch("time.sleep")
    @patch("subprocess.Popen", side_effect=OSError("Cannot start"))
    def test_spawn_exception(self, mock_popen, mock_sleep, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with patch.object(StartProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="start_proxy", listenPort=8080)
            result = action.execute(cmd, empty_context)
            assert result.success is False
            assert "Failed to spawn" in result.error_message

"""Unit tests for stop_proxy action (mocked — no real processes killed)."""

import os
import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.stop_proxy import StopProxyAction


@pytest.fixture
def action(logger):
    a = StopProxyAction()
    a.set_logger(logger)
    return a


class TestStopProxyNotRunning:
    """Test when proxy is not running."""

    def test_nothing_to_stop(self, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with patch.object(StopProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="stop_proxy")
            result = action.execute(cmd, empty_context)
            assert result.success is True


class TestStopProxyInProcess:
    """Test stopping an in-process proxy."""

    def test_stop_in_process_proxy(self, action):
        pm = MagicMock()
        pm.is_running = True
        ctx = {"proxy_manager": pm}
        cmd = make_command(action="stop_proxy")
        result = action.execute(cmd, ctx)
        assert result.success is True
        pm.stop.assert_called_once()


class TestStopProxyBackground:
    """Test stopping a background proxy via PID file."""

    @patch.object(StopProxyAction, "_is_alive", return_value=False)
    def test_already_dead(self, mock_alive, action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with open(pid_file, "w") as f:
            f.write("99999")

        with patch.object(StopProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="stop_proxy")
            result = action.execute(cmd, empty_context)
            assert result.success is True

    @patch.object(StopProxyAction, "_kill_orphan_serve_processes", return_value=0)
    @patch.object(StopProxyAction, "_is_alive")
    @patch.object(StopProxyAction, "_taskkill", return_value=True)
    def test_killed_by_taskkill(self, mock_taskkill, mock_alive, mock_orphan,
                                 action, empty_context, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        with open(pid_file, "w") as f:
            f.write("12345")

        # First call: alive, second call (after taskkill): dead
        mock_alive.side_effect = [True, False]

        with patch.object(StopProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="stop_proxy")
            result = action.execute(cmd, empty_context)
            assert result.success is True
            mock_taskkill.assert_called()


class TestStopProxyContext:
    """Test stopping via context PID."""

    @patch.object(StopProxyAction, "_kill_orphan_serve_processes", return_value=0)
    @patch.object(StopProxyAction, "_is_alive", return_value=False)
    def test_from_context_pid(self, mock_alive, mock_orphan, action, tmp_path):
        pid_file = str(tmp_path / "proxy.pid")
        ctx = {"background_proxy_pid": 67890}

        with patch.object(StopProxyAction, "PID_FILE", pid_file):
            cmd = make_command(action="stop_proxy")
            result = action.execute(cmd, ctx)
            assert result.success is True


class TestStopProxyHelpers:
    """Test helper methods."""

    @patch("subprocess.run")
    def test_taskkill_tree(self, mock_run, action):
        mock_run.return_value = MagicMock(returncode=0)
        result = StopProxyAction._taskkill(12345, tree=True)
        assert result is True
        args = mock_run.call_args[0][0]
        assert "/T" in args
        assert "/F" in args

    @patch("subprocess.run")
    def test_taskkill_no_tree(self, mock_run, action):
        mock_run.return_value = MagicMock(returncode=0)
        result = StopProxyAction._taskkill(12345, tree=False)
        assert result is True
        args = mock_run.call_args[0][0]
        assert "/T" not in args

    @patch("subprocess.run")
    def test_is_alive_true(self, mock_run):
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="Image Name    PID\nmyapp.exe     12345",
        )
        assert StopProxyAction._is_alive(12345) is True

    @patch("subprocess.run")
    def test_is_alive_false(self, mock_run):
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="INFO: No tasks are running which match the specified criteria.",
        )
        assert StopProxyAction._is_alive(12345) is False

    @patch("subprocess.run")
    def test_wmic_terminate(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        result = StopProxyAction._wmic_terminate(12345)
        assert result is True

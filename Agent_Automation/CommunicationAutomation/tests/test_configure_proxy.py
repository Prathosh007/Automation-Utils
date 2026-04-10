"""Unit tests for configure_proxy action (mocked — no real netsh calls)."""

import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.configure_proxy import ConfigureProxyAction


@pytest.fixture
def action(logger):
    a = ConfigureProxyAction()
    a.set_logger(logger)
    return a


class TestConfigureProxyEnable:
    """Test enabling system proxy."""

    @patch("subprocess.run")
    def test_enable_success(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stderr="")
        cmd = make_command(
            action="configure_proxy",
            proxyAction="enable",
            proxyHost="127.0.0.1",
            proxyPort=8080,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("subprocess.run")
    def test_enable_with_bypass(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stderr="")
        cmd = make_command(
            action="configure_proxy",
            proxyAction="enable",
            proxyHost="127.0.0.1",
            proxyPort=8080,
            bypassList="localhost;*.local",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        args = mock_run.call_args[0][0]
        assert "bypass-list" in args

    @patch("subprocess.run")
    def test_enable_failure(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=1, stderr="Access denied")
        cmd = make_command(
            action="configure_proxy",
            proxyAction="enable",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False


class TestConfigureProxyDisable:
    """Test disabling system proxy."""

    @patch("subprocess.run")
    def test_disable_success(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stderr="")
        cmd = make_command(action="configure_proxy", proxyAction="disable")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("subprocess.run")
    def test_disable_failure(self, mock_run, action, empty_context):
        mock_run.return_value = MagicMock(returncode=1, stderr="error")
        cmd = make_command(action="configure_proxy", proxyAction="disable")
        result = action.execute(cmd, empty_context)
        assert result.success is False


class TestConfigureProxyValidation:
    """Test validate_command."""

    def test_valid_enable(self, action):
        cmd = make_command(action="configure_proxy", proxyAction="enable")
        assert action.validate_command(cmd) is True

    def test_valid_disable(self, action):
        cmd = make_command(action="configure_proxy", proxyAction="disable")
        assert action.validate_command(cmd) is True

    def test_invalid_action(self, action):
        cmd = make_command(action="configure_proxy", proxyAction="toggle")
        assert action.validate_command(cmd) is False

    def test_missing_action(self, action):
        cmd = make_command(action="configure_proxy")
        assert action.validate_command(cmd) is False

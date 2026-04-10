"""Unit tests for install_certificate action (mocked — no real certutil calls)."""

import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.install_certificate import InstallCertificateAction


@pytest.fixture
def action(logger):
    a = InstallCertificateAction()
    a.set_logger(logger)
    return a


class TestInstallCertificate:
    """Test installing CA certificate."""

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(True, "Certificate installed successfully"))
    def test_install_success(self, mock_install, action, empty_context):
        cmd = make_command(action="install_certificate", certAction="install")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(False, "certutil failed: Access denied"))
    def test_install_failure(self, mock_install, action, empty_context):
        cmd = make_command(action="install_certificate", certAction="install")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "failed" in result.error_message.lower() or "denied" in result.error_message.lower()

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(True, "Certificate installed successfully"))
    def test_install_custom_path(self, mock_install, action, empty_context):
        cmd = make_command(
            action="install_certificate",
            certAction="install",
            certPath="C:\\custom\\cert.cer",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        mock_install.assert_called_once_with("C:\\custom\\cert.cer")


class TestRemoveCertificate:
    """Test removing CA certificate."""

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(True, "Certificate removed successfully"))
    def test_remove_success(self, mock_remove, action, empty_context):
        cmd = make_command(action="install_certificate", certAction="remove")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(False, "Certificate not found"))
    def test_remove_failure(self, mock_remove, action, empty_context):
        cmd = make_command(action="install_certificate", certAction="remove")
        result = action.execute(cmd, empty_context)
        assert result.success is False


class TestInstallCertificateValidation:
    """Test validate_command."""

    def test_valid_install(self, action):
        cmd = make_command(action="install_certificate", certAction="install")
        assert action.validate_command(cmd) is True

    def test_valid_remove(self, action):
        cmd = make_command(action="install_certificate", certAction="remove")
        assert action.validate_command(cmd) is True

    def test_invalid_action(self, action):
        cmd = make_command(action="install_certificate", certAction="update")
        assert action.validate_command(cmd) is False

    def test_missing_action(self, action):
        cmd = make_command(action="install_certificate")
        assert action.validate_command(cmd) is False

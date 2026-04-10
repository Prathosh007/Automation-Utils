"""Unit tests for install_cert action (mocked — no real certutil calls)."""

import pytest
from unittest.mock import patch
from tests.conftest import make_command
from actions.install_cert import InstallCertAction


@pytest.fixture
def action(logger):
    a = InstallCertAction()
    a.set_logger(logger)
    return a


class TestInstallCert:
    """Test installing CA certificate via install_cert action."""

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(True, "Certificate installed successfully"))
    def test_install_success(self, mock_install, action, empty_context):
        cmd = make_command(action="install_cert")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(False, "certutil failed: Access denied"))
    def test_install_failure(self, mock_install, action, empty_context):
        cmd = make_command(action="install_cert")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "failed" in result.error_message.lower() or "denied" in result.error_message.lower()

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(True, "Certificate installed successfully"))
    def test_install_custom_path(self, mock_install, action, empty_context):
        cmd = make_command(
            action="install_cert",
            certPath="C:\\custom\\cert.cer",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        mock_install.assert_called_once_with("C:\\custom\\cert.cer")


class TestInstallCertValidation:
    """Test validate_command."""

    def test_valid_command(self, action):
        cmd = make_command(action="install_cert")
        assert action.validate_command(cmd) is True

"""Unit tests for remove_certificate action (mocked — no real certutil calls)."""

import pytest
from unittest.mock import patch
from tests.conftest import make_command
from actions.remove_certificate import RemoveCertificateAction


@pytest.fixture
def action(logger):
    a = RemoveCertificateAction()
    a.set_logger(logger)
    return a


class TestRemoveCertificate:
    """Test removing CA certificate via remove_certificate action."""

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(True, "Certificate removed successfully"))
    def test_remove_success(self, mock_remove, action, empty_context):
        cmd = make_command(action="remove_certificate")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(False, "Certificate not found"))
    def test_remove_failure(self, mock_remove, action, empty_context):
        cmd = make_command(action="remove_certificate")
        result = action.execute(cmd, empty_context)
        assert result.success is False

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(True, "Certificate removed successfully"))
    def test_remove_custom_path(self, mock_remove, action, empty_context):
        cmd = make_command(
            action="remove_certificate",
            certPath="C:\\custom\\cert.cer",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        mock_remove.assert_called_once_with("C:\\custom\\cert.cer")


class TestRemoveCertificateValidation:
    """Test validate_command."""

    def test_valid_command(self, action):
        cmd = make_command(action="remove_certificate")
        assert action.validate_command(cmd) is True

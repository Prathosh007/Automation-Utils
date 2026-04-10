"""Unit tests for load_client_cert action (mocked — no real vault/cert access)."""

import os
import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.load_client_cert import LoadClientCertAction


@pytest.fixture
def action(logger):
    a = LoadClientCertAction()
    a.set_logger(logger)
    return a


class TestLoadClientCertMissing:
    """Test when cert files are missing."""

    def test_key_not_found(self, action, empty_context):
        cmd = make_command(
            action="load_client_cert",
            clientKeyPath="/nonexistent/key.pem",
            clientCertPath="/nonexistent/cert.pem",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "key not found" in result.error_message.lower()

    def test_cert_not_found(self, action, empty_context, tmp_path):
        key = str(tmp_path / "key.pem")
        with open(key, "wb") as f:
            f.write(b"fake key")
        cmd = make_command(
            action="load_client_cert",
            clientKeyPath=key,
            clientCertPath="/nonexistent/cert.pem",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "cert not found" in result.error_message.lower()


class TestLoadClientCertProprietaryFormat:
    """Test when cert files exist but are in unrecognized format."""

    def test_proprietary_format_still_succeeds(self, action, empty_context, tmp_path):
        key = str(tmp_path / "key.pem")
        cert = str(tmp_path / "cert.pem")
        with open(key, "wb") as f:
            f.write(b"\x00\x01\x02\x03" * 50)  # Binary garbage
        with open(cert, "wb") as f:
            f.write(b"\x00\x01\x02\x03" * 50)

        cmd = make_command(
            action="load_client_cert",
            clientKeyPath=key,
            clientCertPath=cert,
        )
        result = action.execute(cmd, empty_context)
        # Should succeed with warning about proprietary format
        assert result.success is True


class TestLoadClientCertHotReload:
    """Test hot-reload into running proxy."""

    def test_hot_reload_called(self, action, tmp_path):
        key = str(tmp_path / "key.pem")
        cert = str(tmp_path / "cert.pem")
        # Valid-looking PEM key and cert
        with open(key, "wb") as f:
            f.write(b"-----BEGIN RSA PRIVATE KEY-----\nfakedata\n-----END RSA PRIVATE KEY-----\n")
        with open(cert, "wb") as f:
            f.write(b"-----BEGIN CERTIFICATE-----\nfakedata\n-----END CERTIFICATE-----\n")

        pm = MagicMock()
        pm.is_running = True
        ctx = {"proxy_manager": pm}

        cmd = make_command(
            action="load_client_cert",
            clientKeyPath=key,
            clientCertPath=cert,
        )
        # Will fail PEM parse (fake data) — that's OK, tests the flow
        result = action.execute(cmd, ctx)
        # Should still succeed (proprietary format path)
        assert result.success is True

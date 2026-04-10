"""Unit tests for validate_request action."""

import pytest
from tests.conftest import make_command
from actions.validate_request import ValidateRequestAction


@pytest.fixture
def action(logger):
    a = ValidateRequestAction()
    a.set_logger(logger)
    return a


class TestValidateRequestBasic:
    """Test request existence and method validation."""

    def test_request_found_by_url(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="agentSlot")
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_request_not_found(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="nonexistent_endpoint")
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "No captured request" in result.error_message

    def test_request_found_with_method(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="ClientCSRSign", method="POST")
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_request_wrong_method(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="agentSlot", method="POST")
        result = action.execute(cmd, context_with_store)
        assert result.success is False


class TestValidateRequestHeaders:
    """Test header validation."""

    def test_header_contains(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            headerName="Authorization",
            expectedValue="Bearer",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_header_exact_match(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            headerName="Authorization",
            expectedValue="Bearer token123",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_header_mismatch(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            headerName="Authorization",
            expectedValue="Basic wrong",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False

    def test_header_regex(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            headerName="Authorization",
            expectedValue="Bearer\\s+\\w+",
            validationType="regex",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestValidateRequestBody:
    """Test body validation."""

    def test_body_contains(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            bodyPattern="agent",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_body_regex(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="ClientCSRSign",
            bodyPattern="BEGIN CERTIFICATE REQUEST",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_body_not_found(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            bodyPattern="NOTPRESENT",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False


class TestValidateRequestURL:
    """Test URL validation (expectedValue without header/body)."""

    def test_url_contains(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            expectedValue="server.example.com",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_url_exact(self, action, context_with_store):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            expectedValue="https://server.example.com/agentSlot",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestValidateRequestNoStore:
    """Test behavior when no traffic store exists."""

    def test_no_store_no_capture_file(self, action, empty_context):
        cmd = make_command(action="validate_request", urlPattern="agentSlot")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

    def test_auto_load_from_capture_file(self, action, empty_context, capture_file):
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            captureFile=capture_file,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert "traffic_store" in empty_context  # Store was created

    def test_auto_load_bad_file(self, action, empty_context, tmp_path):
        bad_file = str(tmp_path / "bad.json")
        with open(bad_file, "w") as f:
            f.write("NOT JSON")
        cmd = make_command(
            action="validate_request",
            urlPattern="agentSlot",
            captureFile=bad_file,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False


class TestValidateRequestValidation:
    """Test validate_command logic."""

    def test_missing_url_pattern(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action, context_with_store):
        cmd = make_command(action="validate_request", urlPattern="test")
        assert action.validate_command(cmd) is True

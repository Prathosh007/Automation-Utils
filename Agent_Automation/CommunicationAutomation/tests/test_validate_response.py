"""Unit tests for validate_response action."""

import pytest
from tests.conftest import make_command
from actions.validate_response import ValidateResponseAction


@pytest.fixture
def action(logger):
    a = ValidateResponseAction()
    a.set_logger(logger)
    return a


class TestValidateResponseStatus:
    """Test response status validation."""

    def test_status_match(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            expectedStatus=200,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_status_mismatch(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            expectedStatus=404,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "Expected: 404" in result.error_message
        assert "Got: 200" in result.error_message

    def test_no_status_check_just_exists(self, action, context_with_store):
        cmd = make_command(action="validate_response", urlPattern="agentSlot")
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestValidateResponseHeaders:
    """Test response header validation."""

    def test_response_header_contains(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            headerName="Content-Type",
            expectedValue="xml",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_response_header_exact(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            headerName="Content-Type",
            expectedValue="application/xml",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_response_header_mismatch(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            headerName="Content-Type",
            expectedValue="text/html",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False


class TestValidateResponseBody:
    """Test response body validation."""

    def test_body_contains(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            expectedValue="OK",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_body_pattern(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            bodyPattern="<response>OK</response>",
            validationType="exact",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_body_regex(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            bodyPattern="<response>\\w+</response>",
            validationType="regex",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_body_mismatch(self, action, context_with_store):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            bodyPattern="ERROR",
            validationType="contains",
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False


class TestValidateResponseNoStore:
    """Test when no traffic store exists."""

    def test_no_store_fails(self, action, empty_context):
        cmd = make_command(action="validate_response", urlPattern="agentSlot")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

    def test_auto_load_from_capture_file(self, action, empty_context, capture_file):
        cmd = make_command(
            action="validate_response",
            urlPattern="agentSlot",
            captureFile=capture_file,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    def test_no_matching_response(self, action, context_with_store):
        cmd = make_command(action="validate_response", urlPattern="nonexistent")
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "No captured response" in result.error_message


class TestValidateResponseValidation:
    """Test validate_command logic."""

    def test_missing_url_pattern(self, action):
        cmd = make_command(action="validate_response", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="validate_response", urlPattern="test")
        assert action.validate_command(cmd) is True

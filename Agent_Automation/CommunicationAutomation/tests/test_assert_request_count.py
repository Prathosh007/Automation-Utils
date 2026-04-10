"""Unit tests for assert_request_count action."""

import pytest
from tests.conftest import make_command
from actions.assert_request_count import AssertRequestCountAction


@pytest.fixture
def action(logger):
    a = AssertRequestCountAction()
    a.set_logger(logger)
    return a


class TestAssertRequestCountExact:
    """Test exact count assertions."""

    def test_exact_count_pass(self, action, context_with_store):
        # populated_store has 3 flows total
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            expectedCount=3,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_exact_count_fail(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            expectedCount=5,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "Expected: exactly 5" in result.error_message

    def test_exact_count_for_specific_url(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern="agentSlot",
            expectedCount=1,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_exact_count_zero(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern="nonexistent",
            expectedCount=0,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestAssertRequestCountMinMax:
    """Test min/max count assertions."""

    def test_min_count_pass(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            minCount=1,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_min_count_fail(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            minCount=10,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "at least 10" in result.error_message

    def test_max_count_pass(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            maxCount=5,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_max_count_fail(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            maxCount=2,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "at most 2" in result.error_message

    def test_method_filter(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            method="POST",
            expectedCount=1,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_method_filter_get(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            method="GET",
            expectedCount=2,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestAssertRequestCountNoStore:
    """Test when no traffic store exists."""

    def test_no_store_fails(self, action, empty_context):
        cmd = make_command(action="assert_request_count", urlPattern=".*")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

    def test_auto_load_from_capture_file(self, action, empty_context, capture_file):
        cmd = make_command(
            action="assert_request_count",
            urlPattern=".*",
            captureFile=capture_file,
            minCount=1,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True


class TestAssertRequestCountValidation:
    """Test validate_command."""

    def test_missing_url_pattern(self, action):
        cmd = make_command(action="assert_request_count", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="assert_request_count", urlPattern=".*")
        assert action.validate_command(cmd) is True

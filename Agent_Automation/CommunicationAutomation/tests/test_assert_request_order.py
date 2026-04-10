"""Unit tests for assert_request_order action."""

import pytest
from tests.conftest import make_command
from actions.assert_request_order import AssertRequestOrderAction


@pytest.fixture
def action(logger):
    a = AssertRequestOrderAction()
    a.set_logger(logger)
    return a


class TestAssertRequestOrderPass:
    """Test correct order assertions."""

    def test_correct_order(self, action, context_with_store):
        # Flows are: agentSlot, ClientCSRSign, meta-data.xml
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["agentSlot", "ClientCSRSign"],
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_full_order(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["agentSlot", "ClientCSRSign", "meta-data"],
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_first_and_last(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["agentSlot", "meta-data"],
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True


class TestAssertRequestOrderFail:
    """Test incorrect order assertions."""

    def test_wrong_order(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["meta-data", "agentSlot"],
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "Violation" in result.error_message

    def test_pattern_not_found(self, action, context_with_store):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["agentSlot", "nonexistent_endpoint"],
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is False
        assert "not found" in result.error_message

    def test_empty_store(self, action):
        from proxy.traffic_store import TrafficStore
        ctx = {"traffic_store": TrafficStore()}
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["a", "b"],
        )
        result = action.execute(cmd, ctx)
        assert result.success is False
        assert "No captured traffic" in result.error_message


class TestAssertRequestOrderNoStore:
    """Test when no traffic store exists."""

    def test_no_store_fails(self, action, empty_context):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["a", "b"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

    def test_auto_load_from_capture_file(self, action, empty_context, capture_file):
        cmd = make_command(
            action="assert_request_order",
            expectedOrder=["agentSlot", "ClientCSRSign"],
            captureFile=capture_file,
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True


class TestAssertRequestOrderValidation:
    """Test validate_command."""

    def test_missing_expected_order(self, action):
        cmd = make_command(action="assert_request_order")
        assert action.validate_command(cmd) is False

    def test_single_item_fails(self, action):
        cmd = make_command(action="assert_request_order", expectedOrder=["one"])
        assert action.validate_command(cmd) is False

    def test_two_items_passes(self, action):
        cmd = make_command(action="assert_request_order", expectedOrder=["a", "b"])
        assert action.validate_command(cmd) is True

"""Unit tests for clear_captures action."""

import json
import os
import pytest
from tests.conftest import make_command
from actions.clear_captures import ClearCapturesAction


@pytest.fixture
def action(logger):
    a = ClearCapturesAction()
    a.set_logger(logger)
    return a


class TestClearCaptures:
    """Test clearing the traffic buffer."""

    def test_clear_populated_store(self, action, context_with_store):
        store = context_with_store["traffic_store"]
        assert store.count() == 3

        cmd = make_command(action="clear_captures")
        result = action.execute(cmd, context_with_store)
        assert result.success is True
        assert store.count() == 0

    def test_clear_empty_store(self, action, traffic_store):
        ctx = {"traffic_store": traffic_store}
        cmd = make_command(action="clear_captures")
        result = action.execute(cmd, ctx)
        assert result.success is True

    def test_clear_no_store(self, action, empty_context):
        cmd = make_command(action="clear_captures")
        result = action.execute(cmd, empty_context)
        assert result.success is True

    def test_clear_does_not_affect_rules(self, action, context_with_store):
        """clear_captures should NOT clear response rules."""
        from proxy.response_rules import ResponseRule
        engine = context_with_store["rule_engine"]
        engine.add_rule(ResponseRule(url_pattern="test", inject_status=500))

        cmd = make_command(action="clear_captures")
        action.execute(cmd, context_with_store)

        # Rule should still be there
        rule = engine.get_matching_rule("https://host/test", "GET")
        assert rule is not None
        assert rule.inject_status == 500

    def test_clear_capture_file(self, action, context_with_store, tmp_path):
        """clear_captures should truncate the capture file on disk."""
        capture = tmp_path / "traffic.json"
        capture.write_text(json.dumps([{"url": "http://example.com"}]))

        cmd = make_command(action="clear_captures", capture_file=str(capture))
        result = action.execute(cmd, context_with_store)
        assert result.success is True

        data = json.loads(capture.read_text())
        assert data == []

    def test_clear_capture_file_not_found(self, action, empty_context):
        """clear_captures should succeed even if capture file doesn't exist."""
        cmd = make_command(action="clear_captures", capture_file="nonexistent.json")
        result = action.execute(cmd, empty_context)
        assert result.success is True

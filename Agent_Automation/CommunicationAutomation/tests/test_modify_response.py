"""Unit tests for modify_response action."""

import pytest
from tests.conftest import make_command
from actions.modify_response import ModifyResponseAction
from proxy.response_rules import ResponseRuleEngine


@pytest.fixture
def action(logger):
    a = ModifyResponseAction()
    a.set_logger(logger)
    return a


class TestModifyResponseAddRule:
    """Test adding response modification rules."""

    def test_add_oneshot_rule(self, action, context_with_store):
        cmd = make_command(
            action="modify_response",
            urlPattern="meta-data\\.xml",
            injectStatus=500,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_add_persistent_rule(self, action, context_with_store):
        cmd = make_command(
            action="modify_response",
            urlPattern="agentSlot",
            injectStatus=403,
            persistent=True,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_inject_body(self, action, context_with_store):
        cmd = make_command(
            action="modify_response",
            urlPattern="agentSlot",
            injectBody='{"error": "forbidden"}',
            injectStatus=403,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_inject_headers(self, action, context_with_store):
        cmd = make_command(
            action="modify_response",
            urlPattern="agentSlot",
            injectHeaders={"X-Test": "value"},
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_rule_actually_added_to_engine(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="modify_response",
            urlPattern="test_url",
            injectStatus=500,
        )
        action.execute(cmd, ctx)
        rule = rule_engine.get_matching_rule("https://host/test_url", "GET")
        assert rule is not None
        assert rule.inject_status == 500

    def test_oneshot_removed_after_match(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="modify_response",
            urlPattern="test_url",
            injectStatus=500,
            persistent=False,
        )
        action.execute(cmd, ctx)
        # First match removes it
        rule_engine.get_matching_rule("https://host/test_url", "GET")
        # Second match returns None
        rule = rule_engine.get_matching_rule("https://host/test_url", "GET")
        assert rule is None

    def test_persistent_survives_match(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="modify_response",
            urlPattern="test_url",
            injectStatus=500,
            persistent=True,
        )
        action.execute(cmd, ctx)
        rule_engine.get_matching_rule("https://host/test_url", "GET")
        rule = rule_engine.get_matching_rule("https://host/test_url", "GET")
        assert rule is not None


class TestModifyResponseNoEngine:
    """Test when no rule engine exists."""

    def test_no_rule_engine_fails(self, action, empty_context):
        cmd = make_command(action="modify_response", urlPattern="test")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No rule engine" in result.error_message


class TestModifyResponseValidation:
    """Test validate_command."""

    def test_missing_url_pattern(self, action):
        cmd = make_command(action="modify_response", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="modify_response", urlPattern=".*")
        assert action.validate_command(cmd) is True

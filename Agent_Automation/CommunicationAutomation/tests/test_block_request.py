"""Unit tests for block_request action."""

import pytest
from tests.conftest import make_command
from actions.block_request import BlockRequestAction
from proxy.response_rules import ResponseRuleEngine


@pytest.fixture
def action(logger):
    a = BlockRequestAction()
    a.set_logger(logger)
    return a


class TestBlockRequestAction:
    """Test blocking and delaying requests."""

    def test_block_request(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="block_request",
            urlPattern="agentSlot",
            blockAction="block",
        )
        result = action.execute(cmd, ctx)
        assert result.success is True

        rule = rule_engine.get_matching_rule("https://host/agentSlot", "GET")
        assert rule is not None
        assert rule.block is True

    def test_delay_request(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="block_request",
            urlPattern="agentSlot",
            blockAction="delay",
            delayMs=5000,
        )
        result = action.execute(cmd, ctx)
        assert result.success is True

    def test_block_with_method(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="block_request",
            urlPattern="agentSlot",
            method="POST",
            blockAction="block",
        )
        result = action.execute(cmd, ctx)
        assert result.success is True

        # Should match POST
        rule = rule_engine.get_matching_rule("https://host/agentSlot", "POST")
        assert rule is not None
        # Should NOT match GET (method filter)
        rule_engine2 = ResponseRuleEngine()
        ctx2 = {"rule_engine": rule_engine2}
        action.execute(cmd, ctx2)
        rule2 = rule_engine2.get_matching_rule("https://host/agentSlot", "GET")
        assert rule2 is None

    def test_persistent_block(self, action, rule_engine):
        ctx = {"rule_engine": rule_engine}
        cmd = make_command(
            action="block_request",
            urlPattern="agentSlot",
            blockAction="block",
            persistent=True,
        )
        action.execute(cmd, ctx)
        # First match
        rule_engine.get_matching_rule("https://host/agentSlot", "GET")
        # Second match should still work
        rule = rule_engine.get_matching_rule("https://host/agentSlot", "GET")
        assert rule is not None


class TestBlockRequestNoEngine:
    """Test when no rule engine exists."""

    def test_no_rule_engine(self, action, empty_context):
        cmd = make_command(action="block_request", urlPattern="test")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No rule engine" in result.error_message


class TestBlockRequestValidation:
    """Test validate_command."""

    def test_missing_url_pattern(self, action):
        cmd = make_command(action="block_request", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="block_request", urlPattern=".*")
        assert action.validate_command(cmd) is True

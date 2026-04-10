"""Unit tests for core models, TrafficStore, ResponseRuleEngine, PatternMatcher, and ActionRegistry."""

import pytest
from datetime import datetime
from tests.conftest import make_command

from core.models import Command, CommandResult
from proxy.traffic_store import TrafficStore, CapturedFlow
from proxy.response_rules import ResponseRuleEngine, ResponseRule
from utils.pattern_matcher import matches_pattern, validate_value
from core.action_registry import ActionRegistry


# ===================================================================
# Command model tests
# ===================================================================

class TestCommandFromDict:
    """Test JSON dict → Command mapping."""

    def test_camel_case_mapping(self):
        cmd = Command.from_dict({
            "id": "TC_1",
            "action": "validate_request",
            "urlPattern": "agentSlot",
            "continueOnFailure": True,
            "listenPort": 9090,
        })
        assert cmd.id == "TC_1"
        assert cmd.action == "validate_request"
        assert cmd.url_pattern == "agentSlot"
        assert cmd.continue_on_failure is True
        assert cmd.listen_port == 9090

    def test_snake_case_mapping(self):
        cmd = Command.from_dict({
            "id": "TC_2",
            "action": "start_proxy",
            "listen_host": "0.0.0.0",
            "ssl_insecure": True,
        })
        assert cmd.listen_host == "0.0.0.0"
        assert cmd.ssl_insecure is True

    def test_unknown_keys_ignored(self):
        cmd = Command.from_dict({"id": "X", "action": "test", "unknownField": 42})
        assert cmd.id == "X"

    def test_defaults(self):
        cmd = Command.from_dict({"action": "test"})
        assert cmd.id == ""
        assert cmd.listen_port == 8080
        assert cmd.continue_on_failure is False
        assert cmd.validation_type == "contains"
        assert cmd.expected_count == -1

    def test_list_fields(self):
        cmd = Command.from_dict({
            "action": "patch_registry",
            "oldValues": ["a", "b"],
            "newValues": ["c", "d"],
            "expectedOrder": ["x", "y"],
        })
        assert cmd.old_values == ["a", "b"]
        assert cmd.new_values == ["c", "d"]
        assert cmd.expected_order == ["x", "y"]

    def test_dict_fields(self):
        cmd = Command.from_dict({
            "action": "modify_response",
            "injectHeaders": {"X-Custom": "val"},
        })
        assert cmd.inject_headers == {"X-Custom": "val"}


# ===================================================================
# TrafficStore tests
# ===================================================================

class TestTrafficStore:
    """Test TrafficStore operations."""

    def test_add_and_count(self):
        store = TrafficStore()
        assert store.count() == 0
        store.add_flow(CapturedFlow(request_url="http://a", request_method="GET"))
        assert store.count() == 1

    def test_get_matching_flows(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://host/api", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="http://host/health", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="http://host/api", request_method="POST"))

        matches = store.get_matching_flows(url_pattern="api")
        assert len(matches) == 2

        matches = store.get_matching_flows(url_pattern="api", method="POST")
        assert len(matches) == 1

    def test_get_latest_flow(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://host/a", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="http://host/b", request_method="GET"))

        flow = store.get_latest_flow()
        assert flow.request_url == "http://host/b"

    def test_get_latest_flow_filtered(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://host/a", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="http://host/b", request_method="POST"))

        flow = store.get_latest_flow(method="GET")
        assert flow.request_url == "http://host/a"

    def test_clear(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://host/a"))
        store.add_flow(CapturedFlow(request_url="http://host/b"))
        cleared = store.clear()
        assert cleared == 2
        assert store.count() == 0

    def test_get_all_flows(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://a"))
        store.add_flow(CapturedFlow(request_url="http://b"))
        flows = store.get_all_flows()
        assert len(flows) == 2

    def test_count_filtered(self):
        store = TrafficStore()
        store.add_flow(CapturedFlow(request_url="http://host/api", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="http://host/api", request_method="POST"))
        store.add_flow(CapturedFlow(request_url="http://host/health", request_method="GET"))

        assert store.count(url_pattern="api") == 2
        assert store.count(method="POST") == 1
        assert store.count(url_pattern="health", method="GET") == 1

    def test_captured_flow_to_dict(self):
        flow = CapturedFlow(
            timestamp=datetime(2026, 1, 1),
            request_url="http://test",
            request_method="GET",
            request_headers={"H": "V"},
            request_body="body",
            response_status=200,
            response_headers={"RH": "RV"},
            response_body="resp",
            response_reason="OK",
        )
        d = flow.to_dict()
        assert d["request"]["url"] == "http://test"
        assert d["request"]["method"] == "GET"
        assert d["response"]["status"] == 200
        assert "timestamp" in d


class TestTrafficStoreLiveCapture:
    """Test live capture file writing."""

    def test_live_capture_file(self, tmp_path):
        path = str(tmp_path / "live.json")
        store = TrafficStore(live_capture_file=path)
        store.add_flow(CapturedFlow(request_url="http://test", request_method="GET"))

        import json
        with open(path) as f:
            data = json.load(f)
        assert len(data) == 1
        assert data[0]["request"]["url"] == "http://test"


# ===================================================================
# ResponseRuleEngine tests
# ===================================================================

class TestResponseRuleEngine:
    """Test response rule engine."""

    def test_add_and_match(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", inject_status=500))
        rule = engine.get_matching_rule("http://host/api", "GET")
        assert rule is not None
        assert rule.inject_status == 500

    def test_oneshot_removed(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", inject_status=500, persistent=False))
        engine.get_matching_rule("http://host/api", "GET")
        rule = engine.get_matching_rule("http://host/api", "GET")
        assert rule is None

    def test_persistent_kept(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", inject_status=500, persistent=True))
        engine.get_matching_rule("http://host/api", "GET")
        rule = engine.get_matching_rule("http://host/api", "GET")
        assert rule is not None

    def test_method_filter(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", method="POST", inject_status=500))
        assert engine.get_matching_rule("http://host/api", "GET") is None
        rule = engine.get_matching_rule("http://host/api", "POST")
        assert rule is not None

    def test_clear_rules(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="a"))
        engine.add_rule(ResponseRule(url_pattern="b"))
        cleared = engine.clear_rules()
        assert cleared == 2
        assert engine.get_matching_rule("http://host/a", "GET") is None

    def test_remove_rules_for_pattern(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", persistent=True))
        engine.add_rule(ResponseRule(url_pattern="health", persistent=True))
        removed = engine.remove_rules_for_pattern("api")
        assert removed == 1
        assert engine.get_matching_rule("http://host/api", "GET") is None
        assert engine.get_matching_rule("http://host/health", "GET") is not None

    def test_no_match_returns_none(self):
        engine = ResponseRuleEngine()
        assert engine.get_matching_rule("http://host/api", "GET") is None

    def test_block_rule(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", block=True))
        rule = engine.get_matching_rule("http://host/api", "GET")
        assert rule.block is True

    def test_delay_rule(self):
        engine = ResponseRuleEngine()
        engine.add_rule(ResponseRule(url_pattern="api", delay_ms=5000))
        rule = engine.get_matching_rule("http://host/api", "GET")
        assert rule.delay_ms == 5000


# ===================================================================
# PatternMatcher tests
# ===================================================================

class TestMatchesPattern:
    """Test regex pattern matching."""

    def test_simple_match(self):
        assert matches_pattern("http://host/agentSlot", "agentSlot") is True

    def test_no_match(self):
        assert matches_pattern("http://host/health", "agentSlot") is False

    def test_regex_pattern(self):
        assert matches_pattern("http://host/api/v2/data", r"api/v\d+") is True

    def test_case_insensitive_default(self):
        assert matches_pattern("http://host/AgentSlot", "agentslot") is True

    def test_case_sensitive(self):
        assert matches_pattern("http://host/AgentSlot", "agentslot", case_sensitive=True) is False

    def test_empty_pattern(self):
        assert matches_pattern("anything", "") is True

    def test_invalid_regex_fallback(self):
        assert matches_pattern("hello [world", "[world") is True


class TestValidateValue:
    """Test validation logic."""

    def test_exact_match(self):
        ok, msg = validate_value("hello", "hello", "exact")
        assert ok is True

    def test_exact_mismatch(self):
        ok, msg = validate_value("hello", "world", "exact")
        assert ok is False

    def test_contains_match(self):
        ok, msg = validate_value("hello world", "world", "contains")
        assert ok is True

    def test_contains_mismatch(self):
        ok, msg = validate_value("hello", "xyz", "contains")
        assert ok is False

    def test_startswith(self):
        ok, _ = validate_value("hello world", "hello", "startswith")
        assert ok is True

    def test_endswith(self):
        ok, _ = validate_value("hello world", "world", "endswith")
        assert ok is True

    def test_regex(self):
        ok, _ = validate_value("abc123", r"\w+\d+", "regex")
        assert ok is True

    def test_regex_invalid(self):
        ok, msg = validate_value("test", "[invalid", "regex")
        assert ok is False
        assert "invalid regex" in msg

    def test_notempty(self):
        ok, _ = validate_value("hello", "", "notempty")
        assert ok is True
        ok, _ = validate_value("", "", "notempty")
        assert ok is False

    def test_isempty(self):
        ok, _ = validate_value("", "", "isempty")
        assert ok is True
        ok, _ = validate_value("hello", "", "isempty")
        assert ok is False

    def test_case_insensitive_default(self):
        ok, _ = validate_value("Hello", "hello", "exact")
        assert ok is True

    def test_case_sensitive(self):
        ok, _ = validate_value("Hello", "hello", "exact", case_sensitive=True)
        assert ok is False

    def test_unknown_type(self):
        ok, msg = validate_value("a", "a", "unknown_type")
        assert ok is False
        assert "Unknown" in msg


# ===================================================================
# ActionRegistry tests
# ===================================================================

class TestActionRegistry:
    """Test action registry lookup."""

    def test_all_actions_registered(self):
        registry = ActionRegistry()
        actions = [
            "start_proxy", "stop_proxy", "configure_proxy",
            "install_certificate", "install_cert", "remove_certificate",
            "load_client_cert", "wait_for_request",
            "validate_request", "validate_response", "modify_response",
            "block_request", "assert_request_count", "assert_request_order",
            "capture_traffic", "load_traffic", "clear_captures",
            "wait_for_interrupt",
        ]
        for name in actions:
            action = registry.get_action(name)
            assert action is not None, f"Action '{name}' not registered"

    def test_case_insensitive_lookup(self):
        registry = ActionRegistry()
        action = registry.get_action("Start_Proxy")
        assert action is not None

    def test_unknown_action_raises(self):
        registry = ActionRegistry()
        with pytest.raises(NotImplementedError):
            registry.get_action("nonexistent_action")

    def test_register_custom_action(self):
        from core.base_action import BaseAction
        from core.models import Command, CommandResult

        class CustomAction(BaseAction):
            def execute(self, command, context):
                return self.create_result(command)

        registry = ActionRegistry()
        registry.register_action("custom", CustomAction())
        action = registry.get_action("custom")
        assert action is not None

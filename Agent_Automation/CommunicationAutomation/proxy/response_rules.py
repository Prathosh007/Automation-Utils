import threading
import time
from dataclasses import dataclass, field
from typing import Optional

from utils.pattern_matcher import matches_pattern


@dataclass
class ResponseRule:
    """A rule that modifies or blocks matching responses."""
    url_pattern: str = ""
    method: str = ""
    # Modification
    inject_status: int = 0
    inject_body: str = ""
    inject_headers: dict = field(default_factory=dict)
    # Blocking
    block: bool = False
    delay_ms: int = 0
    # Body replacement (search→replace on real response body)
    body_replacements: dict = field(default_factory=dict)
    # Lifecycle
    persistent: bool = False
    fired: bool = False


class ResponseRuleEngine:
    """Thread-safe rule engine for response modification/blocking."""

    def __init__(self):
        self._lock = threading.Lock()
        self._rules: list[ResponseRule] = []

    def add_rule(self, rule: ResponseRule) -> None:
        with self._lock:
            self._rules.append(rule)

    def get_matching_rule(self, url: str, method: str) -> Optional[ResponseRule]:
        """Find the first matching rule. One-shot rules are removed after firing."""
        with self._lock:
            for i, rule in enumerate(self._rules):
                if self._rule_matches(rule, url, method):
                    if not rule.persistent:
                        rule.fired = True
                        self._rules.pop(i)
                    return rule
        return None

    def get_response_rules(self, url: str, method: str) -> list[ResponseRule]:
        """Return all persistent rules with body_replacements that match this flow."""
        with self._lock:
            return [r for r in self._rules
                    if r.body_replacements and r.persistent
                    and self._rule_matches(r, url, method)]

    def clear_rules(self) -> int:
        with self._lock:
            count = len(self._rules)
            self._rules.clear()
            return count

    def remove_rules_for_pattern(self, url_pattern: str) -> int:
        with self._lock:
            before = len(self._rules)
            self._rules = [r for r in self._rules if r.url_pattern != url_pattern]
            return before - len(self._rules)

    @staticmethod
    def _rule_matches(rule: ResponseRule, url: str, method: str) -> bool:
        if rule.url_pattern and not matches_pattern(url, rule.url_pattern):
            return False
        if rule.method and rule.method.upper() != method.upper():
            return False
        return True

"""Shared fixtures for CommunicationAutomation unit tests."""

import json
import os
import sys
import pytest
from datetime import datetime
from unittest.mock import MagicMock

# Ensure project root is on sys.path
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from core.models import Command, CommandResult
from proxy.traffic_store import TrafficStore, CapturedFlow
from proxy.response_rules import ResponseRuleEngine
from utils.logger import Logger


# ---------------------------------------------------------------------------
# Helper: build a Command quickly
# ---------------------------------------------------------------------------

def make_command(**kwargs) -> Command:
    """Shortcut to build a Command with defaults + overrides."""
    defaults = {"id": "TEST_001", "action": "test_action"}
    defaults.update(kwargs)
    return Command.from_dict(defaults)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def logger(tmp_path):
    """Provide a Logger writing to a temp directory."""
    return Logger(log_dir=str(tmp_path / "logs"))


@pytest.fixture
def traffic_store():
    """Provide an empty TrafficStore."""
    return TrafficStore()


@pytest.fixture
def rule_engine():
    """Provide an empty ResponseRuleEngine."""
    return ResponseRuleEngine()


@pytest.fixture
def sample_flow():
    """A typical captured flow for testing."""
    return CapturedFlow(
        timestamp=datetime(2026, 3, 10, 12, 0, 0),
        request_url="https://server.example.com/agentSlot",
        request_method="GET",
        request_headers={"Authorization": "Bearer token123", "Content-Type": "application/json"},
        request_body='{"agent": "test"}',
        response_status=200,
        response_headers={"Content-Type": "application/xml"},
        response_body='<response>OK</response>',
        response_reason="OK",
    )


@pytest.fixture
def post_flow():
    """A POST request flow."""
    return CapturedFlow(
        timestamp=datetime(2026, 3, 10, 12, 1, 0),
        request_url="https://server.example.com/ClientCSRSign",
        request_method="POST",
        request_headers={"Content-Type": "application/pkcs10"},
        request_body="-----BEGIN CERTIFICATE REQUEST-----\nMIIBxz...",
        response_status=200,
        response_headers={"Content-Type": "application/x-pem-file"},
        response_body="-----BEGIN CERTIFICATE-----\nMIIC...",
        response_reason="OK",
    )


@pytest.fixture
def metadata_flow():
    """A metadata XML request flow."""
    return CapturedFlow(
        timestamp=datetime(2026, 3, 10, 12, 2, 0),
        request_url="https://server.example.com/meta-data.xml",
        request_method="GET",
        request_headers={},
        request_body="",
        response_status=200,
        response_headers={"Content-Type": "text/xml"},
        response_body='<?xml version="1.0"?>',
        response_reason="OK",
    )


@pytest.fixture
def populated_store(traffic_store, sample_flow, post_flow, metadata_flow):
    """A TrafficStore pre-loaded with three flows."""
    traffic_store.add_flow(sample_flow)
    traffic_store.add_flow(post_flow)
    traffic_store.add_flow(metadata_flow)
    return traffic_store


@pytest.fixture
def context_with_store(populated_store, rule_engine):
    """A context dict with traffic_store and rule_engine."""
    return {
        "traffic_store": populated_store,
        "rule_engine": rule_engine,
    }


@pytest.fixture
def empty_context():
    """A context dict with nothing set."""
    return {}


@pytest.fixture
def capture_file(tmp_path, sample_flow, post_flow):
    """Write a JSON capture file and return its path."""
    data = [sample_flow.to_dict(), post_flow.to_dict()]
    path = str(tmp_path / "test-capture.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    return path

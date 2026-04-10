"""Unit tests for wait_for_request action."""

import json
import os
import threading
import time
import pytest
from datetime import datetime
from tests.conftest import make_command
from actions.wait_for_request import WaitForRequestAction
from proxy.traffic_store import TrafficStore, CapturedFlow


@pytest.fixture
def action(logger):
    a = WaitForRequestAction()
    a.set_logger(logger)
    return a


class TestWaitForRequestInProcess:
    """Test in-process mode (store already has matching flow)."""

    def test_immediate_match(self, action, context_with_store):
        cmd = make_command(
            action="wait_for_request",
            urlPattern="agentSlot",
            timeout=5,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_match_with_method(self, action, context_with_store):
        cmd = make_command(
            action="wait_for_request",
            urlPattern="ClientCSRSign",
            method="POST",
            timeout=5,
        )
        result = action.execute(cmd, context_with_store)
        assert result.success is True

    def test_result_contains_flow_data(self, action, context_with_store):
        cmd = make_command(
            action="wait_for_request",
            urlPattern="agentSlot",
            timeout=5,
        )
        result = action.execute(cmd, context_with_store)
        assert result.data is not None
        assert "request" in result.data
        assert result.data["request"]["method"] == "GET"

    def test_timeout_no_match(self, action):
        store = TrafficStore()
        ctx = {"traffic_store": store}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="nonexistent",
            timeout=1,
        )
        result = action.execute(cmd, ctx)
        assert result.success is False
        assert "Timeout" in result.error_message

    def test_delayed_match(self, action):
        """Flow arrives after a short delay — should still be matched."""
        store = TrafficStore()
        ctx = {"traffic_store": store}

        def add_flow_after_delay():
            time.sleep(0.5)
            store.add_flow(CapturedFlow(
                request_url="https://host/delayed_endpoint",
                request_method="GET",
            ))

        t = threading.Thread(target=add_flow_after_delay)
        t.start()

        cmd = make_command(
            action="wait_for_request",
            urlPattern="delayed_endpoint",
            timeout=5,
        )
        result = action.execute(cmd, ctx)
        t.join()
        assert result.success is True


class TestWaitForRequestFilePolling:
    """Test file-polling mode."""

    def test_file_poll_existing_flow(self, action, capture_file):
        ctx = {}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="agentSlot",
            captureFile=capture_file,
            timeout=5,
        )
        result = action.execute(cmd, ctx)
        assert result.success is True
        assert "traffic_store" in ctx  # Store populated

    def test_file_poll_populates_store(self, action, capture_file):
        ctx = {}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="agentSlot",
            captureFile=capture_file,
            timeout=5,
        )
        action.execute(cmd, ctx)
        store = ctx.get("traffic_store")
        assert store is not None
        assert store.count() >= 1

    def test_file_poll_timeout(self, action, tmp_path):
        # Create a file with no matching flows
        path = str(tmp_path / "empty.json")
        with open(path, "w") as f:
            json.dump([], f)

        ctx = {}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="nonexistent",
            captureFile=path,
            timeout=2,
        )
        result = action.execute(cmd, ctx)
        assert result.success is False
        assert "Timeout" in result.error_message

    def test_file_poll_file_missing(self, action):
        ctx = {}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="test",
            captureFile="/nonexistent/file.json",
            timeout=2,
        )
        result = action.execute(cmd, ctx)
        assert result.success is False

    def test_no_store_no_capture_file(self, action):
        ctx = {}
        cmd = make_command(
            action="wait_for_request",
            urlPattern="test",
            timeout=2,
        )
        result = action.execute(cmd, ctx)
        assert result.success is False
        assert "No traffic store" in result.error_message


class TestWaitForRequestValidation:
    """Test validate_command."""

    def test_missing_url_pattern(self, action):
        cmd = make_command(action="wait_for_request", urlPattern="")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="wait_for_request", urlPattern="test")
        assert action.validate_command(cmd) is True

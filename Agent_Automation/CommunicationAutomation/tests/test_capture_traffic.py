"""Unit tests for capture_traffic action."""

import json
import os
import pytest
from tests.conftest import make_command
from actions.capture_traffic import CaptureTrafficAction


@pytest.fixture
def action(logger):
    a = CaptureTrafficAction()
    a.set_logger(logger)
    return a


class TestCaptureTraffic:
    """Test dumping traffic to file."""

    def test_capture_to_file(self, action, context_with_store, tmp_path):
        out = str(tmp_path / "capture.json")
        cmd = make_command(action="capture_traffic", captureFile=out)
        result = action.execute(cmd, context_with_store)
        assert result.success is True
        assert os.path.exists(out)

        with open(out, "r") as f:
            data = json.load(f)
        assert isinstance(data, list)
        assert len(data) == 3  # 3 flows in populated_store

    def test_capture_default_filename(self, action, context_with_store):
        cmd = make_command(action="capture_traffic")
        result = action.execute(cmd, context_with_store)
        assert result.success is True
        # Default file
        assert os.path.exists("Reports/traffic-capture.json")
        os.remove("Reports/traffic-capture.json")

    def test_capture_creates_directories(self, action, context_with_store, tmp_path):
        out = str(tmp_path / "subdir" / "deep" / "capture.json")
        cmd = make_command(action="capture_traffic", captureFile=out)
        result = action.execute(cmd, context_with_store)
        assert result.success is True
        assert os.path.exists(out)

    def test_capture_empty_store(self, action, traffic_store, tmp_path):
        ctx = {"traffic_store": traffic_store}
        out = str(tmp_path / "empty.json")
        cmd = make_command(action="capture_traffic", captureFile=out)
        result = action.execute(cmd, ctx)
        assert result.success is True

    def test_capture_json_structure(self, action, context_with_store, tmp_path):
        out = str(tmp_path / "struct.json")
        cmd = make_command(action="capture_traffic", captureFile=out)
        action.execute(cmd, context_with_store)

        with open(out, "r") as f:
            data = json.load(f)
        flow = data[0]
        assert "request" in flow
        assert "response" in flow
        assert "timestamp" in flow
        assert "url" in flow["request"]
        assert "method" in flow["request"]
        assert "status" in flow["response"]


class TestCaptureTrafficNoStore:
    """Test when no store available."""

    def test_no_store_fails(self, action, empty_context):
        cmd = make_command(action="capture_traffic")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

"""Unit tests for load_traffic action."""

import json
import os
import pytest
from tests.conftest import make_command
from actions.load_traffic import LoadTrafficAction


@pytest.fixture
def action(logger):
    a = LoadTrafficAction()
    a.set_logger(logger)
    return a


class TestLoadTraffic:
    """Test loading traffic from a capture file."""

    def test_load_valid_file(self, action, empty_context, capture_file):
        cmd = make_command(action="load_traffic", captureFile=capture_file)
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert "traffic_store" in empty_context
        assert empty_context["traffic_store"].count() == 2

    def test_load_creates_store_if_missing(self, action, empty_context, capture_file):
        assert "traffic_store" not in empty_context
        cmd = make_command(action="load_traffic", captureFile=capture_file)
        action.execute(cmd, empty_context)
        assert "traffic_store" in empty_context

    def test_load_appends_to_existing_store(self, action, context_with_store, capture_file):
        store = context_with_store["traffic_store"]
        before = store.count()  # 3
        cmd = make_command(action="load_traffic", captureFile=capture_file)
        action.execute(cmd, context_with_store)
        assert store.count() == before + 2

    def test_load_file_not_found(self, action, empty_context):
        cmd = make_command(action="load_traffic", captureFile="/nonexistent/file.json")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "not found" in result.error_message

    def test_load_invalid_json(self, action, empty_context, tmp_path):
        bad = str(tmp_path / "bad.json")
        with open(bad, "w") as f:
            f.write("{not valid json")
        cmd = make_command(action="load_traffic", captureFile=bad)
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "Invalid JSON" in result.error_message

    def test_load_not_array(self, action, empty_context, tmp_path):
        bad = str(tmp_path / "obj.json")
        with open(bad, "w") as f:
            json.dump({"key": "value"}, f)
        cmd = make_command(action="load_traffic", captureFile=bad)
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "JSON array" in result.error_message

    def test_load_empty_array(self, action, empty_context, tmp_path):
        empty = str(tmp_path / "empty.json")
        with open(empty, "w") as f:
            json.dump([], f)
        cmd = make_command(action="load_traffic", captureFile=empty)
        result = action.execute(cmd, empty_context)
        assert result.success is True

    def test_result_data(self, action, empty_context, capture_file):
        cmd = make_command(action="load_traffic", captureFile=capture_file)
        result = action.execute(cmd, empty_context)
        assert result.data["loaded"] == 2
        assert result.data["file"] == capture_file


class TestLoadTrafficValidation:
    """Test validate_command."""

    def test_missing_capture_file(self, action):
        cmd = make_command(action="load_traffic")
        assert action.validate_command(cmd) is False

    def test_valid_command(self, action):
        cmd = make_command(action="load_traffic", captureFile="test.json")
        assert action.validate_command(cmd) is True

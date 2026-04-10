"""Unit tests for switch_capture_file action."""

import json
import os
import threading
import pytest
from tests.conftest import make_command
from actions.switch_capture_file import SwitchCaptureFileAction, CONTROL_FILE, ACK_FILE


@pytest.fixture
def action(logger):
    a = SwitchCaptureFileAction()
    a.set_logger(logger)
    return a


@pytest.fixture(autouse=True)
def cleanup_control_files():
    """Remove control/ack files before and after each test."""
    for path in (CONTROL_FILE, ACK_FILE):
        if os.path.exists(path):
            os.remove(path)
    yield
    for path in (CONTROL_FILE, ACK_FILE):
        if os.path.exists(path):
            os.remove(path)


class TestSwitchCaptureFile:
    """Test switching the live capture file on the background proxy."""

    def test_validate_requires_live_capture_file(self, action):
        cmd = make_command(action="switch_capture_file")
        assert action.validate_command(cmd) is False

        cmd = make_command(action="switch_capture_file", live_capture_file="Reports/new.json")
        assert action.validate_command(cmd) is True

    def test_fails_without_running_proxy(self, action, empty_context):
        cmd = make_command(action="switch_capture_file",
                           live_capture_file="Reports/new.json")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No background proxy running" in result.message

    def test_reads_pid_from_file(self, action, empty_context):
        """When context has no PID, action should fall back to Reports/proxy.pid."""
        pid_file = os.path.join("Reports", "proxy.pid")
        os.makedirs("Reports", exist_ok=True)
        # Write the current process PID so os.kill(pid, 0) succeeds
        with open(pid_file, "w") as f:
            f.write(str(os.getpid()))

        try:
            cmd = make_command(action="switch_capture_file",
                               live_capture_file="Reports/new.json",
                               timeout=1)
            result = action.execute(cmd, empty_context)
            # It should get past the PID check (fail on ack timeout instead)
            assert "No background proxy running" not in result.message
            assert "did not acknowledge" in result.message
        finally:
            if os.path.exists(pid_file):
                os.remove(pid_file)

    def test_fails_on_ack_timeout(self, action, empty_context):
        """Should fail when proxy doesn't acknowledge within timeout."""
        ctx = {"background_proxy_pid": 12345}
        cmd = make_command(action="switch_capture_file",
                           live_capture_file="Reports/new.json",
                           timeout=1)
        result = action.execute(cmd, ctx)
        assert result.success is False
        assert "did not acknowledge" in result.message

    def test_success_with_ack(self, action, tmp_path):
        """Simulate proxy writing acknowledgment after reading control file."""
        ctx = {"background_proxy_pid": 12345}
        new_file = str(tmp_path / "new-traffic.json")
        cmd = make_command(action="switch_capture_file",
                           live_capture_file=new_file,
                           timeout=5)

        # Simulate the proxy: watch for control file and write ack
        def fake_proxy():
            for _ in range(50):
                if os.path.isfile(CONTROL_FILE):
                    with open(CONTROL_FILE, "r") as f:
                        ctrl = json.load(f)
                    os.remove(CONTROL_FILE)
                    ack = {"status": "ok", "old_file": "Reports/old.json",
                           "new_file": ctrl["file"]}
                    os.makedirs(os.path.dirname(ACK_FILE) or ".", exist_ok=True)
                    with open(ACK_FILE, "w") as f:
                        json.dump(ack, f)
                    return
                import time
                time.sleep(0.1)

        t = threading.Thread(target=fake_proxy, daemon=True)
        t.start()

        result = action.execute(cmd, ctx)
        t.join(timeout=5)

        assert result.success is True
        assert result.data["new_file"] == new_file
        assert result.data["old_file"] == "Reports/old.json"

    def test_proxy_error_ack(self, action, tmp_path):
        """Should fail if proxy returns error status in ack."""
        ctx = {"background_proxy_pid": 12345}
        cmd = make_command(action="switch_capture_file",
                           live_capture_file="Reports/x.json",
                           timeout=5)

        def fake_proxy_error():
            for _ in range(50):
                if os.path.isfile(CONTROL_FILE):
                    os.remove(CONTROL_FILE)
                    os.makedirs(os.path.dirname(ACK_FILE) or ".", exist_ok=True)
                    with open(ACK_FILE, "w") as f:
                        json.dump({"status": "error", "message": "bad action"}, f)
                    return
                import time
                time.sleep(0.1)

        t = threading.Thread(target=fake_proxy_error, daemon=True)
        t.start()

        result = action.execute(cmd, ctx)
        t.join(timeout=5)

        assert result.success is False
        assert "bad action" in result.message


class TestTrafficStoreSwitchFile:
    """Test the TrafficStore.switch_live_capture_file method directly."""

    def test_switch_clears_flows(self, tmp_path):
        from proxy.traffic_store import TrafficStore, CapturedFlow

        old_file = str(tmp_path / "old.json")
        new_file = str(tmp_path / "new.json")

        store = TrafficStore(live_capture_file=old_file)
        store.add_flow(CapturedFlow(request_url="https://example.com/a", request_method="GET"))
        store.add_flow(CapturedFlow(request_url="https://example.com/b", request_method="POST"))
        assert store.count() == 2

        old = store.switch_live_capture_file(new_file, clear_flows=True)
        assert old == old_file
        assert store.count() == 0

        data = json.loads(open(new_file).read())
        assert data == []

    def test_switch_keeps_flows(self, tmp_path):
        from proxy.traffic_store import TrafficStore, CapturedFlow

        old_file = str(tmp_path / "old.json")
        new_file = str(tmp_path / "new.json")

        store = TrafficStore(live_capture_file=old_file)
        store.add_flow(CapturedFlow(request_url="https://example.com/a", request_method="GET"))
        assert store.count() == 1

        store.switch_live_capture_file(new_file, clear_flows=False)
        assert store.count() == 1

        data = json.loads(open(new_file).read())
        assert len(data) == 1
        assert "example.com/a" in data[0]["request"]["url"]

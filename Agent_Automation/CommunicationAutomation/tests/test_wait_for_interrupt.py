"""Unit tests for wait_for_interrupt action."""

import signal
import threading
import time
import pytest
from tests.conftest import make_command
from actions.wait_for_interrupt import WaitForInterruptAction
from proxy.traffic_store import TrafficStore


@pytest.fixture
def action(logger):
    a = WaitForInterruptAction()
    a.set_logger(logger)
    return a


class TestWaitForInterrupt:
    """Test wait_for_interrupt action."""

    def test_no_store_fails(self, action, empty_context):
        cmd = make_command(action="wait_for_interrupt")
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "No traffic store" in result.error_message

    def test_interrupt_stops_recording(self, action, traffic_store):
        """Simulate Ctrl+C after a delay."""
        ctx = {"traffic_store": traffic_store}
        cmd = make_command(action="wait_for_interrupt")

        def send_interrupt():
            time.sleep(0.5)
            # Send SIGINT to the current process
            signal.raise_signal(signal.SIGINT)

        t = threading.Thread(target=send_interrupt)
        t.start()

        result = action.execute(cmd, ctx)
        t.join()
        assert result.success is True

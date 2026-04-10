import signal
import threading

from core.base_action import BaseAction
from core.models import Command, CommandResult


class WaitForInterruptAction(BaseAction):
    """Block indefinitely, recording all traffic until Ctrl+C is pressed.
    On interrupt, returns success so the engine continues to cleanup steps."""

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        store = context.get("traffic_store")
        if not store:
            self.handle_failure(command, result,
                                "[wait_for_interrupt] Error: No traffic store - proxy not started? | Result: FAILED")
            return result

        stop_event = threading.Event()

        original_handler = signal.getsignal(signal.SIGINT)

        def on_interrupt(sig, frame):
            stop_event.set()

        signal.signal(signal.SIGINT, on_interrupt)

        print("\n[RECORDING] Proxy is capturing traffic. Press Ctrl+C to stop and save.\n", flush=True)

        try:
            last_count = 0
            while not stop_event.wait(timeout=10):
                count = store.count()
                if count != last_count:
                    print(f"[RECORDING] {count} request(s) captured so far...", flush=True)
                    last_count = count
        finally:
            signal.signal(signal.SIGINT, original_handler)

        count = store.count()
        self.handle_success(command, result,
                            f"[wait_for_interrupt] Recording stopped | "
                            f"Total requests captured: {count} | Result: PASSED")
        return result

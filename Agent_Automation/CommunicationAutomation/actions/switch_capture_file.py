import json
import os
import time

from core.base_action import BaseAction
from core.models import Command, CommandResult

# Control files shared between the test process and the background proxy
CONTROL_FILE = os.path.join("Reports", "proxy-control.json")
ACK_FILE = os.path.join("Reports", "proxy-control-ack.json")


class SwitchCaptureFileAction(BaseAction):
    """Switch the live capture file on the running background proxy.

    Sends a control command to the background proxy process via a file-based
    signal. The proxy picks it up, switches the TrafficStore's live capture
    file, and writes an acknowledgment. Existing captured flows are cleared
    by default so the new file starts fresh for the next test phase.

    JSON example::

        {
            "action": "switch_capture_file",
            "liveCaptureFile": "Reports\\ip-change-traffic.json",
            "description": "Switch capture to IP change traffic file"
        }
    """

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and bool(command.live_capture_file)

    PID_FILE = os.path.join("Reports", "proxy.pid")

    @staticmethod
    def _is_process_alive(pid: int) -> bool:
        """Check if a process with the given PID is alive (Windows-safe)."""
        if os.name == "nt":
            import ctypes
            kernel32 = ctypes.windll.kernel32
            PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
            handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
            if handle:
                kernel32.CloseHandle(handle)
                return True
            return False
        else:
            try:
                os.kill(pid, 0)
                return True
            except OSError:
                return False

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        new_file = command.live_capture_file
        if not new_file:
            self.handle_failure(command, result,
                                "[switch_capture_file] Error: liveCaptureFile is required | Result: FAILED")
            return result

        # Verify a background proxy is running — check in-memory context first,
        # then fall back to the persisted PID file (needed when this action runs
        # in a separate process invocation from start_proxy).
        pid = context.get("background_proxy_pid")
        if not pid and os.path.isfile(self.PID_FILE):
            try:
                with open(self.PID_FILE) as f:
                    pid = int(f.read().strip())
                if not self._is_process_alive(pid):
                    pid = None
            except (ValueError, OSError):
                pid = None
        if not pid:
            self.handle_failure(command, result,
                                "[switch_capture_file] Error: No background proxy running "
                                "(start_proxy first) | Result: FAILED")
            return result

        # Clean up any stale ack file
        if os.path.exists(ACK_FILE):
            try:
                os.remove(ACK_FILE)
            except OSError:
                pass

        # Write the control command
        ctrl = {"action": "switch_capture", "file": new_file, "clear": True}
        try:
            os.makedirs(os.path.dirname(CONTROL_FILE) or ".", exist_ok=True)
            with open(CONTROL_FILE, "w", encoding="utf-8") as f:
                json.dump(ctrl, f)
        except Exception as e:
            self.handle_failure(command, result,
                                f"[switch_capture_file] Error: Failed to write control file - {e} | Result: FAILED")
            return result

        # Wait for the proxy to acknowledge (up to 10 seconds)
        timeout = command.timeout or 10
        waited = 0.0
        ack = None
        while waited < timeout:
            if os.path.isfile(ACK_FILE):
                try:
                    with open(ACK_FILE, "r", encoding="utf-8") as f:
                        ack = json.load(f)
                    os.remove(ACK_FILE)
                    break
                except Exception:
                    pass
            time.sleep(0.5)
            waited += 0.5

        if ack is None:
            # Clean up control file if proxy never read it
            if os.path.exists(CONTROL_FILE):
                try:
                    os.remove(CONTROL_FILE)
                except OSError:
                    pass
            self.handle_failure(command, result,
                                f"[switch_capture_file] Error: Proxy did not acknowledge within "
                                f"{timeout}s — is the background proxy running? | Result: FAILED")
            return result

        if ack.get("status") != "ok":
            self.handle_failure(command, result,
                                f"[switch_capture_file] Error: Proxy returned error - "
                                f"{ack.get('message', 'unknown')} | Result: FAILED")
            return result

        old_file = ack.get("old_file", "")
        result.data = {"old_file": old_file, "new_file": new_file}
        self.handle_success(
            command, result,
            f"[switch_capture_file] Switched: {old_file} -> {new_file} | Result: PASSED"
        )
        return result

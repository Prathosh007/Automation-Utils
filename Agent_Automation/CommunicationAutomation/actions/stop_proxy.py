import os
import signal
import subprocess
import sys
import time

from core.base_action import BaseAction
from core.models import Command, CommandResult


class StopProxyAction(BaseAction):
    """Stop the running mitmproxy instance (in-process or background)."""

    PID_FILE = os.path.join("Reports", "proxy.pid")

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        # Try in-process proxy first
        pm = context.get("proxy_manager")
        if pm and pm.is_running:
            pm.stop()
            self.handle_success(command, result,
                                "[stop_proxy] Action: stop in-process proxy | Result: PASSED")
            return result

        # Collect PID(s) to kill — from PID file and/or context
        pids_to_kill: list[int] = []

        if os.path.exists(self.PID_FILE):
            try:
                with open(self.PID_FILE) as f:
                    pid = int(f.read().strip())
                pids_to_kill.append(pid)
            except (ValueError, OSError) as e:
                if self.logger:
                    self.logger.log_to_file(f"Cannot read PID file: {e}")

        bg_pid = context.get("background_proxy_pid")
        if bg_pid and bg_pid not in pids_to_kill:
            pids_to_kill.append(bg_pid)

        if not pids_to_kill:
            self.handle_success(command, result,
                                "[stop_proxy] Proxy was not running - nothing to stop | Result: PASSED")
            return result

        # Kill every collected PID
        killed = []
        failed = []
        for pid in pids_to_kill:
            if self._kill_pid_robust(pid):
                killed.append(pid)
            else:
                failed.append(pid)

        # Last-resort: kill any remaining CommunicationAutomation.exe --serve
        # processes that the PID-based approach missed
        orphans = self._kill_orphan_serve_processes()

        self._cleanup_pid_file()

        if failed and not orphans:
            self.handle_failure(
                command, result,
                f"[stop_proxy] PID(s): {failed} | "
                f"Error: Failed to stop - process may still be running | Result: FAILED")
        else:
            parts = []
            if killed:
                parts.append(f"PID(s) {killed} stopped")
            if orphans:
                parts.append(f"{orphans} orphan --serve process(es) killed")
            self.handle_success(command, result,
                                f"[stop_proxy] {'; '.join(parts)} | Result: PASSED")
        return result

    # ------------------------------------------------------------------
    # Robust PID kill: multiple strategies
    # ------------------------------------------------------------------

    def _kill_pid_robust(self, pid: int) -> bool:
        """Try every available method to terminate *pid*. Returns True if dead."""
        current_pid = os.getpid()
        if pid == current_pid:
            if self.logger:
                self.logger.log_to_file(f"Skipping PID {pid} — it is the current process")
            return True  # Don't kill ourselves

        if not self._is_alive(pid):
            if self.logger:
                self.logger.log_to_file(f"PID {pid} is already dead")
            return True

        if self.logger:
            self.logger.log_to_file(f"Stopping background proxy PID {pid}")

        # Attempt 1: taskkill /F /T (force kill tree — most common Windows approach)
        if os.name == "nt":
            self._taskkill(pid, tree=True)
            time.sleep(1)
            if not self._is_alive(pid):
                return True

        # Attempt 2: taskkill /F without /T (single process, not tree)
        if os.name == "nt" and self._is_alive(pid):
            if self.logger:
                self.logger.log_to_file(f"Tree kill failed — trying single-process taskkill")
            self._taskkill(pid, tree=False)
            time.sleep(1)
            if not self._is_alive(pid):
                return True

        # Attempt 3: wmic process terminate (alternative Windows API path)
        if os.name == "nt" and self._is_alive(pid):
            if self.logger:
                self.logger.log_to_file(f"taskkill failed — trying wmic terminate")
            self._wmic_terminate(pid)
            time.sleep(1)
            if not self._is_alive(pid):
                return True

        # Attempt 4: os.kill SIGTERM (calls TerminateProcess on Windows)
        if self._is_alive(pid):
            try:
                os.kill(pid, signal.SIGTERM)
            except OSError:
                pass
            time.sleep(2)
            if not self._is_alive(pid):
                return True

        # Attempt 5: CTRL_BREAK_EVENT (for processes with CREATE_NEW_PROCESS_GROUP)
        if self._is_alive(pid) and hasattr(signal, "CTRL_BREAK_EVENT"):
            try:
                os.kill(pid, signal.CTRL_BREAK_EVENT)
            except OSError:
                pass
            time.sleep(2)
            if not self._is_alive(pid):
                return True

        # Attempt 6: final retry with taskkill
        if os.name == "nt" and self._is_alive(pid):
            if self.logger:
                self.logger.log_to_file(f"PID {pid} still alive — final taskkill retry")
            self._taskkill(pid, tree=True)
            time.sleep(2)

        alive = self._is_alive(pid)
        if self.logger:
            self.logger.log_to_file(
                f"PID {pid} after all kill attempts: {'STILL ALIVE' if alive else 'dead'}")
        return not alive

    # ------------------------------------------------------------------
    # Kill by image name — catches orphaned --serve processes
    # ------------------------------------------------------------------

    def _kill_orphan_serve_processes(self) -> int:
        """Kill CommunicationAutomation.exe processes running in --serve mode.

        Uses WMIC to find processes whose command-line contains ``--serve``
        and kills them (excluding the current process). Returns the count
        of processes killed.
        """
        if os.name != "nt":
            return 0

        current_pid = os.getpid()
        exe_name = os.path.basename(sys.executable)  # CommunicationAutomation.exe or python.exe

        try:
            # Find PIDs of all instances with --serve in command line
            r = subprocess.run(
                ["wmic", "process", "where",
                 f"Name='{exe_name}' AND CommandLine LIKE '%--serve%'",
                 "get", "ProcessId"],
                capture_output=True, text=True, timeout=10,
            )
            if r.returncode != 0:
                return 0

            killed = 0
            for line in r.stdout.splitlines():
                line = line.strip()
                if line.isdigit():
                    pid = int(line)
                    if pid == current_pid:
                        continue
                    if self.logger:
                        self.logger.log_to_file(
                            f"Killing orphan --serve process: PID {pid}")
                    self._taskkill(pid, tree=True)
                    time.sleep(0.5)
                    if self._is_alive(pid):
                        self._wmic_terminate(pid)
                    killed += 1
            return killed
        except Exception as e:
            if self.logger:
                self.logger.log_to_file(f"Orphan scan failed: {e}")
            return 0

    # ------------------------------------------------------------------
    # Low-level helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _is_alive(pid: int) -> bool:
        """Check if a process is still running (Windows-aware)."""
        if os.name == "nt":
            try:
                r = subprocess.run(
                    ["tasklist", "/FI", f"PID eq {pid}", "/NH"],
                    capture_output=True, text=True, timeout=5,
                )
                # tasklist prints the process info if it exists,
                # or "INFO: No tasks are running..." if it doesn't
                return str(pid) in r.stdout
            except Exception:
                pass
        # Fallback: os.kill signal 0
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False

    @staticmethod
    def _taskkill(pid: int, tree: bool = True) -> bool:
        """Use taskkill to kill a process. Returns True on success."""
        try:
            cmd = ["taskkill", "/F"]
            if tree:
                cmd.append("/T")
            cmd.extend(["/PID", str(pid)])
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            return r.returncode == 0
        except Exception:
            return False

    @staticmethod
    def _wmic_terminate(pid: int) -> bool:
        """Use wmic to terminate a process. Returns True on success."""
        try:
            r = subprocess.run(
                ["wmic", "process", "where", f"ProcessId={pid}", "call", "terminate"],
                capture_output=True, text=True, timeout=10,
            )
            return r.returncode == 0
        except Exception:
            return False

    def _cleanup_pid_file(self) -> None:
        try:
            os.remove(self.PID_FILE)
        except OSError:
            pass

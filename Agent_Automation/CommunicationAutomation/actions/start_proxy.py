import os
import re
import socket
import subprocess
import sys
import time

from core.base_action import BaseAction
from core.models import Command, CommandResult


class StartProxyAction(BaseAction):
    """Start mitmproxy as a background process that survives exe exit.

    Spawns a detached child process running in ``--serve`` mode.
    Traffic is written to a live capture JSON file in real-time.
    Use ``stop_proxy`` to terminate the background proxy later.

    If the mode URL contains a hostname (e.g. ``reverse:https://host:8383``),
    it is automatically resolved to an IP address before starting the proxy.
    This prevents 502 errors when ``traffic_lock`` later adds the same
    hostname to the hosts file pointing to 127.0.0.1.
    """

    PID_FILE = os.path.join("Reports", "proxy.pid")

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and command.listen_port > 0

    @staticmethod
    def _resolve_mode_hostname(mode: str) -> str:
        """Resolve the hostname in a mode URL to an IP address.

        ``reverse:https://myhost:8383`` → ``reverse:https://10.1.2.3:8383``

        Returns the mode string unchanged if it already uses an IP,
        is empty, or resolution fails.
        """
        if not mode:
            return mode
        m = re.search(r"(://)([\w.\-]+)(:\d+)", mode)
        if not m:
            return mode
        host = m.group(2)
        # Already an IP — nothing to do
        if re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", host):
            return mode
        try:
            ip = socket.gethostbyname(host)
            return mode[:m.start(2)] + ip + mode[m.end(2):]
        except socket.gaierror:
            return mode  # can't resolve — use as-is

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        # Check if a background proxy is already running
        if os.path.exists(self.PID_FILE):
            try:
                with open(self.PID_FILE) as f:
                    old_pid = int(f.read().strip())
                os.kill(old_pid, 0)  # Check if alive
                self.handle_failure(command, result,
                                    f"[start_proxy] Error: Background proxy already running (PID {old_pid}) - "
                                    "stop it first with stop_proxy | Result: FAILED")
                return result
            except (OSError, ValueError):
                try:
                    os.remove(self.PID_FILE)
                except OSError:
                    pass

        # Determine the executable path
        exe = sys.executable

        # Resolve hostname in mode URL to IP so the proxy is immune to
        # hosts-file changes made later by traffic_lock
        original_mode = command.mode or "regular"
        resolved_mode = self._resolve_mode_hostname(original_mode)
        if resolved_mode != original_mode and self.logger:
            self.logger.log_to_file(
                f"Resolved mode hostname: {original_mode} -> {resolved_mode}")

        # Build --serve command
        args = [
            exe, "--serve",
            command.listen_host,
            str(command.listen_port),
            resolved_mode,
        ]
        if command.ssl_insecure:
            args.append("--ssl-insecure")
        if command.auto_load_cert:
            args.append("--auto-load-cert")

        live_file = command.live_capture_file or "Reports/live-capture.json"
        args.extend(["--live-capture", live_file])

        if self.logger:
            self.logger.log_to_file(f"Spawning background proxy: {' '.join(args)}")

        # Spawn detached process — stdout/stderr go to proxy.log via logging in --serve mode
        try:
            creation_flags = (
                subprocess.CREATE_NEW_PROCESS_GROUP
                | subprocess.DETACHED_PROCESS
                | subprocess.CREATE_NO_WINDOW
            )
            log_path = os.path.join("Reports", "proxy_stdout.log")
            os.makedirs("Reports", exist_ok=True)
            log_fh = open(log_path, "w")
            proc = subprocess.Popen(
                args,
                stdout=log_fh,
                stderr=log_fh,
                stdin=subprocess.DEVNULL,
                creationflags=creation_flags,
                close_fds=True,   # Don't inherit parent handles (DLLs in _MEIPASS etc.)
                cwd=os.getcwd(),
            )
            log_fh.close()  # Parent doesn't need this handle anymore
        except Exception as e:
            self.handle_failure(command, result,
                                f"[start_proxy] Error: Failed to spawn background proxy - {e} | Result: FAILED")
            return result

        # Wait briefly for process to start and write PID file
        time.sleep(3)
        if proc.poll() is not None:
            # Process died — read proxy.log for the real error
            err_detail = self._read_proxy_log_errors()
            self.handle_failure(command, result,
                                f"[start_proxy] Error: Background proxy exited immediately "
                                f"(code {proc.returncode}){err_detail} | Result: FAILED")
            return result

        # Verify the proxy is actually listening on the port
        if not self._wait_for_port(command.listen_host, command.listen_port,
                                   timeout=12, pid=proc.pid):
            err_detail = self._read_proxy_log_errors()
            # Kill the zombie process
            try:
                proc.kill()
            except OSError:
                pass
            self.handle_failure(
                command, result,
                f"[start_proxy] Error: Proxy process alive (PID {proc.pid}) but "
                f"port {command.listen_host}:{command.listen_port} is NOT listening. "
                f"Likely a port conflict or permission issue.{err_detail} | Result: FAILED")
            return result

        context["background_proxy_pid"] = proc.pid
        result.data = {"pid": proc.pid, "live_capture_file": live_file}

        mode_desc = f" [{command.mode}]" if command.mode else ""
        auto_desc = " (auto cert load on CSR)" if command.auto_load_cert else ""
        self.handle_success(
            command, result,
            f"[start_proxy] Host: {command.listen_host}:{command.listen_port}{mode_desc}{auto_desc} | "
            f"PID: {proc.pid} | Capture file: {live_file} | Result: PASSED"
        )
        return result

    def _wait_for_port(self, host: str, port: int, timeout: int = 12,
                       pid: int = 0) -> bool:
        """Poll until *host:port* accepts a TCP connection or timeout."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            # If the process died while we wait, bail early
            if pid:
                try:
                    os.kill(pid, 0)
                except OSError:
                    return False
            try:
                s = socket.create_connection((host, port), timeout=1)
                s.close()
                return True
            except (ConnectionRefusedError, OSError):
                time.sleep(1)
        return False

    @staticmethod
    def _read_proxy_log_errors() -> str:
        """Read proxy.log and extract ERROR lines for diagnostics."""
        log_path = os.path.join("Reports", "proxy.log")
        try:
            with open(log_path, "r", encoding="utf-8") as f:
                lines = f.readlines()
            errors = [ln.strip() for ln in lines if "ERROR" in ln]
            if errors:
                # Return last 3 error lines
                snippet = "; ".join(errors[-3:])
                return f" — proxy.log: {snippet[:500]}"
        except Exception:
            pass
        return ""

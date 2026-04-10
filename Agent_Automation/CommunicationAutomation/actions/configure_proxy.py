import subprocess

from core.base_action import BaseAction
from core.models import Command, CommandResult


class ConfigureProxyAction(BaseAction):
    """Configure system proxy settings via netsh or registry."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and command.proxy_action in ("enable", "disable")

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        if command.proxy_action == "enable":
            ok, msg = self._enable_system_proxy(
                command.proxy_host or "127.0.0.1",
                command.proxy_port or 8080,
                command.bypass_list,
            )
        else:
            ok, msg = self._disable_system_proxy()

        if ok:
            self.handle_success(command, result,
                                f"[configure_proxy] Action: {command.proxy_action} | {msg} | Result: PASSED")
        else:
            self.handle_failure(command, result,
                                f"[configure_proxy] Action: {command.proxy_action} | {msg} | Result: FAILED")
        return result

    @staticmethod
    def _enable_system_proxy(host: str, port: int, bypass: str) -> tuple[bool, str]:
        """Enable Windows system proxy using netsh."""
        proxy_addr = f"{host}:{port}"
        try:
            # Set proxy via netsh winhttp
            cmd = ["netsh", "winhttp", "set", "proxy", proxy_addr]
            if bypass:
                cmd.extend(["bypass-list", bypass])
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if r.returncode == 0:
                return True, f"System proxy enabled: {proxy_addr}"
            return False, f"netsh failed: {r.stderr.strip()}"
        except Exception as e:
            return False, f"Failed to enable system proxy: {e}"

    @staticmethod
    def _disable_system_proxy() -> tuple[bool, str]:
        """Disable Windows system proxy."""
        try:
            r = subprocess.run(
                ["netsh", "winhttp", "reset", "proxy"],
                capture_output=True, text=True, timeout=15
            )
            if r.returncode == 0:
                return True, "System proxy disabled"
            return False, f"netsh failed: {r.stderr.strip()}"
        except Exception as e:
            return False, f"Failed to disable system proxy: {e}"

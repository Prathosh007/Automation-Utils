import subprocess
import time

from core.base_action import BaseAction
from core.models import Command, CommandResult


class ManageServiceAction(BaseAction):
    """Start, stop, or restart a Windows service.

    If the exact service_name is not found, auto-discovers by searching
    for services matching UEMS/ManageEngine/Agent patterns.
    """

    def validate_command(self, command: Command) -> bool:
        return (super().validate_command(command)
                and bool(command.service_name)
                and command.service_action in ("start", "stop", "restart", "status", "find"))

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)
        action = command.service_action.lower()

        # Resolve service name (auto-discover if needed)
        svc = command.service_name
        if not self._service_exists(svc):
            if self.logger:
                self.logger.log_to_file(f"Service '{svc}' not found, searching...")
            found = self._find_service(svc)
            if found:
                if self.logger:
                    self.logger.log_to_file(f"Auto-resolved: '{svc}' -> '{found}'")
                svc = found
                context["resolved_service_name"] = svc
            else:
                # Use previously resolved name if available
                prev = context.get("resolved_service_name")
                if prev:
                    svc = prev
                elif action == "find":
                    self.handle_failure(command, result,
                                        f"[manage_service] Service: '{svc}' | "
                                        f"Error: No matching service found | Result: FAILED")
                    return result

        if action == "find":
            result.data = svc
            self.handle_success(command, result,
                                f"[manage_service] Action: find | Service found: '{svc}' | Result: PASSED")
            return result
        elif action == "status":
            ok, msg = self._get_status(svc)
        elif action == "stop":
            ok, msg = self._stop(svc)
        elif action == "start":
            ok, msg = self._start(svc)
        elif action == "restart":
            ok, msg = self._stop(svc)
            if ok:
                time.sleep(2)
                ok, msg = self._start(svc)
        else:
            ok, msg = False, f"Unknown service action: {action}"

        if ok:
            self.handle_success(command, result,
                                f"[manage_service] Service: '{svc}' | Action: {action} | {msg} | Result: PASSED")
        else:
            self.handle_failure(command, result,
                                f"[manage_service] Service: '{svc}' | Action: {action} | {msg} | Result: FAILED")
        return result

    @staticmethod
    def _run_sc(args: list) -> tuple[bool, str]:
        try:
            r = subprocess.run(
                ["sc"] + args,
                capture_output=True, text=True, timeout=30
            )
            output = (r.stdout + r.stderr).strip()
            return r.returncode == 0, output
        except Exception as e:
            return False, str(e)

    def _service_exists(self, svc: str) -> bool:
        ok, _ = self._run_sc(["query", svc])
        return ok

    def _find_service(self, hint: str) -> str:
        """Search for a service by keyword. Returns the service name or empty string."""
        # Get all services and search by keyword
        try:
            r = subprocess.run(
                ["sc", "query", "type=", "service", "state=", "all"],
                capture_output=True, text=True, timeout=30
            )
            if r.returncode != 0:
                return ""

            # Parse service names from sc query output
            services = []
            for line in r.stdout.splitlines():
                line = line.strip()
                if line.startswith("SERVICE_NAME:"):
                    svc_name = line.split(":", 1)[1].strip()
                    services.append(svc_name)

            if self.logger:
                self.logger.log_to_file(f"Total services found: {len(services)}")

            # Search by hint keywords
            keywords = hint.lower().replace("_", " ").split()
            # Also add common UEMS keywords
            extra_keywords = ["uems", "manageengine", "desktopcentral", "dcagent", "zoho"]

            # First pass: exact keywords from hint
            for svc in services:
                svc_lower = svc.lower()
                if all(kw in svc_lower for kw in keywords):
                    if self.logger:
                        self.logger.log_to_file(f"Matched by hint keywords: {svc}")
                    return svc

            # Second pass: common UEMS patterns
            matches = []
            for svc in services:
                svc_lower = svc.lower()
                for kw in extra_keywords:
                    if kw in svc_lower:
                        matches.append(svc)
                        break

            if self.logger and matches:
                self.logger.log_to_file(f"UEMS-related services found: {matches}")

            if len(matches) == 1:
                return matches[0]
            elif len(matches) > 1:
                # Prefer one with "agent" in name
                for m in matches:
                    if "agent" in m.lower():
                        return m
                return matches[0]

            return ""
        except Exception as e:
            if self.logger:
                self.logger.log_to_file(f"Service search error: {e}")
            return ""

    def _stop(self, svc: str) -> tuple[bool, str]:
        ok, out = self._run_sc(["stop", svc])
        if ok or "STOPPED" in out or "1062" in out or "not been started" in out.lower():
            for _ in range(10):
                _, status = self._run_sc(["query", svc])
                if "STOPPED" in status:
                    return True, f"Service '{svc}' stopped"
                time.sleep(1)
            return True, f"Service '{svc}' already stopped"
        return False, f"Failed to stop '{svc}': {out}"

    def _start(self, svc: str) -> tuple[bool, str]:
        ok, out = self._run_sc(["start", svc])
        if ok or "RUNNING" in out or "START_PENDING" in out:
            for _ in range(15):
                _, status = self._run_sc(["query", svc])
                if "RUNNING" in status:
                    return True, f"Service '{svc}' started"
                time.sleep(1)
            return True, f"Service '{svc}' start command sent"
        return False, f"Failed to start '{svc}': {out}"

    def _get_status(self, svc: str) -> tuple[bool, str]:
        ok, out = self._run_sc(["query", svc])
        if ok:
            state = "UNKNOWN"
            for line in out.splitlines():
                if "STATE" in line:
                    state = line.strip()
                    break
            return True, f"Service '{svc}' status: {state}"
        return False, f"Could not query '{svc}': {out}"

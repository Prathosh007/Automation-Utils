"""traffic_lock / traffic_unlock — OS-level traffic redirection through a proxy.

Lock pipeline:
  0. Ensure Windows Firewall is ON
  1. Hosts file:  append ``127.0.0.1 <hostname>``
  2. Port proxy:  ``netsh interface portproxy add v4tov4``
     listenport=<server_port> → connectport=<proxy_port>
  3. Response rewrite:  persistent ``modify_response`` rule that replaces
     server_ip and hostname with ``127.0.0.1`` in every response body,
     preventing the agent from discovering the real server address.
  4. Firewall (best-effort):  per-exe outbound-block rules.
     May be overridden by endpoint security products (e.g. CrowdStrike).
  5. Flush DNS

Unlock pipeline reverses each step.

JSON examples::

    Lock (with firewall — existing install):
    {"id": "SETUP_004", "action": "traffic_lock",
     "hostname": "prathosh-w22-11", "server_ip": "172.24.148.221",
     "server_port": 8383, "proxy_port": 8080,
     "firewall_block_folder": "C:\\\\Program Files (x86)\\\\ManageEngine\\\\UEMS_Agent",
     "description": "OS-level traffic lock"}

    Lock (without firewall — fresh install):
    {"id": "SETUP_004", "action": "traffic_lock",
     "hostname": "prathosh-w22-11", "server_ip": "172.24.148.221",
     "server_port": 8383, "proxy_port": 8080,
     "description": "OS-level traffic lock (fresh install)"}

    Unlock:
    {"id": "SETUP_005", "action": "traffic_unlock",
     "server_port": 8383,
     "description": "OS-level traffic unlock"}
"""

import glob
import json
import os
import subprocess
import time

from core.base_action import BaseAction
from core.models import Command, CommandResult

_HOSTS_PATH = r"C:\Windows\System32\drivers\etc\hosts"
_HOSTS_MARKER = "# UEMS_TRAFFIC_LOCK"
_FW_PREFIX = "BlockUEMS_"


class TrafficLockAction(BaseAction):
    """Redirect agent traffic through the proxy.

    Required fields:
        hostname              real server hostname
        server_ip             real server IP
        server_port           real server port (e.g. 8383)
        proxy_port            proxy listen port (e.g. 8080)

    Optional fields:
        firewall_block_folder folder to enumerate .exe files for per-exe
                              firewall block rules.  If omitted or the folder
                              is empty (e.g. fresh install), the firewall step
                              is skipped and only hosts + portproxy are used.
    """

    def validate_command(self, command: Command) -> bool:
        return (super().validate_command(command)
                and bool(command.hostname)
                and bool(command.server_ip)
                and command.server_port > 0
                and command.proxy_port > 0)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        hostname = command.hostname
        server_ip = command.server_ip
        server_port = command.server_port
        proxy_port = command.proxy_port
        block_folder = command.firewall_block_folder

        errors: list[str] = []
        steps: list[str] = []

        if self.logger:
            self.logger.log_to_file(
                f"  [traffic_lock] === LOCK START ===")
            self.logger.log_to_file(
                f"  [traffic_lock] hostname={hostname}")
            self.logger.log_to_file(
                f"  [traffic_lock] server_ip={server_ip}")
            self.logger.log_to_file(
                f"  [traffic_lock] server_port={server_port}")
            self.logger.log_to_file(
                f"  [traffic_lock] proxy_port={proxy_port}")
            self.logger.log_to_file(
                f"  [traffic_lock] firewall_block_folder={block_folder}")

        # 0a. Prerequisites — verify proxy_port is actually listening
        if not self._check_port_listening(proxy_port):
            self.handle_failure(
                command, result,
                f"[traffic_lock] ABORTED: proxy port {proxy_port} is NOT "
                f"listening. Is start_proxy running? Check Reports/proxy.log")
            return result

        # 0b. Prerequisites — IP Helper (iphlpsvc) must run for portproxy
        self._ensure_ip_helper()

        # 0c. Prerequisites — Ensure Windows Firewall is ON
        self._ensure_firewall_on()

        # 1. Hosts file — redirect hostname to localhost
        ok, msg = self._add_hosts_entry(hostname)
        if ok:
            steps.append(f"hosts(127.0.0.1 {hostname})")
        else:
            errors.append(f"hosts: {msg}")

        # 2. Port proxy — server_port on localhost → proxy_port
        ok, msg = self._add_port_proxy(server_port, proxy_port)
        if ok:
            steps.append(f"portproxy({server_port}->127.0.0.1:{proxy_port})")
        else:
            errors.append(f"portproxy: {msg}")

        # 2b. Verify port proxy is actually listening
        self._verify_port_listening(server_port)

        # 3. Response rewrite — replace server_ip/hostname with 127.0.0.1
        #    in all response bodies so the agent never discovers the real
        #    server address.  This is the primary traffic-lock mechanism.
        ok, msg = self._add_response_rewrite(context, server_ip, hostname)
        if ok:
            steps.append(f"response_rewrite({msg})")
        else:
            errors.append(f"response_rewrite: {msg}")

        # 4. Firewall — per-exe blocking (best-effort, may be overridden
        #    by endpoint security products like CrowdStrike Falcon)
        if block_folder:
            ok, msg = self._add_firewall_rules(block_folder, server_ip,
                                               server_port)
            if ok:
                steps.append(f"firewall({msg})")
            else:
                # Always non-fatal — firewall is best-effort
                steps.append(f"firewall(best-effort: {msg})")
                if self.logger:
                    self.logger.log_to_file(
                        f"  [Firewall] Best-effort (non-fatal): {msg}")
        else:
            steps.append("firewall(skipped: no folder specified)")
            if self.logger:
                self.logger.log_to_file(
                    "  [Firewall] Skipped — no firewall_block_folder "
                    "specified (fresh install mode)")

        # 5. Flush DNS
        self._flush_dns()
        steps.append("dns_flush")

        # 6. Restart agent services to clear in-process DNS cache
        restarted = self._restart_agent_services(block_folder)
        if restarted:
            steps.append(f"agent_restart({restarted})")

        # Store in context for traffic_unlock
        context["traffic_lock"] = {
            "hostname": hostname,
            "server_ip": server_ip,
            "server_port": server_port,
            "proxy_port": proxy_port,
            "firewall_block_folder": block_folder,
        }

        result.data = {"steps": steps, "errors": errors}

        if errors:
            self.handle_failure(
                command, result,
                f"[traffic_lock] Steps: {', '.join(steps)} | "
                f"Errors: {'; '.join(errors)}")
        else:
            self.handle_success(
                command, result,
                f"[traffic_lock] {', '.join(steps)}")
        return result

    # ── prerequisites ───────────────────────────────────────────────────

    @staticmethod
    def _check_port_listening(port: int) -> bool:
        """Return True if something is accepting TCP on 127.0.0.1:port."""
        import socket
        try:
            s = socket.create_connection(("127.0.0.1", port), timeout=3)
            s.close()
            return True
        except (ConnectionRefusedError, OSError):
            return False

    def _ensure_ip_helper(self) -> None:
        """Start IP Helper — required for netsh interface portproxy."""
        self._run("sc config iphlpsvc start= auto")
        ok, msg = self._run("net start iphlpsvc")
        if self.logger:
            if ok or "already been started" in msg.lower():
                self.logger.log_to_file(
                    "  [Prerequisites] IP Helper service is running")
            else:
                self.logger.log_to_file(
                    f"  [Prerequisites] WARNING: IP Helper: {msg}")

    def _ensure_firewall_on(self) -> None:
        """Ensure Windows Firewall is active on all profiles."""
        ok, msg = self._run(
            "netsh advfirewall set allprofiles state on")
        if self.logger:
            if ok:
                self.logger.log_to_file(
                    "  [Prerequisites] Windows Firewall enabled "
                    "on all profiles")
            else:
                self.logger.log_to_file(
                    f"  [Prerequisites] WARNING: Could not enable "
                    f"firewall: {msg}")
        # Log current state for verification
        self._run("netsh advfirewall show allprofiles state")

    def _verify_port_listening(self, port: int) -> None:
        """Check that the port proxy is really accepting connections."""
        if not self.logger:
            return
        self._run("netsh interface portproxy show all")
        ok, out = self._run(
            f'netstat -an | findstr ":{port}" | findstr LISTENING')
        if ok and str(port) in out:
            self.logger.log_to_file(
                f"  [PortProxy] VERIFIED: port {port} is LISTENING")
        else:
            self.logger.log_to_file(
                f"  [PortProxy] WARNING: port {port} NOT in LISTENING "
                f"state. IP Helper running? IPv6 enabled?")
            self._run("sc query iphlpsvc")

    def _restart_agent_services(self, block_folder: str) -> str:
        """Restart UEMS agent services so they re-resolve DNS."""
        # Build a wmic filter — prefer folder path, fall back to UEMS name
        if block_folder:
            folder_key = os.path.basename(block_folder)
            for ch in ['"', "'", ";", "&", "|", "`"]:
                folder_key = folder_key.replace(ch, "")
            where = (f"State='Running' and "
                     f"PathName like '%{folder_key}%'")
        else:
            # Fresh install mode — search by well-known service name
            where = ("State='Running' and "
                     "(Name like '%UEMS%' or Name like '%ManageEngine%')")
        ok, output = self._run(
            f'wmic service where "{where}" get Name /value')
        if not ok or not output.strip():
            if self.logger:
                self.logger.log_to_file(
                    "  [AgentRestart] No running agent services found")
            return ""
        services = []
        for line in output.splitlines():
            line = line.strip()
            if line.lower().startswith("name="):
                svc = line.split("=", 1)[1].strip()
                if svc:
                    services.append(svc)
        if not services:
            return ""
        restarted = []
        for svc in services:
            if self.logger:
                self.logger.log_to_file(
                    f"  [AgentRestart] Stopping service: {svc}")
            self._run(f'net stop "{svc}" /y')
            time.sleep(2)
            if self.logger:
                self.logger.log_to_file(
                    f"  [AgentRestart] Starting service: {svc}")
            ok, _ = self._run(f'net start "{svc}"')
            if ok:
                restarted.append(svc)
            else:
                if self.logger:
                    self.logger.log_to_file(
                        f"  [AgentRestart] WARNING: Failed to start {svc}")
        return ", ".join(restarted) if restarted else ""

    # ── hosts file ──────────────────────────────────────────────────────

    def _add_hosts_entry(self, hostname: str) -> tuple[bool, str]:
        entry = f"127.0.0.1 {hostname}"
        try:
            with open(_HOSTS_PATH, "r", encoding="utf-8") as f:
                content = f.read()
            if entry in content:
                if self.logger:
                    self.logger.log_to_file(
                        f"  Hosts entry already present: {entry}")
                return True, "already present"
            content += f"\n{entry}  {_HOSTS_MARKER}\n"
            with open(_HOSTS_PATH, "w", encoding="utf-8") as f:
                f.write(content)
            if self.logger:
                self.logger.log_to_file(f"  Added hosts entry: {entry}")
            return True, "added"
        except Exception as e:
            return False, str(e)

    # ── port proxy ──────────────────────────────────────────────────────

    def _add_port_proxy(self, listen_port: int,
                        connect_port: int) -> tuple[bool, str]:
        cmd = (f"netsh interface portproxy add v4tov4 "
               f"listenport={listen_port} listenaddress=127.0.0.1 "
               f"connectport={connect_port} connectaddress=127.0.0.1")
        return self._run(cmd)

    # ── response rewrite ─────────────────────────────────────────────────

    def _add_response_rewrite(self, context: dict, server_ip: str,
                              hostname: str) -> tuple[bool, str]:
        """Add a persistent response rule that replaces server_ip/hostname
        with 127.0.0.1 in all response bodies.

        Sends the request to the background proxy via file-based IPC
        (proxy-control.json), since the proxy runs in a separate process
        with its own rule_engine.
        """
        replacements: dict[str, str] = {}
        if server_ip:
            replacements[server_ip] = "127.0.0.1"
        if hostname and hostname != server_ip:
            replacements[hostname] = "127.0.0.1"

        if not replacements:
            return False, "no server_ip or hostname to replace"

        # Store replacements in context for unlock
        context.setdefault("traffic_lock", {})["_rewrite_replacements"] = replacements

        # Send to background proxy via file-based IPC
        control_file = os.path.join("Reports", "proxy-control.json")
        ack_file = os.path.join("Reports", "proxy-control-ack.json")

        # Clean stale ack
        if os.path.exists(ack_file):
            try:
                os.remove(ack_file)
            except OSError:
                pass

        ctrl = {"action": "add_body_replacements", "replacements": replacements}
        try:
            os.makedirs("Reports", exist_ok=True)
            with open(control_file, "w", encoding="utf-8") as f:
                json.dump(ctrl, f)
        except Exception as e:
            return False, f"failed to write control file: {e}"

        # Wait for ack (up to 10 seconds)
        waited = 0.0
        ack = None
        while waited < 10:
            if os.path.isfile(ack_file):
                try:
                    with open(ack_file, "r", encoding="utf-8") as f:
                        ack = json.load(f)
                    os.remove(ack_file)
                    break
                except Exception:
                    pass
            time.sleep(0.5)
            waited += 0.5

        if ack is None:
            if os.path.exists(control_file):
                try:
                    os.remove(control_file)
                except OSError:
                    pass
            return False, "proxy did not acknowledge — is background proxy running?"

        if ack.get("status") != "ok":
            return False, ack.get("message", "unknown error")

        targets = ", ".join(f"{k}→127.0.0.1" for k in replacements)
        if self.logger:
            self.logger.log_to_file(
                f"  [ResponseRewrite] Added persistent rule via IPC: {targets}")
        return True, targets

    # ── firewall ────────────────────────────────────────────────────────

    def _add_firewall_rules(self, folder: str, remote_ip: str,
                            remote_port: int) -> tuple[bool, str]:
        if not os.path.isdir(folder):
            return False, f"folder not found: {folder}"

        exes = sorted(glob.glob(os.path.join(folder, "**", "*.exe"),
                                recursive=True))
        if not exes:
            return False, f"no .exe files in {folder}"

        if self.logger:
            self.logger.log_to_file(
                f"  [Firewall] Scanning folder: {folder}")
            self.logger.log_to_file(
                f"  [Firewall] Found {len(exes)} exe(s):")
            for exe in exes:
                self.logger.log_to_file(f"    - {exe}")

        created: list[str] = []
        failed: list[str] = []
        for exe in exes:
            basename = os.path.basename(exe)
            rule_name = f"{_FW_PREFIX}{basename}"
            cmd = (f'netsh advfirewall firewall add rule name="{rule_name}" '
                   f"dir=out action=block protocol=TCP "
                   f"remoteip={remote_ip} remoteport={remote_port} "
                   f'program="{exe}" enable=yes profile=any')
            ok, msg = self._run(cmd)
            if ok:
                created.append(basename)
                if self.logger:
                    self.logger.log_to_file(
                        f"  [Firewall] CREATED rule '{rule_name}' "
                        f"-> block {remote_ip}:{remote_port} "
                        f"for {exe}")
            else:
                failed.append(f"{basename}: {msg}")
                if self.logger:
                    self.logger.log_to_file(
                        f"  [Firewall] FAILED rule '{rule_name}' "
                        f"for {exe}: {msg}")

        if self.logger:
            self.logger.log_to_file(
                f"  [Firewall] Summary: created {len(created)}, "
                f"failed {len(failed)}")

        # Verify rules actually exist in the firewall
        self._verify_firewall_rules(remote_ip, remote_port)

        if failed:
            return (False,
                    f"created {len(created)}, failed {len(failed)}: "
                    f"{'; '.join(failed)}")
        return True, f"blocked {len(created)} exe(s)"

    def _verify_firewall_rules(self, remote_ip: str,
                               remote_port: int) -> None:
        """Query the firewall and log all BlockUEMS_* rules for verification."""
        if not self.logger:
            return
        self.logger.log_to_file(
            f"  [Firewall] Verifying rules (BlockUEMS_*) ...")
        ok, output = self._run(
            f'netsh advfirewall firewall show rule name=all dir=out '
            f'| findstr /i /c:"Rule Name" /c:"RemoteIP" '
            f'/c:"RemotePort" /c:"Program" /c:"Action" '
            f'/c:"Enabled" /c:"{_FW_PREFIX}"')
        if not ok:
            self.logger.log_to_file(
                "  [Firewall] Could not query firewall rules")
            return

        # Parse and log only BlockUEMS_* rule blocks
        current_rule: list[str] = []
        found = 0
        for line in output.splitlines():
            line = line.strip()
            if line.lower().startswith("rule name:"):
                # Flush previous block if it was a BlockUEMS_ rule
                if current_rule and any(_FW_PREFIX in ln for ln in current_rule):
                    for ln in current_rule:
                        self.logger.log_to_file(f"    {ln}")
                    found += 1
                current_rule = [line]
            elif current_rule:
                current_rule.append(line)
        # Flush last block
        if current_rule and any(_FW_PREFIX in ln for ln in current_rule):
            for ln in current_rule:
                self.logger.log_to_file(f"    {ln}")
            found += 1

        self.logger.log_to_file(
            f"  [Firewall] Verified {found} BlockUEMS_* rule(s) active")

    # ── DNS flush ───────────────────────────────────────────────────────

    def _flush_dns(self) -> None:
        try:
            subprocess.run("ipconfig /flushdns", shell=True,
                           capture_output=True, timeout=15)
            if self.logger:
                self.logger.log_to_file("  Flushed DNS cache")
        except Exception:
            pass

    # ── shell runner ────────────────────────────────────────────────────

    def _run(self, cmd: str) -> tuple[bool, str]:
        try:
            r = subprocess.run(cmd, shell=True, capture_output=True,
                               text=True, timeout=30)
            output = (r.stdout + r.stderr).strip()
            if self.logger:
                self.logger.log_to_file(f"  CMD: {cmd}")
                self.logger.log_to_file(
                    f"  Exit={r.returncode}  Output={output[:200]}")
            if r.returncode == 0:
                return True, output or "OK"
            return False, f"exit {r.returncode}: {output[:200]}"
        except subprocess.TimeoutExpired:
            return False, "Timed out"
        except Exception as e:
            return False, str(e)


class TrafficUnlockAction(BaseAction):
    """Undo everything traffic_lock did.

    Required fields (or pulled from stored context):
        server_port           port to remove from port proxy
        firewall_block_folder folder to enumerate .exe for firewall rule cleanup
    """

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        stored = context.get("traffic_lock", {})
        server_port = command.server_port or stored.get("server_port", 0)
        block_folder = (command.firewall_block_folder
                        or stored.get("firewall_block_folder", ""))

        errors: list[str] = []
        steps: list[str] = []

        # 1. Remove hosts entries
        ok, msg = self._remove_hosts_entries()
        if ok:
            steps.append(f"hosts({msg})")
        else:
            errors.append(f"hosts: {msg}")

        # 2. Remove port proxy
        if server_port:
            ok, msg = self._remove_port_proxy(server_port)
            if ok:
                steps.append(f"portproxy({server_port})")
            else:
                low = msg.lower()
                if ("not found" in low or "element" in low
                        or "cannot find the file" in low):
                    steps.append(f"portproxy({server_port}: already clean)")
                else:
                    errors.append(f"portproxy: {msg}")

        # 3. Remove response rewrite rules
        ok, msg = self._remove_response_rewrite(context)
        if ok:
            steps.append(f"response_rewrite({msg})")
        else:
            steps.append(f"response_rewrite(skipped: {msg})")

        # 4. Delete firewall rules
        ok, msg = self._remove_firewall_rules(block_folder)
        if ok:
            steps.append(f"firewall({msg})")
        else:
            errors.append(f"firewall: {msg}")

        # 5. Flush DNS
        self._flush_dns()
        steps.append("dns_flush")

        context.pop("traffic_lock", None)

        result.data = {"steps": steps, "errors": errors}

        if errors:
            self.handle_failure(
                command, result,
                f"[traffic_unlock] Steps: {', '.join(steps)} | "
                f"Errors: {'; '.join(errors)}")
        else:
            self.handle_success(
                command, result,
                f"[traffic_unlock] {', '.join(steps)}")
        return result

    # ── hosts file ──────────────────────────────────────────────────────

    def _remove_hosts_entries(self) -> tuple[bool, str]:
        try:
            with open(_HOSTS_PATH, "r", encoding="utf-8") as f:
                lines = f.readlines()
            cleaned = [ln for ln in lines if _HOSTS_MARKER not in ln]
            removed = len(lines) - len(cleaned)
            with open(_HOSTS_PATH, "w", encoding="utf-8") as f:
                f.writelines(cleaned)
            if self.logger:
                self.logger.log_to_file(
                    f"  Removed {removed} hosts entry(ies)")
            return True, f"removed {removed}"
        except Exception as e:
            return False, str(e)

    # ── response rewrite ─────────────────────────────────────────────────

    def _remove_response_rewrite(self, context: dict) -> tuple[bool, str]:
        """Remove all body-replacement rules from the background proxy via IPC."""
        control_file = os.path.join("Reports", "proxy-control.json")
        ack_file = os.path.join("Reports", "proxy-control-ack.json")

        # Clean stale ack
        if os.path.exists(ack_file):
            try:
                os.remove(ack_file)
            except OSError:
                pass

        ctrl = {"action": "clear_body_replacements"}
        try:
            os.makedirs("Reports", exist_ok=True)
            with open(control_file, "w", encoding="utf-8") as f:
                json.dump(ctrl, f)
        except Exception as e:
            return False, f"failed to write control file: {e}"

        # Wait for ack (up to 10 seconds)
        waited = 0.0
        ack = None
        while waited < 10:
            if os.path.isfile(ack_file):
                try:
                    with open(ack_file, "r", encoding="utf-8") as f:
                        ack = json.load(f)
                    os.remove(ack_file)
                    break
                except Exception:
                    pass
            time.sleep(0.5)
            waited += 0.5

        if ack is None:
            # Proxy might already be stopped — not an error during unlock
            return True, "no ack (proxy may be stopped)"

        if ack.get("status") != "ok":
            return False, ack.get("message", "unknown error")

        count = ack.get("cleared", 0)
        return True, f"cleared {count} rule(s)"

    # ── port proxy ──────────────────────────────────────────────────────

    def _remove_port_proxy(self, listen_port: int) -> tuple[bool, str]:
        cmd = (f"netsh interface portproxy delete v4tov4 "
               f"listenport={listen_port} listenaddress=127.0.0.1")
        return self._run(cmd)

    # ── firewall ────────────────────────────────────────────────────────

    def _remove_firewall_rules(self, folder: str) -> tuple[bool, str]:
        removed = 0

        # Delete by enumerating the folder (if available)
        if folder and os.path.isdir(folder):
            exes = glob.glob(os.path.join(folder, "**", "*.exe"),
                             recursive=True)
            for exe in exes:
                rule_name = f"{_FW_PREFIX}{os.path.basename(exe)}"
                self._run(
                    f'netsh advfirewall firewall delete rule '
                    f'name="{rule_name}"')
                removed += 1

        # Sweep any remaining BlockUEMS_* rules (safety net)
        ok, output = self._run(
            "netsh advfirewall firewall show rule dir=out "
            f'name=all | findstr /i "{_FW_PREFIX}"')
        if ok:
            for line in output.splitlines():
                line = line.strip()
                if line.lower().startswith("rule name:"):
                    name = line.split(":", 1)[1].strip()
                    if name.startswith(_FW_PREFIX):
                        self._run(
                            f'netsh advfirewall firewall delete rule '
                            f'name="{name}"')
                        removed += 1

        if self.logger:
            self.logger.log_to_file(f"  Removed {removed} firewall rule(s)")
        return True, f"removed {removed}"

    # ── DNS flush ───────────────────────────────────────────────────────

    def _flush_dns(self) -> None:
        try:
            subprocess.run("ipconfig /flushdns", shell=True,
                           capture_output=True, timeout=15)
            if self.logger:
                self.logger.log_to_file("  Flushed DNS cache")
        except Exception:
            pass

    # ── shell runner ────────────────────────────────────────────────────

    def _run(self, cmd: str) -> tuple[bool, str]:
        try:
            r = subprocess.run(cmd, shell=True, capture_output=True,
                               text=True, timeout=30)
            output = (r.stdout + r.stderr).strip()
            if self.logger:
                self.logger.log_to_file(f"  CMD: {cmd}")
                self.logger.log_to_file(
                    f"  Exit={r.returncode}  Output={output[:200]}")
            if r.returncode == 0:
                return True, output or "OK"
            return False, f"exit {r.returncode}: {output[:200]}"
        except subprocess.TimeoutExpired:
            return False, "Timed out"
        except Exception as e:
            return False, str(e)

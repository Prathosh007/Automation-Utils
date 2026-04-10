from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Any, Optional


@dataclass
class Command:
    """Represents a single test command loaded from JSON."""
    # Identity
    id: str = ""
    action: str = ""
    description: str = ""

    # Control flow
    continue_on_failure: bool = False
    timeout: Optional[int] = None

    # Proxy settings
    listen_host: str = "127.0.0.1"
    listen_port: int = 8080
    upstream_proxy: str = ""
    ssl_insecure: bool = False
    mode: str = ""  # e.g. "reverse:https://10.72.70.228:8383"
    auto_load_cert: bool = False  # Auto-load agent client certs on CSR detection
    live_capture_file: str = ""  # Write traffic to file in real-time

    # Certificate (CA cert install/remove)
    cert_action: str = ""  # "install" or "remove"
    cert_path: str = ""

    # Client certificate (mTLS - for forwarding to real server as the agent)
    client_cert_path: str = ""   # path to client.pem
    client_key_path: str = ""    # path to key.pem

    # Service management
    service_name: str = ""
    service_action: str = ""     # "start", "stop", "restart", "status"

    # Run command
    command_line: str = ""
    working_dir: str = ""

    # Registry patching
    registry_key: str = ""
    registry_action: str = ""    # "patch" or "restore"
    old_values: list = field(default_factory=list)
    new_values: list = field(default_factory=list)

    # System proxy
    proxy_action: str = ""  # "enable" or "disable"
    proxy_host: str = ""
    proxy_port: int = 0
    bypass_list: str = ""

    # Request matching
    url_pattern: str = ""
    method: str = ""
    header_name: str = ""
    body_pattern: str = ""

    # Validation
    expected_value: str = ""
    expected_status: int = 0
    validation_type: str = "contains"  # exact, contains, regex, startswith, endswith
    case_sensitive: bool = False
    min_count: int = 0
    max_count: int = 0
    expected_count: int = -1
    expected_order: list = field(default_factory=list)

    # Response modification
    inject_status: int = 0
    inject_body: str = ""
    inject_headers: dict = field(default_factory=dict)
    persistent: bool = False

    # Body replacement (search→replace on real response body, used with modify_response)
    body_replacements: dict = field(default_factory=dict)

    # Blocking
    block_action: str = "block"  # "block" or "delay"
    delay_ms: int = 0

    # Capture
    capture_file: str = ""
    capture_format: str = "json"

    # Traffic lock (redirect all server-port traffic through proxy)
    target: str = "agent"           # "agent", "ds", or "all"
    server_hostname: str = ""       # real server hostname
    server_ip: str = ""             # real server IP
    server_port: int = 0            # real server port (e.g. 8383)
    redirect_host: str = "127.0.0.1"  # proxy listen address
    redirect_port: int = 0          # proxy listen port (e.g. 8080)
    firewall_rule_name: str = ""    # Windows Firewall rule name
    firewall_program: str = ""      # Block only this exe (empty = block all processes)

    # lock_traffic action
    lock_action: str = ""           # "lock" or "unlock"
    hostname: str = ""              # server hostname to redirect via hosts file
    proxy_port: int = 0             # proxy listen port
    firewall_block_folder: str = "" # folder to enumerate .exe files for firewall rules

    @classmethod
    def from_dict(cls, data: dict) -> "Command":
        """Create a Command from a JSON dictionary, mapping camelCase to snake_case."""
        mapping = {
            "id": "id",
            "action": "action",
            "description": "description",
            "continueOnFailure": "continue_on_failure",
            "continue_on_failure": "continue_on_failure",
            "timeout": "timeout",
            "listenHost": "listen_host",
            "listen_host": "listen_host",
            "listenPort": "listen_port",
            "listen_port": "listen_port",
            "upstreamProxy": "upstream_proxy",
            "upstream_proxy": "upstream_proxy",
            "sslInsecure": "ssl_insecure",
            "ssl_insecure": "ssl_insecure",
            "mode": "mode",
            "autoLoadCert": "auto_load_cert",
            "auto_load_cert": "auto_load_cert",
            "liveCaptureFile": "live_capture_file",
            "live_capture_file": "live_capture_file",
            "certAction": "cert_action",
            "cert_action": "cert_action",
            "certPath": "cert_path",
            "cert_path": "cert_path",
            "proxyAction": "proxy_action",
            "proxy_action": "proxy_action",
            "proxyHost": "proxy_host",
            "proxy_host": "proxy_host",
            "proxyPort": "proxy_port",
            "proxy_port": "proxy_port",
            "bypassList": "bypass_list",
            "bypass_list": "bypass_list",
            "urlPattern": "url_pattern",
            "url_pattern": "url_pattern",
            "method": "method",
            "headerName": "header_name",
            "header_name": "header_name",
            "bodyPattern": "body_pattern",
            "body_pattern": "body_pattern",
            "expectedValue": "expected_value",
            "expected_value": "expected_value",
            "expectedStatus": "expected_status",
            "expected_status": "expected_status",
            "validationType": "validation_type",
            "validation_type": "validation_type",
            "caseSensitive": "case_sensitive",
            "case_sensitive": "case_sensitive",
            "minCount": "min_count",
            "min_count": "min_count",
            "maxCount": "max_count",
            "max_count": "max_count",
            "expectedCount": "expected_count",
            "expected_count": "expected_count",
            "expectedOrder": "expected_order",
            "expected_order": "expected_order",
            "injectStatus": "inject_status",
            "inject_status": "inject_status",
            "injectBody": "inject_body",
            "inject_body": "inject_body",
            "injectHeaders": "inject_headers",
            "inject_headers": "inject_headers",
            "persistent": "persistent",
            "bodyReplacements": "body_replacements",
            "body_replacements": "body_replacements",
            "blockAction": "block_action",
            "block_action": "block_action",
            "delayMs": "delay_ms",
            "delay_ms": "delay_ms",
            "captureFile": "capture_file",
            "capture_file": "capture_file",
            "captureFormat": "capture_format",
            "capture_format": "capture_format",
            "clientCertPath": "client_cert_path",
            "client_cert_path": "client_cert_path",
            "clientKeyPath": "client_key_path",
            "client_key_path": "client_key_path",
            "serviceName": "service_name",
            "service_name": "service_name",
            "serviceAction": "service_action",
            "service_action": "service_action",
            "commandLine": "command_line",
            "command_line": "command_line",
            "workingDir": "working_dir",
            "working_dir": "working_dir",
            "registryKey": "registry_key",
            "registry_key": "registry_key",
            "registryAction": "registry_action",
            "registry_action": "registry_action",
            "oldValues": "old_values",
            "old_values": "old_values",
            "newValues": "new_values",
            "new_values": "new_values",
            "serverHostname": "server_hostname",
            "server_hostname": "server_hostname",
            "serverIp": "server_ip",
            "server_ip": "server_ip",
            "serverPort": "server_port",
            "server_port": "server_port",
            "redirectHost": "redirect_host",
            "redirect_host": "redirect_host",
            "redirectPort": "redirect_port",
            "redirect_port": "redirect_port",
            "firewallRuleName": "firewall_rule_name",
            "firewall_rule_name": "firewall_rule_name",
            "firewallProgram": "firewall_program",
            "firewall_program": "firewall_program",
            "target": "target",
            "lockAction": "lock_action",
            "lock_action": "lock_action",
            "hostname": "hostname",
            "proxyPort": "proxy_port",
            "proxy_port": "proxy_port",
            "firewallBlockFolder": "firewall_block_folder",
            "firewall_block_folder": "firewall_block_folder",
        }
        kwargs = {}
        for json_key, value in data.items():
            attr_name = mapping.get(json_key)
            if attr_name:
                kwargs[attr_name] = value
        return cls(**kwargs)


@dataclass
class CommandResult:
    """Result of executing a single command."""
    command_id: str = ""
    action: str = ""
    success: bool = False
    message: str = ""
    error_message: str = ""
    duration: timedelta = field(default_factory=timedelta)
    data: Any = None
    executed_at: datetime = field(default_factory=datetime.now)


@dataclass
class TestResult:
    """Aggregate result of a full test run."""
    test_id: str = ""
    test_name: str = ""
    success: bool = False
    message: str = ""
    start_time: datetime = field(default_factory=datetime.now)
    end_time: datetime = field(default_factory=datetime.now)
    command_results: list = field(default_factory=list)

    @property
    def duration(self) -> timedelta:
        return self.end_time - self.start_time

    @property
    def total_commands(self) -> int:
        return len(self.command_results)

    @property
    def passed_commands(self) -> int:
        return sum(1 for r in self.command_results if r.success)

    @property
    def failed_commands(self) -> int:
        return sum(1 for r in self.command_results if not r.success)

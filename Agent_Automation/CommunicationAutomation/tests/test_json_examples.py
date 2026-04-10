"""
Tests for all JSON example actions from CommunicationOperation_Reference.md (sections 6.1–6.27).

Each test class validates:
  1. Command.from_dict() correctly parses the example JSON parameters.
  2. The action's validate_command() passes for the parsed command.
  3. The action's execute() works correctly with mocked dependencies.
"""

import pytest
from unittest.mock import patch, MagicMock
from core.models import Command
from core.action_registry import ActionRegistry


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _parse(params: dict) -> Command:
    """Parse a parameters dict into a Command."""
    return Command.from_dict(params)


def _get_action(name: str):
    """Get an action handler from the registry."""
    registry = ActionRegistry()
    return registry.get_action(name)


# ============================================================================
# 6.1 Start Proxy — Basic
# ============================================================================
class TestExample_6_1_StartProxy:
    PARAMS = {
        "action": "start_proxy",
        "listenPort": 8080,
        "description": "Start mitmproxy on port 8080",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "start_proxy"
        assert cmd.listen_port == 8080

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("start_proxy")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("actions.start_proxy.subprocess.Popen")
    @patch("time.sleep")
    def test_execute(self, mock_sleep, mock_popen, logger, empty_context):
        mock_proc = MagicMock()
        mock_proc.pid = 12345
        mock_proc.poll.return_value = None
        mock_popen.return_value = mock_proc
        cmd = _parse(self.PARAMS)
        action = _get_action("start_proxy")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.2 Start Reverse Proxy with SSL and Auto Cert Loading
# ============================================================================
class TestExample_6_2_StartReverseProxy:
    PARAMS = {
        "action": "start_proxy",
        "listenHost": "127.0.0.1",
        "listenPort": 8080,
        "mode": "reverse:https://server.example.com:443",
        "sslInsecure": True,
        "autoLoadCert": True,
        "liveCaptureFile": "Reports/live-capture.json",
        "description": "Start reverse proxy with auto cert loading",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "start_proxy"
        assert cmd.listen_host == "127.0.0.1"
        assert cmd.listen_port == 8080
        assert cmd.mode == "reverse:https://server.example.com:443"
        assert cmd.ssl_insecure is True
        assert cmd.auto_load_cert is True
        assert cmd.live_capture_file == "Reports/live-capture.json"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("start_proxy")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("actions.start_proxy.subprocess.Popen")
    @patch("time.sleep")
    def test_execute(self, mock_sleep, mock_popen, logger, empty_context):
        mock_proc = MagicMock()
        mock_proc.pid = 12345
        mock_proc.poll.return_value = None
        mock_popen.return_value = mock_proc
        cmd = _parse(self.PARAMS)
        action = _get_action("start_proxy")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.3 Stop Proxy
# ============================================================================
class TestExample_6_3_StopProxy:
    PARAMS = {
        "action": "stop_proxy",
        "description": "Stop the proxy and clean up",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "stop_proxy"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("stop_proxy")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("actions.stop_proxy.subprocess.run")
    def test_execute(self, mock_run, logger, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        cmd = _parse(self.PARAMS)
        action = _get_action("stop_proxy")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        # stop_proxy succeeds even with no proxy running (nothing to stop)
        assert result.success is True


# ============================================================================
# 6.4 Install Certificate
# ============================================================================
class TestExample_6_4_InstallCert:
    PARAMS = {
        "action": "install_cert",
        "description": "Install mitmproxy CA certificate",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "install_cert"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("install_cert")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("proxy.certificate_manager.CertificateManager.install_certificate",
           return_value=(True, "Certificate installed"))
    def test_execute(self, mock_install, logger, empty_context):
        cmd = _parse(self.PARAMS)
        action = _get_action("install_cert")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.5 Remove Certificate
# ============================================================================
class TestExample_6_5_RemoveCertificate:
    PARAMS = {
        "action": "remove_certificate",
        "description": "Remove mitmproxy CA certificate",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "remove_certificate"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("remove_certificate")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("proxy.certificate_manager.CertificateManager.remove_certificate",
           return_value=(True, "Certificate removed"))
    def test_execute(self, mock_remove, logger, empty_context):
        cmd = _parse(self.PARAMS)
        action = _get_action("remove_certificate")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.6 Wait for a Specific Request
# ============================================================================
class TestExample_6_6_WaitForRequest:
    PARAMS = {
        "action": "wait_for_request",
        "urlPattern": "agentSlot",
        "timeout": 120,
        "captureFile": "Reports/live-capture.json",
        "description": "Wait for agentSlot request (file-polling mode)",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "wait_for_request"
        assert cmd.url_pattern == "agentSlot"
        assert cmd.timeout == 120
        assert cmd.capture_file == "Reports/live-capture.json"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("wait_for_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True


# ============================================================================
# 6.11 Load Traffic from File
# ============================================================================
class TestExample_6_11_LoadTraffic:
    PARAMS = {
        "action": "load_traffic",
        "captureFile": "Reports/recorded-traffic.json",
        "description": "Load previously captured traffic for validation",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "load_traffic"
        assert cmd.capture_file == "Reports/recorded-traffic.json"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("load_traffic")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, empty_context, capture_file):
        """Execute with a real temp capture file."""
        params = {
            "action": "load_traffic",
            "captureFile": capture_file,
            "description": "Load captured traffic",
        }
        cmd = _parse(params)
        action = _get_action("load_traffic")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert "traffic_store" in empty_context


# ============================================================================
# 6.12 Validate Request — Method Check
# ============================================================================
class TestExample_6_12_ValidateRequestMethod:
    PARAMS = {
        "action": "validate_request",
        "urlPattern": "agentSlot",
        "method": "GET",
        "description": "Verify agentSlot uses GET method",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "validate_request"
        assert cmd.url_pattern == "agentSlot"
        assert cmd.method == "GET"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.13 Validate Request — Header Check
# ============================================================================
class TestExample_6_13_ValidateRequestHeader:
    PARAMS = {
        "action": "validate_request",
        "urlPattern": "agentSlot",
        "headerName": "Authorization",
        "expectedValue": "Bearer ",
        "validationType": "startswith",
        "description": "Verify agentSlot has Bearer Authorization header",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "validate_request"
        assert cmd.header_name == "Authorization"
        assert cmd.expected_value == "Bearer "
        assert cmd.validation_type == "startswith"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        # sample_flow has "Bearer token123" which starts with "Bearer "
        assert result.success is True


# ============================================================================
# 6.14 Validate Request — Body Check
# ============================================================================
class TestExample_6_14_ValidateRequestBody:
    PARAMS = {
        "action": "validate_request",
        "urlPattern": "agentSlot",
        "bodyPattern": "agent",
        "validationType": "contains",
        "description": "Verify agentSlot request body contains agent",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.body_pattern == "agent"
        assert cmd.validation_type == "contains"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_request")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        # sample_flow body is '{"agent": "test"}' which contains "agent"
        assert result.success is True


# ============================================================================
# 6.15 Validate Response — Status Code
# ============================================================================
class TestExample_6_15_ValidateResponseStatus:
    PARAMS = {
        "action": "validate_response",
        "urlPattern": "agentSlot",
        "expectedStatus": 200,
        "description": "Verify agentSlot returns 200 OK",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "validate_response"
        assert cmd.expected_status == 200

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_response")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_response")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.16 Validate Response — Header (exact match)
# ============================================================================
class TestExample_6_16_ValidateResponseHeader:
    PARAMS = {
        "action": "validate_response",
        "urlPattern": "meta-data\\.xml",
        "headerName": "Content-Type",
        "expectedValue": "text/xml",
        "validationType": "exact",
        "description": "Verify meta-data.xml Content-Type is XML",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "validate_response"
        assert cmd.header_name == "Content-Type"
        assert cmd.validation_type == "exact"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_response")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("validate_response")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        # metadata_flow has Content-Type: text/xml
        assert result.success is True


# ============================================================================
# 6.17 Assert Request Count
# ============================================================================
class TestExample_6_17_AssertRequestCount:
    PARAMS = {
        "action": "assert_request_count",
        "urlPattern": "agentSlot",
        "minCount": 1,
        "description": "Verify at least one agentSlot request captured",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "assert_request_count"
        assert cmd.min_count == 1

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("assert_request_count")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("assert_request_count")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.18 Assert Request Order
# ============================================================================
class TestExample_6_18_AssertRequestOrder:
    PARAMS = {
        "action": "assert_request_order",
        "expectedOrder": ["agentSlot", "meta-data\\.xml"],
        "description": "Verify correct request sequence",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "assert_request_order"
        assert len(cmd.expected_order) == 2

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("assert_request_order")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("assert_request_order")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        # sample_flow (agentSlot) was added before metadata_flow (meta-data.xml)
        assert result.success is True


# ============================================================================
# 6.19 Capture Traffic to File
# ============================================================================
class TestExample_6_19_CaptureTraffic:
    PARAMS = {
        "action": "capture_traffic",
        "captureFile": "Reports/recorded-traffic.json",
        "description": "Save captured traffic to file",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "capture_traffic"
        assert cmd.capture_file == "Reports/recorded-traffic.json"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("capture_traffic")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store, tmp_path):
        out_file = str(tmp_path / "captured.json")
        params = dict(self.PARAMS, captureFile=out_file)
        cmd = _parse(params)
        action = _get_action("capture_traffic")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.20 Clear Captured Traffic
# ============================================================================
class TestExample_6_20_ClearCaptures:
    PARAMS = {
        "action": "clear_captures",
        "description": "Reset traffic buffer",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "clear_captures"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("clear_captures")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("clear_captures")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.21 Modify Response — Inject Error
# ============================================================================
class TestExample_6_21_ModifyResponse:
    PARAMS = {
        "action": "modify_response",
        "urlPattern": "meta-data\\.xml",
        "injectStatus": 500,
        "injectBody": "Internal Server Error",
        "persistent": True,
        "description": "Return 500 error on meta-data.xml requests",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "modify_response"
        assert cmd.inject_status == 500
        assert cmd.inject_body == "Internal Server Error"
        assert cmd.persistent is True

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("modify_response")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("modify_response")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.22 Block Request
# ============================================================================
class TestExample_6_22_BlockRequest:
    PARAMS = {
        "action": "block_request",
        "urlPattern": "agentSlot",
        "blockAction": "block",
        "persistent": True,
        "description": "Block all agentSlot requests",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "block_request"
        assert cmd.block_action == "block"
        assert cmd.persistent is True

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("block_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("block_request")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.23 Delay Request
# ============================================================================
class TestExample_6_23_DelayRequest:
    PARAMS = {
        "action": "block_request",
        "urlPattern": ".*",
        "blockAction": "delay",
        "delayMs": 5000,
        "persistent": True,
        "description": "Add 5 second delay to all requests",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "block_request"
        assert cmd.block_action == "delay"
        assert cmd.delay_ms == 5000

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("block_request")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    def test_execute(self, logger, context_with_store):
        cmd = _parse(self.PARAMS)
        action = _get_action("block_request")
        action.set_logger(logger)
        result = action.execute(cmd, context_with_store)
        assert result.success is True


# ============================================================================
# 6.24 Configure System Proxy — Enable
# ============================================================================
class TestExample_6_24_ConfigureProxyEnable:
    PARAMS = {
        "action": "configure_proxy",
        "proxyAction": "enable",
        "proxyHost": "127.0.0.1",
        "proxyPort": 8080,
        "description": "Enable Windows system proxy",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "configure_proxy"
        assert cmd.proxy_action == "enable"
        assert cmd.proxy_host == "127.0.0.1"
        assert cmd.proxy_port == 8080

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("configure_proxy")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("actions.configure_proxy.subprocess.run")
    def test_execute(self, mock_run, logger, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stdout="OK", stderr="")
        cmd = _parse(self.PARAMS)
        action = _get_action("configure_proxy")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.25 Configure System Proxy — Disable
# ============================================================================
class TestExample_6_25_ConfigureProxyDisable:
    PARAMS = {
        "action": "configure_proxy",
        "proxyAction": "disable",
        "description": "Disable Windows system proxy",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "configure_proxy"
        assert cmd.proxy_action == "disable"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("configure_proxy")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True

    @patch("actions.configure_proxy.subprocess.run")
    def test_execute(self, mock_run, logger, empty_context):
        mock_run.return_value = MagicMock(returncode=0, stdout="OK", stderr="")
        cmd = _parse(self.PARAMS)
        action = _get_action("configure_proxy")
        action.set_logger(logger)
        result = action.execute(cmd, empty_context)
        assert result.success is True


# ============================================================================
# 6.26 Load Client Certificate
# ============================================================================
class TestExample_6_26_LoadClientCert:
    PARAMS = {
        "action": "load_client_cert",
        "description": "Load agent mTLS client certificate from vault",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "load_client_cert"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("load_client_cert")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True


# ============================================================================
# 6.27 Wait for Interrupt (Recording Mode)
# ============================================================================
class TestExample_6_27_WaitForInterrupt:
    PARAMS = {
        "action": "wait_for_interrupt",
        "description": "Record traffic until Ctrl+C",
    }

    def test_parse(self):
        cmd = _parse(self.PARAMS)
        assert cmd.action == "wait_for_interrupt"

    def test_validate(self, logger):
        cmd = _parse(self.PARAMS)
        action = _get_action("wait_for_interrupt")
        action.set_logger(logger)
        assert action.validate_command(cmd) is True


# ============================================================================
# Action Registry — Verify all actions are registered
# ============================================================================
class TestActionRegistryComplete:
    """Verify every action referenced in the document is registered."""

    ALL_ACTIONS = [
        "start_proxy", "stop_proxy", "install_cert", "remove_certificate",
        "install_certificate",  # backward compatibility
        "load_client_cert", "configure_proxy",
        "wait_for_request",
        "validate_request", "validate_response", "modify_response",
        "block_request", "assert_request_count", "assert_request_order",
        "capture_traffic", "load_traffic", "clear_captures",
        "wait_for_interrupt",
    ]

    @pytest.mark.parametrize("action_name", ALL_ACTIONS)
    def test_action_registered(self, action_name):
        registry = ActionRegistry()
        action = registry.get_action(action_name)
        assert action is not None

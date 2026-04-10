import logging
import os
import time
import threading
from datetime import datetime
from typing import Callable, Optional

from mitmproxy import http

from proxy.traffic_store import TrafficStore, CapturedFlow
from proxy.response_rules import ResponseRuleEngine

logger = logging.getLogger(__name__)

# CSR servlet URL pattern for agent certificate enrollment
CSR_SERVLET_PATTERN = "ClientCSRSigningServlet"

# How long to wait (seconds) for cert files to appear in vault after CSR
_CERT_WAIT_TIMEOUT = 60
_CERT_POLL_INTERVAL = 2


class UEMSAddon:
    """mitmproxy addon that intercepts flows, captures traffic, and applies response rules.

    Auto-detects ClientCSRSigningServlet responses during agent installation.
    When a successful CSR response is seen, the addon holds subsequent requests
    until client certificates are loaded from the agent vault. This allows
    seamless mTLS transition without dropping any traffic.

    If ``auto_load_cert=True``, the addon will automatically discover the agent
    vault from the Windows service, load the client certs, and hot-reload them
    into the proxy — no manual ``load_client_cert`` step needed.
    """

    def __init__(self, traffic_store: TrafficStore, rule_engine: ResponseRuleEngine,
                 auto_load_cert: bool = False):
        self._traffic_store = traffic_store
        self._rule_engine = rule_engine

        # CSR interception state
        self._csr_detected = False          # True after CSR 200 response seen
        self._cert_ready = threading.Event() # Set when certs are loaded into proxy
        self._cert_ready.set()               # Initially set (no hold needed)
        self._hold_timeout = 90              # Max seconds to hold a request

        # Auto cert loading
        self._auto_load_cert = auto_load_cert
        self._cert_update_callback: Optional[Callable[[str], None]] = None
        self._auto_load_thread: Optional[threading.Thread] = None

    @property
    def csr_detected(self) -> bool:
        """Whether a successful CSR response has been observed."""
        return self._csr_detected

    def set_cert_update_callback(self, callback: Callable[[str], None]) -> None:
        """Set a callback that the addon invokes to hot-reload a combined PEM into the proxy.

        Called by ProxyManager after startup so the addon can trigger cert updates
        without holding a direct reference to the proxy master.
        """
        self._cert_update_callback = callback

    def release_held_requests(self) -> None:
        """Release any requests held waiting for client certs."""
        logger.info("Releasing held requests — client certs loaded")
        self._cert_ready.set()

    def _auto_load_certs_background(self) -> None:
        """Background thread: discover vault from service, wait for cert files, load them.

        Runs only when ``auto_load_cert=True`` and a CSR 200 response is detected.
        """
        try:
            from utils.service_discovery import get_agent_vault_path
            from actions.load_client_cert import LoadClientCertAction

            logger.info("Auto cert loader started — discovering agent vault from service…")

            vault = get_agent_vault_path()
            if not vault:
                logger.warning("Auto cert load: could not discover vault path from service")
                self.release_held_requests()
                return

            key_path = os.path.join(vault, "key.pem")
            cert_path = os.path.join(vault, "client.pem")

            # Poll until both files appear (agent writes them after CSR response)
            waited = 0
            while waited < _CERT_WAIT_TIMEOUT:
                if os.path.exists(key_path) and os.path.exists(cert_path):
                    # Give the agent a moment to finish writing
                    time.sleep(1)
                    logger.info(f"Cert files found in vault: {vault}")
                    break
                logger.info(f"Waiting for cert files in {vault}… ({waited}s)")
                time.sleep(_CERT_POLL_INTERVAL)
                waited += _CERT_POLL_INTERVAL
            else:
                logger.warning(f"Cert files not found after {_CERT_WAIT_TIMEOUT}s — releasing hold")
                self.release_held_requests()
                return

            # Use LoadClientCertAction's PEM conversion logic
            action = LoadClientCertAction()
            combined_path = os.path.normpath(
                os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "..", "Reports", "agent_client_combined.pem")
            )
            os.makedirs(os.path.dirname(combined_path), exist_ok=True)

            import shutil
            temp_key = combined_path + ".key.tmp"
            temp_cert = combined_path + ".cert.tmp"
            try:
                shutil.copy2(key_path, temp_key)
                shutil.copy2(cert_path, temp_cert)
            except Exception as e:
                logger.error(f"Auto cert load: failed to copy vault files: {e}")
                self.release_held_requests()
                return

            try:
                with open(temp_key, "rb") as f:
                    key_data = f.read()
                with open(temp_cert, "rb") as f:
                    cert_data = f.read()
            except Exception as e:
                logger.error(f"Auto cert load: failed to read temp files: {e}")
                self.release_held_requests()
                return
            finally:
                for tmp in (temp_key, temp_cert):
                    try:
                        os.remove(tmp)
                    except OSError:
                        pass

            # Convert to PEM
            key_pem = None
            cert_pem = None
            try:
                key_pem = action._to_pem_key(key_data)
            except Exception as e:
                logger.warning(f"Auto cert load: key conversion failed: {e}")
            try:
                cert_pem = action._to_pem_cert(cert_data)
            except Exception as e:
                logger.warning(f"Auto cert load: cert conversion failed: {e}")

            if key_pem and cert_pem:
                try:
                    with open(combined_path, "wb") as f:
                        f.write(key_pem)
                        if not key_pem.endswith(b"\n"):
                            f.write(b"\n")
                        f.write(cert_pem)
                        if not cert_pem.endswith(b"\n"):
                            f.write(b"\n")
                    logger.info(f"Auto cert load: combined PEM written to {combined_path}")
                except Exception as e:
                    logger.error(f"Auto cert load: failed to write combined PEM: {e}")
                    self.release_held_requests()
                    return

                # Hot-reload into proxy via callback
                if self._cert_update_callback:
                    self._cert_update_callback(combined_path)
                    logger.info("Auto cert load: hot-reloaded into proxy — requests released")
                else:
                    logger.warning("Auto cert load: no callback set, releasing held requests")
                    self.release_held_requests()
            else:
                logger.warning("Auto cert load: vault files in proprietary format, skipping mTLS")
                if self._cert_update_callback:
                    self._cert_update_callback("")  # Release hold, no certs
                else:
                    self.release_held_requests()

        except Exception as e:
            logger.error(f"Auto cert load: unexpected error: {e}", exc_info=True)
            self.release_held_requests()

    def request(self, flow: http.HTTPFlow) -> None:
        """Called when a request is received by the proxy."""
        url = flow.request.pretty_url
        method = flow.request.method
        logger.info(f"Request: {method} {url}")

        # If CSR was detected and certs aren't loaded yet, hold this request
        if self._csr_detected and not self._cert_ready.is_set():
            logger.info(f"Holding request until certs loaded: {method} {url}")
            if not self._cert_ready.wait(timeout=self._hold_timeout):
                logger.warning(f"Cert load timeout ({self._hold_timeout}s), releasing: {url}")
            else:
                logger.info(f"Certs loaded, releasing held request: {url}")

        # Check for blocking/delay rules
        rule = self._rule_engine.get_matching_rule(url, method)
        if rule:
            if rule.block:
                flow.response = http.Response.make(
                    502, b"Blocked by UEMS proxy automation",
                    {"Content-Type": "text/plain"}
                )
                return

            if rule.delay_ms > 0:
                time.sleep(rule.delay_ms / 1000.0)

            # If rule modifies response, inject it now
            if rule.inject_status or rule.inject_body:
                status = rule.inject_status or 200
                body = rule.inject_body.encode("utf-8") if rule.inject_body else b""
                headers = {"Content-Type": "application/json"}
                headers.update(rule.inject_headers)
                flow.response = http.Response.make(status, body, headers)

    def response(self, flow: http.HTTPFlow) -> None:
        """Called when a response is received (or was injected)."""
        logger.info(f"Response: {flow.request.method} {flow.request.pretty_url} -> {flow.response.status_code}")

        # Detect successful CSR response — agent just enrolled its certificate
        if (CSR_SERVLET_PATTERN in flow.request.pretty_url
                and flow.response and flow.response.status_code == 200
                and not self._csr_detected):
            logger.info(f"CSR response detected: {flow.request.pretty_url} -> 200")
            self._csr_detected = True
            self._cert_ready.clear()  # Hold subsequent requests until certs loaded
            logger.info("Post-CSR hold activated — next requests will wait for cert load")

            # Auto-load certs from agent vault in background thread
            if self._auto_load_cert and self._auto_load_thread is None:
                logger.info("Auto cert loading enabled — starting background loader")
                self._auto_load_thread = threading.Thread(
                    target=self._auto_load_certs_background,
                    daemon=True,
                    name="auto-cert-loader",
                )
                self._auto_load_thread.start()

        # Apply body replacements from persistent response rules
        self._apply_body_replacements(flow)

        self._capture_flow(flow)

    def _apply_body_replacements(self, flow: http.HTTPFlow) -> None:
        """Replace strings in real response bodies (e.g. server IP → 127.0.0.1)."""
        if not flow.response or not flow.response.content:
            return
        rules = self._rule_engine.get_response_rules(
            flow.request.pretty_url, flow.request.method)
        if not rules:
            return
        try:
            body = flow.response.content.decode("utf-8", errors="replace")
            modified = False
            for rule in rules:
                for search, replace in rule.body_replacements.items():
                    if search in body:
                        body = body.replace(search, replace)
                        modified = True
            if modified:
                flow.response.content = body.encode("utf-8")
                # Update Content-Length header
                flow.response.headers["Content-Length"] = str(len(flow.response.content))
                logger.info(f"Body replacements applied to {flow.request.pretty_url}")
        except Exception as e:
            logger.warning(f"Body replacement failed for {flow.request.pretty_url}: {e}")

    def error(self, flow: http.HTTPFlow) -> None:
        """Called when a flow errors (e.g. upstream TLS handshake failure).
        Still captures the request data so we know what the agent tried to send."""
        error_detail = str(flow.error) if flow.error else "unknown"
        logger.warning(f"Flow error: {flow.request.method} {flow.request.pretty_url} -> {error_detail}")
        self._capture_flow(flow, is_error=True)

    def _capture_flow(self, flow: http.HTTPFlow, is_error: bool = False) -> None:
        """Capture a flow (successful or failed) into the traffic store."""
        try:
            req = flow.request
            resp = flow.response

            # Build request body
            req_body = ""
            if req.content:
                try:
                    req_body = req.content.decode("utf-8", errors="replace")
                except Exception:
                    req_body = "<binary>"

            # Build response body
            resp_body = ""
            if resp and resp.content:
                try:
                    resp_body = resp.content.decode("utf-8", errors="replace")
                except Exception:
                    resp_body = "<binary>"

            # For error flows, include error info
            if is_error:
                error_msg = str(flow.error) if flow.error else "connection error"
                if not resp:
                    resp_body = f"<proxy error: {error_msg}>"
                else:
                    resp_body = f"<proxy error: {error_msg}> (response status={resp.status_code})"
                logger.debug(f"Captured error flow: {req.method} {req.pretty_url} -> {error_msg}")

            captured = CapturedFlow(
                timestamp=datetime.now(),
                request_url=req.pretty_url,
                request_method=req.method,
                request_headers=dict(req.headers),
                request_body=req_body,
                response_status=resp.status_code if resp else 0,
                response_headers=dict(resp.headers) if resp else {},
                response_body=resp_body,
                response_reason=str(resp.reason) if resp and resp.reason else ("error" if is_error else ""),
            )
            self._traffic_store.add_flow(captured)
        except Exception as e:
            logger.warning("Failed to capture flow: %s %s -> %s",
                           flow.request.method, flow.request.pretty_url, e)

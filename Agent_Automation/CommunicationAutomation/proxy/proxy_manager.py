import asyncio
import logging
import threading
from typing import Optional

from mitmproxy import options
from mitmproxy.tools.dump import DumpMaster

from proxy.addon import UEMSAddon
from proxy.traffic_store import TrafficStore
from proxy.response_rules import ResponseRuleEngine

logger = logging.getLogger(__name__)


class _StartupNotifier:
    """mitmproxy addon that signals when the proxy is fully running."""

    def __init__(self, event: threading.Event):
        self._event = event

    def running(self):
        self._event.set()


class ProxyManager:
    """Manages mitmproxy DumpMaster lifecycle in a background daemon thread."""

    def __init__(self, live_capture_file: str = ""):
        self._master: Optional[DumpMaster] = None
        self._thread: Optional[threading.Thread] = None
        self._started = threading.Event()
        self._error: Optional[str] = None
        self._addon: Optional[UEMSAddon] = None
        self.traffic_store = TrafficStore(live_capture_file=live_capture_file)
        self.rule_engine = ResponseRuleEngine()
        self._auto_load_cert = False

    @property
    def is_running(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def start(self, listen_host: str = "127.0.0.1", listen_port: int = 8080,
              ssl_insecure: bool = False, upstream_proxy: str = "",
              mode: str = "", client_certs: str = "",
              auto_load_cert: bool = False) -> None:
        """Start mitmproxy in a background daemon thread."""
        if self.is_running:
            raise RuntimeError("Proxy is already running")

        self._error = None
        self._auto_load_cert = auto_load_cert
        self._started.clear()
        self._thread = threading.Thread(
            target=self._run_proxy,
            args=(listen_host, listen_port, ssl_insecure, upstream_proxy, mode, client_certs),
            daemon=True,
            name="mitmproxy-thread",
        )
        self._thread.start()

        # Wait for proxy to signal ready (via running() hook) or fail
        if not self._started.wait(timeout=15):
            raise RuntimeError("Proxy failed to start within 15 seconds")
        if self._error:
            raise RuntimeError(f"Proxy startup error: {self._error}")

    @property
    def addon(self) -> "UEMSAddon":
        """The UEMSAddon instance (available after start)."""
        return self._addon

    def update_client_certs(self, cert_path: str) -> None:
        """Hot-reload client certificate into the running proxy.

        Updates mitmproxy's client_certs option at runtime and releases
        any requests held by the addon's CSR interception logic.
        """
        if self._master and cert_path:
            self._master.options.update(client_certs=cert_path)
        # Release held requests even if cert_path is empty (timeout scenario)
        if self._addon:
            self._addon.release_held_requests()

    def stop(self) -> None:
        """Stop the proxy gracefully."""
        # Release any held requests before shutdown
        if self._addon:
            self._addon.release_held_requests()
        if self._master:
            try:
                self._master.shutdown()
            except Exception:
                pass
        if self._thread:
            self._thread.join(timeout=10)
        self._master = None
        self._thread = None
        self._addon = None

    def _run_proxy(self, listen_host: str, listen_port: int,
                   ssl_insecure: bool, upstream_proxy: str, mode: str,
                   client_certs: str) -> None:
        """Thread target: use asyncio.run() with an async helper so
        DumpMaster is created inside a running event loop."""
        try:
            asyncio.run(self._async_run_proxy(
                listen_host, listen_port, ssl_insecure,
                upstream_proxy, mode, client_certs
            ))
        except Exception as e:
            self._error = str(e)
            self._started.set()

    async def _async_run_proxy(self, listen_host: str, listen_port: int,
                               ssl_insecure: bool, upstream_proxy: str,
                               mode: str, client_certs: str) -> None:
        """Async helper: DumpMaster needs asyncio.get_running_loop() to work,
        so it must be created inside an already-running async context."""

        # Suppress ConnectionResetError [WinError 10054] in asyncio ProactorEventLoop.
        # This is a non-fatal error on Windows when the agent or server abruptly
        # closes a connection — the proxy should keep running.
        loop = asyncio.get_running_loop()
        original_handler = loop.get_exception_handler()

        def _exception_handler(loop, context):
            exc = context.get("exception")
            if isinstance(exc, (ConnectionResetError, ConnectionAbortedError, OSError)):
                return  # Suppress — non-fatal Windows network errors
            if original_handler:
                original_handler(loop, context)
            else:
                loop.default_exception_handler(context)

        loop.set_exception_handler(_exception_handler)

        # Build mode list
        if mode:
            proxy_mode = [mode]
        elif upstream_proxy:
            proxy_mode = [f"upstream:{upstream_proxy}"]
        else:
            proxy_mode = ["regular"]

        opts_kwargs = dict(
            listen_host=listen_host,
            listen_port=listen_port,
            ssl_insecure=ssl_insecure,
            mode=proxy_mode,
        )
        if client_certs:
            opts_kwargs["client_certs"] = client_certs

        logger.info(f"mitmproxy options: {opts_kwargs}")

        opts = options.Options(**opts_kwargs)

        # Created inside async context — get_running_loop() now works
        self._master = DumpMaster(opts)

        self._addon = UEMSAddon(self.traffic_store, self.rule_engine,
                                auto_load_cert=self._auto_load_cert)
        self._addon.set_cert_update_callback(self.update_client_certs)
        notifier = _StartupNotifier(self._started)
        self._master.addons.add(self._addon)
        self._master.addons.add(notifier)

        await self._master.run()

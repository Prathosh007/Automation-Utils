"""CommunicationAutomation - MITM Proxy Testing for UEMS Agent.

Usage: python main.py <test.json>
       python main.py --serve <host> <port> <mode> [--ssl-insecure] [--auto-load-cert] [--live-capture <file>]

Exit codes:
    0  - All tests passed
    1  - One or more tests failed
    5  - Test file not found
    99 - Critical unhandled error
"""
import os
import signal
import sys

from core.test_engine import TestEngine
from utils.logger import Logger


def _run_serve_mode(args: list[str]) -> int:
    """Run the proxy as a long-lived blocking process (for background mode).

    Invoked internally by start_proxy.
    Runs until SIGINT/SIGTERM or the process is killed.
    Writes detailed logs to Reports/proxy.log.
    """
    import logging

    host = args[0] if len(args) > 0 else "127.0.0.1"
    port = int(args[1]) if len(args) > 1 else 8080
    mode = args[2] if len(args) > 2 else ""
    ssl_insecure = "--ssl-insecure" in args
    auto_load_cert = "--auto-load-cert" in args

    live_capture = ""
    if "--live-capture" in args:
        idx = args.index("--live-capture")
        if idx + 1 < len(args):
            live_capture = args[idx + 1]

    # ---- Setup logging to file so background proxy is diagnosable ----
    os.makedirs("Reports", exist_ok=True)
    log_file = os.path.join("Reports", "proxy.log")
    logging.basicConfig(
        filename=log_file,
        level=logging.DEBUG,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    log = logging.getLogger("serve")

    # ---- Ensure certifi CA bundle is discoverable (PyInstaller onefile) ----
    # The _MEIPASS temp dir can be cleaned up by the parent process or antivirus
    # while the background proxy is still running. Copy the CA bundle to a stable
    # location so mitmproxy can always find it for TLS context creation.
    _meipass = getattr(sys, '_MEIPASS', None)
    stable_ca = os.path.join("Reports", "cacert.pem")
    ca_found = False

    if _meipass:
        ca_path = os.path.join(_meipass, 'certifi', 'cacert.pem')
        if os.path.isfile(ca_path):
            # Copy to stable location outside the temp dir
            try:
                import shutil
                shutil.copy2(ca_path, stable_ca)
                _abs_stable = os.path.abspath(stable_ca)
                os.environ['SSL_CERT_FILE'] = _abs_stable
                os.environ['REQUESTS_CA_BUNDLE'] = _abs_stable

                # Monkey-patch certifi.where() — mitmproxy calls this directly
                # in mitmproxy/net/tls.py, NOT the SSL_CERT_FILE env var
                import certifi
                certifi.where = lambda: _abs_stable

                log.info(f"Certifi CA bundle: {ca_path} -> {stable_ca} (monkey-patched)")
                ca_found = True
            except Exception as e:
                log.warning(f"Failed to copy CA bundle to stable path: {e}")
                # Fall back to temp dir path
                os.environ.setdefault('SSL_CERT_FILE', ca_path)
                os.environ.setdefault('REQUESTS_CA_BUNDLE', ca_path)
                log.info(f"Certifi CA bundle (temp, may disappear): {ca_path}")
                ca_found = True

    if not ca_found:
        # Try certifi module directly (dev mode or temp dir missing)
        try:
            import certifi
            ca_path = certifi.where()
            if os.path.isfile(ca_path):
                try:
                    import shutil
                    shutil.copy2(ca_path, stable_ca)
                    _abs_stable = os.path.abspath(stable_ca)
                    os.environ['SSL_CERT_FILE'] = _abs_stable
                    certifi.where = lambda: _abs_stable
                    log.info(f"Certifi CA bundle (module): {ca_path} -> {stable_ca} (monkey-patched)")
                except Exception:
                    os.environ.setdefault('SSL_CERT_FILE', ca_path)
                    log.info(f"Certifi CA bundle (module): {ca_path}")
        except ImportError:
            log.warning("certifi not installed — TLS verification may fail")

    log.info("=" * 60)
    log.info(f"Proxy serve mode starting  PID={os.getpid()}")
    log.info(f"  host={host}  port={port}  mode={mode}")
    log.info(f"  ssl_insecure={ssl_insecure}  auto_load_cert={auto_load_cert}")
    log.info(f"  live_capture={live_capture}")
    log.info("=" * 60)

    from proxy.proxy_manager import ProxyManager

    pid_file = os.path.join("Reports", "proxy.pid")
    with open(pid_file, "w") as f:
        f.write(str(os.getpid()))

    pm = ProxyManager(live_capture_file=live_capture)
    stop_event = __import__("threading").Event()

    def _on_signal(sig, frame):
        log.info(f"Signal received: {sig} — shutting down")
        stop_event.set()

    signal.signal(signal.SIGINT, _on_signal)
    signal.signal(signal.SIGTERM, _on_signal)

    try:
        pm.start(
            listen_host=host,
            listen_port=port,
            ssl_insecure=ssl_insecure,
            mode=mode,
            auto_load_cert=auto_load_cert,
        )
        log.info(f"Proxy running on {host}:{port} (PID {os.getpid()})")
        if live_capture:
            log.info(f"Live capture: {live_capture}")

        # Poll for control file commands while waiting for stop signal
        control_file = os.path.join("Reports", "proxy-control.json")
        import json as _json
        while not stop_event.is_set():
            # Check for control commands every second
            if os.path.isfile(control_file):
                try:
                    with open(control_file, "r", encoding="utf-8") as cf:
                        ctrl = _json.load(cf)
                    os.remove(control_file)

                    action = ctrl.get("action", "")
                    if action == "switch_capture":
                        new_file = ctrl.get("file", "")
                        clear = ctrl.get("clear", True)
                        old_file = pm.traffic_store.switch_live_capture_file(new_file, clear_flows=clear)
                        log.info(f"Switched live capture: {old_file} -> {new_file} (clear={clear})")
                        ack = {"status": "ok", "old_file": old_file, "new_file": new_file}

                    elif action == "add_body_replacements":
                        from proxy.response_rules import ResponseRule
                        replacements = ctrl.get("replacements", {})
                        if replacements:
                            rule = ResponseRule(
                                url_pattern="",
                                body_replacements=replacements,
                                persistent=True,
                            )
                            pm.rule_engine.add_rule(rule)
                            targets = ", ".join(f"{k}->{v}" for k, v in replacements.items())
                            log.info(f"Added body replacement rule: {targets}")
                            ack = {"status": "ok", "targets": targets}
                        else:
                            log.warning("add_body_replacements: no replacements provided")
                            ack = {"status": "error", "message": "no replacements provided"}

                    elif action == "clear_body_replacements":
                        count = pm.rule_engine.clear_rules()
                        log.info(f"Cleared {count} rule(s)")
                        ack = {"status": "ok", "cleared": count}

                    else:
                        log.warning(f"Unknown control action: {action}")
                        ack = {"status": "error", "message": f"Unknown action: {action}"}

                    ack_file = os.path.join("Reports", "proxy-control-ack.json")
                    with open(ack_file, "w", encoding="utf-8") as af:
                        _json.dump(ack, af)
                except Exception as e:
                    log.error(f"Control file error: {e}", exc_info=True)

            stop_event.wait(timeout=1)
    except Exception as e:
        log.error(f"Proxy serve error: {e}", exc_info=True)
        return 99
    finally:
        pm.stop()
        try:
            os.remove(pid_file)
        except OSError:
            pass
        log.info("Proxy stopped.")

    return 0


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python main.py <test.json>", file=sys.stderr)
        return 1

    # Internal --serve mode for background proxy
    if sys.argv[1] == "--serve":
        return _run_serve_mode(sys.argv[2:])

    json_path = sys.argv[1]

    if not os.path.isfile(json_path):
        Logger.output_file_not_found(json_path)
        return 5

    # Optional: --show-all flag to show PASSED remarks too
    if "--show-all" in sys.argv:
        Logger.show_failures_only = False

    try:
        engine = TestEngine(log_dir="Logs")
        result = engine.execute_test_suite(json_path)
        return 0 if result.success else 1
    except KeyboardInterrupt:
        print("\n[INTERRUPTED] Ctrl+C received outside recording step. Exiting.", flush=True)
        return 1
    except Exception as e:
        Logger.output_critical_error(str(e))
        return 99


if __name__ == "__main__":
    _code = main()
    # Clean up PyInstaller temp directory before forced exit.
    # os._exit() skips atexit handlers, so PyInstaller can't clean up _MEIxxxxxx.
    _mei_dir = getattr(sys, '_MEIPASS', None)
    if _mei_dir:
        import shutil
        _parent_dir = os.path.dirname(_mei_dir)
        # PyInstaller uses a sibling _MEIxxxxxx directory for onefile cleanup
        for _entry in os.listdir(os.path.dirname(_mei_dir)):
            _candidate = os.path.join(os.path.dirname(_mei_dir), _entry)
            if _entry.startswith('_MEI') and _candidate != _mei_dir and os.path.isdir(_candidate):
                try:
                    shutil.rmtree(_candidate, ignore_errors=True)
                except Exception:
                    pass
    # Use os._exit() for test mode to ensure immediate termination.
    # sys.exit() can hang if library imports left non-daemon threads.
    # --serve mode already exits cleanly from _run_serve_mode().
    os._exit(_code)

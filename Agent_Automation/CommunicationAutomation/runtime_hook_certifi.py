"""PyInstaller runtime hook: ensure certifi.where() returns a stable CA path.

When running as a onefile EXE, mitmproxy calls certifi.where() directly
(NOT SSL_CERT_FILE) to find the CA bundle. The _MEIPASS temp dir can be
cleaned up unexpectedly (parent process exit, antivirus, etc.) while a
background proxy subprocess is still running.

This hook copies the CA bundle to a stable location and monkey-patches
certifi.where() to return it, *before* any TLS code runs.
"""
import os
import sys

_meipass = getattr(sys, '_MEIPASS', None)
if _meipass:
    _src = os.path.join(_meipass, 'certifi', 'cacert.pem')
    if os.path.isfile(_src):
        # Copy to a stable location outside the temp dir
        _stable = os.path.join(os.getcwd(), 'Reports', 'cacert.pem')
        try:
            os.makedirs(os.path.dirname(_stable), exist_ok=True)
            import shutil
            shutil.copy2(_src, _stable)
            _stable = os.path.abspath(_stable)
            os.environ['SSL_CERT_FILE'] = _stable

            # Monkey-patch certifi.where() — mitmproxy calls this directly
            # in mitmproxy/net/tls.py, NOT the SSL_CERT_FILE env var
            import certifi
            certifi.where = lambda: _stable
        except Exception:
            # Fall back to temp dir path if anything fails
            os.environ.setdefault('SSL_CERT_FILE', _src)

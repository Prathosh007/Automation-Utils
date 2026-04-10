import os
import subprocess


class CertificateManager:
    """Windows certificate store operations via certutil."""

    MITMPROXY_DIR = os.path.join(os.path.expanduser("~"), ".mitmproxy")
    DEFAULT_CERT_PATH = os.path.join(MITMPROXY_DIR, "mitmproxy-ca-cert.cer")

    @staticmethod
    def _ensure_ca_cert_exists() -> tuple[bool, str]:
        """Generate mitmproxy CA cert if it doesn't exist yet."""
        if os.path.exists(CertificateManager.DEFAULT_CERT_PATH):
            return True, "CA cert already exists"

        try:
            from mitmproxy.certs import CertStore
            os.makedirs(CertificateManager.MITMPROXY_DIR, exist_ok=True)
            CertStore.from_store(
                path=CertificateManager.MITMPROXY_DIR,
                basename="mitmproxy",
                key_size=2048,
            )
            if os.path.exists(CertificateManager.DEFAULT_CERT_PATH):
                return True, "CA cert generated successfully"
            return False, "CertStore created but .cer file not found"
        except Exception as e:
            return False, f"Failed to generate CA cert: {e}"

    @staticmethod
    def install_certificate(cert_path: str = "") -> tuple[bool, str]:
        """Install mitmproxy CA certificate into the Windows Trusted Root store."""
        path = cert_path or CertificateManager.DEFAULT_CERT_PATH

        # Auto-generate if using default path and it doesn't exist
        if not cert_path and not os.path.exists(path):
            ok, msg = CertificateManager._ensure_ca_cert_exists()
            if not ok:
                return False, msg

        if not os.path.exists(path):
            return False, f"Certificate file not found: {path}"

        try:
            result = subprocess.run(
                ["certutil", "-addstore", "Root", path],
                capture_output=True, text=True, timeout=30
            )
            if result.returncode == 0:
                return True, "Certificate installed successfully"
            return False, f"certutil failed: {result.stderr.strip()}"
        except subprocess.TimeoutExpired:
            return False, "certutil timed out"
        except FileNotFoundError:
            return False, "certutil not found - not running on Windows?"
        except Exception as e:
            return False, f"Certificate install error: {e}"

    @staticmethod
    def remove_certificate(cert_path: str = "") -> tuple[bool, str]:
        """Remove mitmproxy CA certificate from the Windows Trusted Root store."""
        try:
            result = subprocess.run(
                ["certutil", "-delstore", "Root", "mitmproxy"],
                capture_output=True, text=True, timeout=30
            )
            if result.returncode == 0:
                return True, "Certificate removed successfully"
            return False, f"certutil remove failed: {result.stderr.strip()}"
        except subprocess.TimeoutExpired:
            return False, "certutil timed out"
        except FileNotFoundError:
            return False, "certutil not found - not running on Windows?"
        except Exception as e:
            return False, f"Certificate remove error: {e}"

    @staticmethod
    def is_certificate_installed() -> bool:
        """Check if the mitmproxy CA certificate is installed."""
        try:
            result = subprocess.run(
                ["certutil", "-verifystore", "Root", "mitmproxy"],
                capture_output=True, text=True, timeout=15
            )
            return result.returncode == 0
        except Exception:
            return False

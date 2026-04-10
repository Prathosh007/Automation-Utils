import base64
import os
import shutil

from core.base_action import BaseAction
from core.models import Command, CommandResult


class LoadClientCertAction(BaseAction):
    """Load agent client cert + key, combine into a single PEM for mTLS forwarding.

    Reads key.pem and client.pem from the UEMS agent vault and prepares
    a combined PEM file that mitmproxy uses when connecting to the real server.

    Handles multiple formats: standard PEM, DER (binary ASN.1), hex-encoded DER.
    Auto-detects format and converts to PEM if needed.
    """

    DEFAULT_VAULT = r"C:\Program Files (x86)\ManageEngine\UEMS_Agent\vault\ME_UEMS\storage"

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        vault = self.DEFAULT_VAULT
        key_path = command.client_key_path or os.path.join(vault, "key.pem")
        cert_path = command.client_cert_path or os.path.join(vault, "client.pem")

        # Diagnostics
        if self.logger:
            self.logger.log_to_file(f"Client key path:  {key_path}")
            self.logger.log_to_file(f"Client cert path: {cert_path}")
            self.logger.log_to_file(f"Key exists: {os.path.exists(key_path)}")
            self.logger.log_to_file(f"Cert exists: {os.path.exists(cert_path)}")
            if os.path.isdir(vault):
                files = os.listdir(vault)
                self.logger.log_to_file(f"Vault contents: {files}")
                for f in files:
                    fp = os.path.join(vault, f)
                    try:
                        sz = os.path.getsize(fp)
                        self.logger.log_to_file(f"  {f}: {sz} bytes")
                    except Exception as e:
                        self.logger.log_to_file(f"  {f}: error getting size - {e}")

        if not os.path.exists(key_path):
            self.handle_failure(command, result,
                                f"[load_client_cert] Error: Client key not found at '{key_path}' | Result: FAILED")
            return result
        if not os.path.exists(cert_path):
            self.handle_failure(command, result,
                                f"[load_client_cert] Error: Client cert not found at '{cert_path}' | Result: FAILED")
            return result

        # Prepare output path
        combined_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "..", "Reports", "agent_client_combined.pem"
        )
        combined_path = os.path.normpath(combined_path)
        os.makedirs(os.path.dirname(combined_path), exist_ok=True)

        # Copy files first to avoid locked file issues
        temp_key = combined_path + ".key.tmp"
        temp_cert = combined_path + ".cert.tmp"

        try:
            if self.logger:
                self.logger.log_to_file("Copying key.pem to temp location...")
            shutil.copy2(key_path, temp_key)
            if self.logger:
                self.logger.log_to_file("Copying client.pem to temp location...")
            shutil.copy2(cert_path, temp_cert)
        except PermissionError as e:
            self.handle_failure(command, result,
                                f"[load_client_cert] Error: Permission denied reading vault files "
                                f"(is agent service running?) - {e} | Result: FAILED")
            return result
        except Exception as e:
            self.handle_failure(command, result,
                                f"[load_client_cert] Error: Failed to copy cert files from vault - {e} | Result: FAILED")
            return result

        # Read from copies
        try:
            with open(temp_key, "rb") as f:
                key_data = f.read()
            with open(temp_cert, "rb") as f:
                cert_data = f.read()

            if self.logger:
                self.logger.log_to_file(f"Key data: {len(key_data)} bytes, starts: {key_data[:40]}")
                self.logger.log_to_file(f"Cert data: {len(cert_data)} bytes, starts: {cert_data[:40]}")
        except Exception as e:
            self.handle_failure(command, result,
                                f"[load_client_cert] Error: Failed to read temp cert files - {e} | Result: FAILED")
            return result
        finally:
            for tmp in (temp_key, temp_cert):
                try:
                    os.remove(tmp)
                except OSError:
                    pass

        # Try to convert to proper PEM format using cryptography library
        key_pem = None
        cert_pem = None
        conversion_errors = []

        try:
            key_pem = self._to_pem_key(key_data)
            if self.logger:
                self.logger.log_to_file(f"Key converted to PEM: {key_pem[:50]}...")
        except Exception as e:
            conversion_errors.append(f"key: {e}")
            if self.logger:
                self.logger.log_to_file(f"WARNING: Cannot convert key to PEM: {e}")

        try:
            cert_pem = self._to_pem_cert(cert_data)
            if self.logger:
                self.logger.log_to_file(f"Cert converted to PEM: {cert_pem[:50]}...")
        except Exception as e:
            conversion_errors.append(f"cert: {e}")
            if self.logger:
                self.logger.log_to_file(f"WARNING: Cannot convert cert to PEM: {e}")

        if key_pem and cert_pem:
            # Both converted successfully — write combined PEM
            try:
                with open(combined_path, "wb") as f:
                    f.write(key_pem)
                    if not key_pem.endswith(b"\n"):
                        f.write(b"\n")
                    f.write(cert_pem)
                    if not cert_pem.endswith(b"\n"):
                        f.write(b"\n")
            except Exception as e:
                self.handle_failure(command, result,
                                    f"[load_client_cert] Error: Failed to write combined cert - {e} | Result: FAILED")
                return result

            context["client_cert_combined"] = combined_path
            result.data = combined_path

            # Hot-reload into running proxy if available (releases held post-CSR requests)
            hot_reloaded = False
            pm = context.get("proxy_manager")
            if pm and pm.is_running:
                try:
                    pm.update_client_certs(combined_path)
                    hot_reloaded = True
                    if self.logger:
                        self.logger.log_to_file("Client cert hot-reloaded into running proxy")
                except Exception as e:
                    if self.logger:
                        self.logger.log_to_file(f"WARNING: Hot-reload failed: {e}")

            reload_note = " (hot-reloaded into proxy)" if hot_reloaded else ""
            self.handle_success(
                command, result,
                f"[load_client_cert] Cert: {os.path.basename(cert_path)} + {os.path.basename(key_path)} | "
                f"Size: {len(key_pem)}+{len(cert_pem)} bytes PEM{reload_note} | Result: PASSED"
            )
        else:
            # Vault files are in proprietary/encrypted format — continue without mTLS
            # Proxy will still capture request data (URLs, headers, bodies)
            context["client_cert_combined"] = ""  # Empty = no client cert
            context["client_cert_warning"] = "; ".join(conversion_errors)
            result.data = None

            # Release any held requests even without certs (don't hold forever)
            pm = context.get("proxy_manager")
            if pm and pm.is_running:
                try:
                    pm.update_client_certs("")  # Release hold, no certs
                    if self.logger:
                        self.logger.log_to_file("Released held requests (no usable certs)")
                except Exception:
                    pass

            self.handle_success(
                command, result,
                f"[load_client_cert] Cert files found but in proprietary format (skipping mTLS) | "
                f"Proxy will still capture request data | "
                f"Details: {'; '.join(conversion_errors)} | Result: PASSED"
            )
        return result

    def _to_pem_key(self, data: bytes) -> bytes:
        """Convert key data to PEM format. Handles: PEM, DER, hex-encoded DER."""
        from cryptography.hazmat.primitives.serialization import (
            Encoding, PrivateFormat, NoEncryption,
            load_pem_private_key, load_der_private_key,
        )

        # Already PEM?
        if b"-----BEGIN" in data:
            # Validate it parses
            load_pem_private_key(data, password=None)
            return data

        # Try DER binary directly
        der_data = self._try_decode_to_der(data)

        # Try loading as various key types
        key = load_der_private_key(der_data, password=None)
        return key.private_bytes(Encoding.PEM, PrivateFormat.TraditionalOpenSSL, NoEncryption())

    def _to_pem_cert(self, data: bytes) -> bytes:
        """Convert cert data to PEM format. Handles: PEM, DER, hex-encoded DER."""
        from cryptography.x509 import load_pem_x509_certificate, load_der_x509_certificate
        from cryptography.hazmat.primitives.serialization import Encoding

        # Already PEM?
        if b"-----BEGIN" in data:
            load_pem_x509_certificate(data)
            return data

        # Try DER binary directly
        der_data = self._try_decode_to_der(data)

        cert = load_der_x509_certificate(der_data)
        return cert.public_bytes(Encoding.PEM)

    def _try_decode_to_der(self, data: bytes) -> bytes:
        """Try to interpret data as DER. If it looks like hex, decode hex first."""
        # Check if it's hex-encoded (all bytes are hex chars: 0-9, A-F, a-f)
        try:
            text = data.decode("ascii").strip()
            if all(c in "0123456789abcdefABCDEF" for c in text):
                if self.logger:
                    self.logger.log_to_file("Data appears to be hex-encoded, decoding...")
                return bytes.fromhex(text)
        except (UnicodeDecodeError, ValueError):
            pass

        # Check if it's base64-encoded (without PEM headers)
        try:
            text = data.decode("ascii").strip()
            decoded = base64.b64decode(text, validate=True)
            if len(decoded) > 10:  # Reasonable DER size
                if self.logger:
                    self.logger.log_to_file("Data appears to be base64-encoded, decoding...")
                return decoded
        except Exception:
            pass

        # Assume raw DER binary
        if self.logger:
            self.logger.log_to_file("Treating data as raw DER binary")
        return data

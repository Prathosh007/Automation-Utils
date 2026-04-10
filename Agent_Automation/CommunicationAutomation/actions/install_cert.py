from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.certificate_manager import CertificateManager


class InstallCertAction(BaseAction):
    """Install mitmproxy CA certificate into the Windows trust store."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command)

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        ok, msg = CertificateManager.install_certificate(command.cert_path)

        if ok:
            self.handle_success(command, result,
                                f"[install_cert] {msg} | Result: PASSED")
        else:
            self.handle_failure(command, result,
                                f"[install_cert] {msg} | Result: FAILED")
        return result

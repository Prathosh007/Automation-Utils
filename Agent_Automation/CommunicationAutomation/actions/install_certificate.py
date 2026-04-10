from core.base_action import BaseAction
from core.models import Command, CommandResult
from proxy.certificate_manager import CertificateManager


class InstallCertificateAction(BaseAction):
    """Install or remove mitmproxy CA certificate in the Windows trust store."""

    def validate_command(self, command: Command) -> bool:
        return super().validate_command(command) and command.cert_action in ("install", "remove")

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        if command.cert_action == "install":
            ok, msg = CertificateManager.install_certificate(command.cert_path)
        else:
            ok, msg = CertificateManager.remove_certificate(command.cert_path)

        if ok:
            self.handle_success(command, result,
                                f"[install_certificate] Action: {command.cert_action} | {msg} | Result: PASSED")
        else:
            self.handle_failure(command, result,
                                f"[install_certificate] Action: {command.cert_action} | {msg} | Result: FAILED")
        return result

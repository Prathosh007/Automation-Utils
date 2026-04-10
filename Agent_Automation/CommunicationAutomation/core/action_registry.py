from core.base_action import BaseAction
from actions.start_proxy import StartProxyAction
from actions.stop_proxy import StopProxyAction
from actions.configure_proxy import ConfigureProxyAction
from actions.install_certificate import InstallCertificateAction
from actions.install_cert import InstallCertAction
from actions.remove_certificate import RemoveCertificateAction
from actions.load_client_cert import LoadClientCertAction
from actions.wait_for_request import WaitForRequestAction
from actions.validate_request import ValidateRequestAction
from actions.validate_response import ValidateResponseAction
from actions.modify_response import ModifyResponseAction
from actions.block_request import BlockRequestAction
from actions.assert_request_count import AssertRequestCountAction
from actions.assert_request_order import AssertRequestOrderAction
from actions.capture_traffic import CaptureTrafficAction
from actions.load_traffic import LoadTrafficAction
from actions.clear_captures import ClearCapturesAction
from actions.switch_capture_file import SwitchCaptureFileAction
from actions.wait_for_interrupt import WaitForInterruptAction
from actions.traffic_lock import TrafficLockAction, TrafficUnlockAction


class ActionRegistry:
    """Maps action name strings to handler instances, mirroring ActionRegistry.cs."""

    def __init__(self):
        self._actions: dict[str, BaseAction] = {
            "start_proxy": StartProxyAction(),
            "stop_proxy": StopProxyAction(),
            "configure_proxy": ConfigureProxyAction(),
            "install_certificate": InstallCertificateAction(),
            "install_cert": InstallCertAction(),
            "remove_certificate": RemoveCertificateAction(),
            "load_client_cert": LoadClientCertAction(),
            "wait_for_request": WaitForRequestAction(),
            "validate_request": ValidateRequestAction(),
            "validate_response": ValidateResponseAction(),
            "modify_response": ModifyResponseAction(),
            "block_request": BlockRequestAction(),
            "assert_request_count": AssertRequestCountAction(),
            "assert_request_order": AssertRequestOrderAction(),
            "capture_traffic": CaptureTrafficAction(),
            "load_traffic": LoadTrafficAction(),
            "clear_captures": ClearCapturesAction(),
            "switch_capture_file": SwitchCaptureFileAction(),
            "wait_for_interrupt": WaitForInterruptAction(),
            "traffic_lock": TrafficLockAction(),
            "traffic_unlock": TrafficUnlockAction(),
        }

    def get_action(self, action_name: str) -> BaseAction:
        """Look up an action by name (case-insensitive)."""
        key = action_name.lower().strip()
        action = self._actions.get(key)
        if action is None:
            raise NotImplementedError(f"Action '{action_name}' is not supported")
        return action

    def register_action(self, name: str, action: BaseAction) -> None:
        """Register or override an action."""
        self._actions[name.lower().strip()] = action

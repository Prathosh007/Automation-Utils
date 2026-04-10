import winreg

from core.base_action import BaseAction
from core.models import Command, CommandResult


class PatchRegistryAction(BaseAction):
    """Read all values from a registry key and replace matching addresses/ports.

    Patches every value that contains any of the old addresses/ports.
    Stores originals in context for automatic restoration.

    Command fields:
        registry_key:    Full key path (e.g. "HKLM\\SOFTWARE\\...")
        old_values:      List of strings to find (e.g. ["naveen-14097", "10.69.73.19", "8383"])
        new_values:      List of replacement strings (same order as old_values)
        registry_action: "patch" or "restore"
    """

    def validate_command(self, command: Command) -> bool:
        return (super().validate_command(command)
                and bool(command.registry_key)
                and command.registry_action in ("patch", "restore"))

    def execute(self, command: Command, context: dict) -> CommandResult:
        result = self.create_result(command)

        if command.registry_action == "patch":
            return self._patch(command, context, result)
        else:
            return self._restore(command, context, result)

    def _patch(self, command: Command, context: dict, result: CommandResult) -> CommandResult:
        root_key, sub_key = self._parse_key(command.registry_key)
        if root_key is None:
            self.handle_failure(command, result,
                                f"[patch_registry] Key: '{command.registry_key}' | "
                                "Error: Invalid registry key | Result: FAILED")
            return result

        old_values = command.old_values
        new_values = command.new_values

        if not old_values or not new_values or len(old_values) != len(new_values):
            self.handle_failure(command, result,
                                "[patch_registry] Error: old_values and new_values must be "
                                "non-empty lists of equal length | Result: FAILED")
            return result

        try:
            key = winreg.OpenKey(root_key, sub_key, 0,
                                 winreg.KEY_READ | winreg.KEY_WRITE | winreg.KEY_WOW64_32KEY)
        except FileNotFoundError:
            self.handle_failure(command, result,
                                f"[patch_registry] Key: '{command.registry_key}' | "
                                "Error: Registry key not found | Result: FAILED")
            return result
        except PermissionError:
            self.handle_failure(command, result,
                                "[patch_registry] Error: Permission denied - run as Administrator | Result: FAILED")
            return result

        originals = {}
        patched = []
        skipped = []

        try:
            # Read all values
            i = 0
            while True:
                try:
                    name, data, reg_type = winreg.EnumValue(key, i)
                    i += 1

                    if self.logger:
                        self.logger.log_to_file(f"  Registry value: {name} = {data} (type={reg_type})")

                    # Only patch string types
                    if reg_type not in (winreg.REG_SZ, winreg.REG_EXPAND_SZ):
                        skipped.append(name)
                        continue

                    original_data = str(data)
                    new_data = original_data

                    # Apply all replacements (case-insensitive)
                    for old_val, new_val in zip(old_values, new_values):
                        if old_val.lower() in new_data.lower():
                            # Case-insensitive replace
                            import re
                            new_data = re.sub(re.escape(old_val), new_val, new_data, flags=re.IGNORECASE)

                    if new_data != original_data:
                        originals[name] = (original_data, reg_type)
                        winreg.SetValueEx(key, name, 0, reg_type, new_data)
                        patched.append(f"{name}: {original_data} -> {new_data}")
                        if self.logger:
                            self.logger.log_to_file(f"  PATCHED: {name}: {original_data} -> {new_data}")
                    else:
                        skipped.append(name)

                except OSError:
                    break  # No more values

        finally:
            winreg.CloseKey(key)

        # Store originals for restore
        context["registry_originals"] = originals
        context["registry_key"] = command.registry_key
        result.data = {"patched": patched, "skipped": skipped}

        if patched:
            summary = "; ".join(patched)
            self.handle_success(command, result,
                                f"[patch_registry] Key: '{command.registry_key}' | "
                                f"Patched: {len(patched)} value(s) - {summary} | Result: PASSED")
        else:
            self.handle_success(command, result,
                                f"[patch_registry] Key: '{command.registry_key}' | "
                                "No values matched - nothing to patch | Result: PASSED")
        return result

    def _restore(self, command: Command, context: dict, result: CommandResult) -> CommandResult:
        originals = context.get("registry_originals")
        reg_key = context.get("registry_key", command.registry_key)

        if not originals:
            self.handle_success(command, result,
                                "[patch_registry] Action: restore | Nothing to restore | Result: PASSED")
            return result

        root_key, sub_key = self._parse_key(reg_key)
        if root_key is None:
            self.handle_failure(command, result,
                                f"[patch_registry] Action: restore | Key: '{reg_key}' | "
                                "Error: Invalid registry key | Result: FAILED")
            return result

        try:
            key = winreg.OpenKey(root_key, sub_key, 0,
                                 winreg.KEY_WRITE | winreg.KEY_WOW64_32KEY)
        except Exception as e:
            self.handle_failure(command, result,
                                f"[patch_registry] Action: restore | Key: '{reg_key}' | "
                                f"Error: Cannot open registry - {e} | Result: FAILED")
            return result

        restored = []
        try:
            for name, (original_data, reg_type) in originals.items():
                try:
                    winreg.SetValueEx(key, name, 0, reg_type, original_data)
                    restored.append(name)
                    if self.logger:
                        self.logger.log_to_file(f"  RESTORED: {name} = {original_data}")
                except Exception as e:
                    if self.logger:
                        self.logger.log_to_file(f"  RESTORE FAILED: {name} - {e}")
        finally:
            winreg.CloseKey(key)

        self.handle_success(command, result,
                            f"[patch_registry] Action: restore | "
                            f"Restored: {len(restored)} value(s) - {', '.join(restored)} | Result: PASSED")
        return result

    @staticmethod
    def _parse_key(key_path: str):
        """Parse 'HKLM\\SOFTWARE\\...' into (root_handle, subkey_string)."""
        roots = {
            "HKLM": winreg.HKEY_LOCAL_MACHINE,
            "HKEY_LOCAL_MACHINE": winreg.HKEY_LOCAL_MACHINE,
            "HKCU": winreg.HKEY_CURRENT_USER,
            "HKEY_CURRENT_USER": winreg.HKEY_CURRENT_USER,
        }
        parts = key_path.replace("/", "\\").split("\\", 1)
        if len(parts) != 2:
            return None, None
        root = roots.get(parts[0].upper())
        if root is None:
            return None, None
        return root, parts[1]

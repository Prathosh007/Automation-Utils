"""Unit tests for patch_registry action (mocked — no real registry access)."""

import pytest
from unittest.mock import patch, MagicMock
from tests.conftest import make_command
from actions.patch_registry import PatchRegistryAction


@pytest.fixture
def action(logger):
    a = PatchRegistryAction()
    a.set_logger(logger)
    return a


def _make_mock_winreg():
    """Create a consistent mock winreg module."""
    m = MagicMock()
    m.HKEY_LOCAL_MACHINE = 0x80000002
    m.HKEY_CURRENT_USER = 0x80000001
    m.KEY_READ = 0x20019
    m.KEY_WRITE = 0x20006
    m.KEY_WOW64_32KEY = 0x0200
    m.REG_SZ = 1
    m.REG_EXPAND_SZ = 2
    m.REG_DWORD = 4
    return m


class TestPatchRegistryPatch:
    """Test patching registry values."""

    @patch("actions.patch_registry.winreg")
    def test_patch_replaces_values(self, mock_wr, action, empty_context):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.HKEY_CURRENT_USER = 0x80000001
        mock_wr.KEY_READ = 0x20019
        mock_wr.KEY_WRITE = 0x20006
        mock_wr.KEY_WOW64_32KEY = 0x0200
        mock_wr.REG_SZ = 1
        mock_wr.REG_EXPAND_SZ = 2

        mock_key = MagicMock()
        mock_wr.OpenKey.return_value = mock_key

        values = [
            ("ServerURL", "https://naveen-14097:8383/api", 1),
            ("BackupURL", "https://10.69.73.19:8383/backup", 1),
        ]
        call_idx = [0]
        def enum_side_effect(key, idx):
            if call_idx[0] >= len(values):
                raise OSError("no more")
            v = values[call_idx[0]]
            call_idx[0] += 1
            return v
        mock_wr.EnumValue.side_effect = enum_side_effect

        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="patch",
            oldValues=["naveen-14097", "10.69.73.19", "8383"],
            newValues=["127.0.0.1", "127.0.0.1", "8080"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True
        assert "registry_originals" in empty_context
        assert len(empty_context["registry_originals"]) == 2

    @patch("actions.patch_registry.winreg")
    def test_patch_no_matches(self, mock_wr, action, empty_context):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.KEY_READ = 0x20019
        mock_wr.KEY_WRITE = 0x20006
        mock_wr.KEY_WOW64_32KEY = 0x0200
        mock_wr.REG_SZ = 1
        mock_wr.REG_EXPAND_SZ = 2

        mock_key = MagicMock()
        mock_wr.OpenKey.return_value = mock_key

        values = [("UnrelatedKey", "some_value", 1)]
        call_idx = [0]
        def enum_side_effect(key, idx):
            if call_idx[0] >= len(values):
                raise OSError("no more")
            v = values[call_idx[0]]
            call_idx[0] += 1
            return v
        mock_wr.EnumValue.side_effect = enum_side_effect

        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="patch",
            oldValues=["naveen-14097"],
            newValues=["127.0.0.1"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True

    @patch("actions.patch_registry.winreg")
    def test_patch_key_not_found(self, mock_wr, action, empty_context):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.KEY_READ = 0x20019
        mock_wr.KEY_WRITE = 0x20006
        mock_wr.KEY_WOW64_32KEY = 0x0200
        mock_wr.OpenKey.side_effect = FileNotFoundError("key not found")

        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\NonExistent",
            registryAction="patch",
            oldValues=["a"],
            newValues=["b"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "not found" in result.error_message.lower()

    @patch("actions.patch_registry.winreg")
    def test_patch_permission_denied(self, mock_wr, action, empty_context):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.KEY_READ = 0x20019
        mock_wr.KEY_WRITE = 0x20006
        mock_wr.KEY_WOW64_32KEY = 0x0200
        mock_wr.OpenKey.side_effect = PermissionError("access denied")

        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\Protected",
            registryAction="patch",
            oldValues=["a"],
            newValues=["b"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "permission" in result.error_message.lower() or "administrator" in result.error_message.lower()

    def test_patch_mismatched_lists(self, action, empty_context):
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="patch",
            oldValues=["a", "b"],
            newValues=["c"],
        )
        result = action.execute(cmd, empty_context)
        assert result.success is False
        assert "equal length" in result.error_message.lower()


class TestPatchRegistryRestore:
    """Test restoring registry values."""

    @patch("actions.patch_registry.winreg")
    def test_restore_success(self, mock_wr, action):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.KEY_WRITE = 0x20006
        mock_wr.KEY_WOW64_32KEY = 0x0200
        mock_wr.REG_SZ = 1

        mock_key = MagicMock()
        mock_wr.OpenKey.return_value = mock_key

        ctx = {
            "registry_originals": {
                "ServerURL": ("https://naveen-14097:8383/api", 1),
            },
            "registry_key": "HKLM\\SOFTWARE\\TestApp",
        }
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="restore",
        )
        result = action.execute(cmd, ctx)
        assert result.success is True

    def test_restore_nothing(self, action, empty_context):
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="restore",
        )
        result = action.execute(cmd, empty_context)
        assert result.success is True


class TestPatchRegistryKeyParsing:
    """Test registry key path parsing."""

    @patch("actions.patch_registry.winreg")
    def test_hklm(self, mock_wr, action):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.HKEY_CURRENT_USER = 0x80000001
        root, sub = PatchRegistryAction._parse_key("HKLM\\SOFTWARE\\TestApp")
        assert root == mock_wr.HKEY_LOCAL_MACHINE
        assert sub == "SOFTWARE\\TestApp"

    @patch("actions.patch_registry.winreg")
    def test_hkcu(self, mock_wr, action):
        mock_wr.HKEY_LOCAL_MACHINE = 0x80000002
        mock_wr.HKEY_CURRENT_USER = 0x80000001
        root, sub = PatchRegistryAction._parse_key("HKCU\\Software\\MyApp")
        assert root == mock_wr.HKEY_CURRENT_USER
        assert sub == "Software\\MyApp"

    def test_invalid_root(self, action):
        root, sub = PatchRegistryAction._parse_key("INVALID\\Path")
        assert root is None

    def test_no_separator(self, action):
        root, sub = PatchRegistryAction._parse_key("HKLM")
        assert root is None


class TestPatchRegistryValidation:
    """Test validate_command."""

    def test_valid_patch(self, action):
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="patch",
        )
        assert action.validate_command(cmd) is True

    def test_valid_restore(self, action):
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="restore",
        )
        assert action.validate_command(cmd) is True

    def test_missing_key(self, action):
        cmd = make_command(action="patch_registry", registryAction="patch")
        assert action.validate_command(cmd) is False

    def test_invalid_action(self, action):
        cmd = make_command(
            action="patch_registry",
            registryKey="HKLM\\SOFTWARE\\TestApp",
            registryAction="delete",
        )
        assert action.validate_command(cmd) is False

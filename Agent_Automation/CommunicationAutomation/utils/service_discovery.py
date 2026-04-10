"""Discover UEMS Agent installation paths from the Windows service registry."""

import logging
import os
import winreg

logger = logging.getLogger(__name__)

# Service display name used to locate the agent
UEMS_SERVICE_DISPLAY = "ManageEngine UEMS - Agent"

# Fallback service names to search for
UEMS_SERVICE_NAMES = [
    "ManageEngine UEMS - Agent",
    "YOURCOMPANY UEMS - Agent",
]

# Registry path where Windows stores service config
_SERVICES_KEY = r"SYSTEM\CurrentControlSet\Services"


def get_agent_install_dir(service_hint: str = UEMS_SERVICE_DISPLAY) -> str:
    """Discover the UEMS Agent installation directory from the Windows service.

    Reads the ImagePath of the service from the registry, extracts the base
    directory by stripping the binary subfolder (e.g. ``bin\\dcagentservice.exe``).

    Args:
        service_hint: Display name or service name to search for.

    Returns:
        The base installation directory (e.g. ``C:\\Program Files (x86)\\ManageEngine\\UEMS_Agent``),
        or an empty string if not found.
    """
    image_path = _get_service_image_path(service_hint)
    if not image_path:
        # Try common fallback service names
        for name in UEMS_SERVICE_NAMES:
            image_path = _get_service_image_path(name)
            if image_path:
                break

    if not image_path:
        logger.warning("Could not find UEMS Agent service image path")
        return ""

    # image_path is something like:
    #   "C:\Program Files (x86)\ManageEngine\UEMS_Agent\bin\dcagentservice.exe"
    # Or with quotes: "\"C:\Program Files (x86)\...\dcagentservice.exe\""
    clean = image_path.strip().strip('"')

    # Walk up from the exe: strip the filename, then strip 'bin' directory
    exe_dir = os.path.dirname(clean)          # ...\UEMS_Agent\bin
    parent = os.path.dirname(exe_dir)         # ...\UEMS_Agent

    # Verify the parent looks correct (should not be empty or root)
    if os.path.isdir(parent):
        logger.info(f"Agent install dir from service: {parent}")
        return parent

    logger.warning(f"Derived install dir does not exist: {parent}")
    return ""


def get_agent_vault_path(service_hint: str = UEMS_SERVICE_DISPLAY) -> str:
    """Get the agent vault storage path: ``<install_dir>\\vault\\ME_UEMS\\storage``.

    Args:
        service_hint: Display name or service name to search for.

    Returns:
        Full path to the vault storage directory, or empty string if not found.
    """
    install_dir = get_agent_install_dir(service_hint)
    if not install_dir:
        return ""

    vault = os.path.join(install_dir, "vault", "ME_UEMS", "storage")
    logger.info(f"Agent vault path: {vault} (exists={os.path.isdir(vault)})")
    return vault


def _get_service_image_path(service_name: str) -> str:
    """Look up a service's ImagePath from the registry.

    Tries two strategies:
    1. Enumerate all services and match by DisplayName.
    2. Try opening the key directly by service_name.
    """
    # Strategy 1: match by DisplayName
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, _SERVICES_KEY) as root:
            i = 0
            while True:
                try:
                    sub_name = winreg.EnumKey(root, i)
                    i += 1
                    try:
                        with winreg.OpenKey(root, sub_name) as sub:
                            display, _ = winreg.QueryValueEx(sub, "DisplayName")
                            if service_name.lower() in str(display).lower():
                                img, _ = winreg.QueryValueEx(sub, "ImagePath")
                                logger.info(f"Found service '{display}' -> {img}")
                                return str(img)
                    except (FileNotFoundError, OSError):
                        continue
                except OSError:
                    break
    except Exception as e:
        logger.debug(f"Registry enumeration failed: {e}")

    # Strategy 2: direct key lookup
    try:
        key_path = f"{_SERVICES_KEY}\\{service_name}"
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, key_path) as key:
            img, _ = winreg.QueryValueEx(key, "ImagePath")
            return str(img)
    except Exception:
        pass

    return ""

"""cx_Freeze build script for CommunicationAutomation."""
import sys
from cx_Freeze import setup, Executable

build_options = {
    "packages": [
        "mitmproxy",
        "mitmproxy_rs",
        "mitmproxy_windows",
        "asyncio",
        "threading",
        "winreg",
        "core",
        "actions",
        "proxy",
        "utils",
    ],
    "excludes": [
        "tkinter",
        "matplotlib",
        "numpy",
        "PIL",
        "pytest",
        "unittest",
    ],
    "include_files": [],
}

setup(
    name="CommunicationAutomation",
    version="1.0",
    description="UEMS Agent Traffic Capture",
    options={"build_exe": build_options},
    executables=[
        Executable(
            "main.py",
            target_name="CommunicationAutomation.exe",
            base=None,  # Console application
        )
    ],
)

# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec for CommunicationAutomation
# Build: pyinstaller CommunicationAutomation.spec

from PyInstaller.utils.hooks import collect_data_files, collect_dynamic_libs

# Avoid collect_all/collect_submodules - they spawn isolated subprocesses that
# crash (0xC0000142) when mitmproxy_rs Rust extensions fail to init in that context.
# Use filesystem-only collection instead.
mitm_datas = collect_data_files('mitmproxy')
mitm_binaries = collect_dynamic_libs('mitmproxy')
mitm_hiddenimports = []

rs_datas = collect_data_files('mitmproxy_rs')
rs_binaries = collect_dynamic_libs('mitmproxy_rs')
rs_hiddenimports = ['mitmproxy_rs']

win_datas = collect_data_files('mitmproxy_windows')
win_binaries = collect_dynamic_libs('mitmproxy_windows')
win_hiddenimports = ['mitmproxy_windows']

# certifi CA bundle — mitmproxy needs this for upstream TLS context
certifi_datas = collect_data_files('certifi')

a = Analysis(
    ['main.py'],
    pathex=['.'],
    binaries=mitm_binaries + rs_binaries + win_binaries,
    datas=mitm_datas + rs_datas + win_datas + certifi_datas,
    hiddenimports=(
        mitm_hiddenimports + rs_hiddenimports + win_hiddenimports +
        [
            # Core mitmproxy internals loaded dynamically
            'mitmproxy.addons',
            'mitmproxy.addons.export',
            'mitmproxy.addons.next_layer',
            'mitmproxy.addons.proxyserver',
            'mitmproxy.addons.tlsconfig',
            'mitmproxy.proxy.layers',
            'mitmproxy.proxy.layers.http',
            'mitmproxy.proxy.layers.tls',
            'mitmproxy.proxy.layers.quic',
            'mitmproxy.proxy.tunnel',
            'mitmproxy.net.tls',
            'mitmproxy.net.http',
            'mitmproxy.connection',
            'mitmproxy.coretypes',
            'mitmproxy.options',
            # Our project packages
            'core',
            'core.models',
            'core.action_registry',
            'core.base_action',
            'core.test_engine',
            'actions',
            'actions.start_proxy',
            'actions.stop_proxy',
            'actions.configure_proxy',
            'actions.install_certificate',
            'actions.wait_for_request',
            'actions.validate_request',
            'actions.validate_response',
            'actions.modify_response',
            'actions.block_request',
            'actions.assert_request_count',
            'actions.assert_request_order',
            'actions.capture_traffic',
            'actions.clear_captures',
            'actions.wait_for_interrupt',
            'proxy',
            'proxy.proxy_manager',
            'proxy.addon',
            'proxy.traffic_store',
            'proxy.response_rules',
            'proxy.certificate_manager',
            'utils',
            'utils.logger',
            'utils.variable_context',
            'utils.pattern_matcher',
            # Standard lib used at runtime
            'asyncio',
            'threading',
            'winreg',
            'certifi',
        ]
    ),
    hookspath=[],
    hooksconfig={},
    runtime_hooks=['runtime_hook_certifi.py'],
    excludes=[
        # Exclude heavy unused packages to reduce EXE size
        'tkinter',
        'matplotlib',
        'numpy',
        'PIL',
        'pytest',
    ],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='CommunicationAutomation',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,             # UPX can break mitmproxy-rs Rust extensions
    console=True,          # Must be True - GOAT server reads stdout
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    onefile=True,
)

# Certificate File Operation Reference 📄

This document describes the certificate file operations available in the GOAT automation framework, allowing you to extract values from certificate files, export certificates, and validate certificate properties for automation testing.

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Operations](#operations)
    - [Get Certificate Value](#get-certificate-value)
    - [Export Certificate](#export-certificate)
    - [Remove Trusted Root Certificate](#remove-trusted-root-certificate)
- [Special Handling](#special-handling)
- [Timeout Handling](#timeout-handling)
- [Common Parameters](#common-parameters)
- [Path Variables](#path-variables)
- [Examples](#examples)

## Overview

The certificate file operations provide functionality for extracting values from certificate files, exporting certificates from the Windows certificate store, and validating certificate properties in your test automation workflows.

## Quick Reference

| Icon | Action              | Description                                 |
|------|---------------------|---------------------------------------------|
| 🏷️   | `get_cert_value`    | Extract values from a certificate file      |
| 📤   | `export_certificate`| Export a certificate from Windows store     |
| 🗑️   | `remove_user_trusted_root_cert` | Remove a certificate from the LocalMachine Trusted Root store |

### 🏷️ Get Certificate Value

Extracts values from a certificate file, such as subject, issuer, validity dates, thumbprint, and more.

**Parameters:**
- `action`: `"get_cert_value"` (required)
- `cert_path`: Path to the certificate file (required unless exporting)
- `key_name`: Name of the property to extract (required). Supported values:
  - `subject`, `issuer`, `thumbprint`, `version`, `serialNumber`
  - `validFrom`, `validTo`
  - `signatureAlgorithm`, `signatureHashAlgorithm`
  - `publicKey`, `basicConstraints`
  - `subjectAlternativeName`, `subjectKeyIdentifier`, `authorityKeyIdentifier`
  - `keyUsage`, `enhancedKeyUsage`
  - `"all"` for all properties
- `expected_value`: Value to compare against the extracted property (optional)
- `system_cert_name`: Subject name to export from Windows certificate store (optional, triggers export before extraction)
- `export_path`: Path to export the certificate file (optional, used with `system_cert_name`)
- `delete_after_get_value`: `"true"` to delete the exported certificate after extraction (optional)

**Example:**
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "get_cert_value",
    "cert_path": "tool_home/certs/server.cer",
    "key_name": "subject",
    "expected_value": "CN=Server,O=Company,C=US"
  }
}
```

### 📤 Export Certificate

Exports a certificate from the Windows certificate store to a file.

**Parameters:**
- `action`: `"export_certificate"` (required)
- `system_cert_name`: Certificate name to search in Windows certificate store (required)
- `export_path`: Path to export the certificate file with file name (optional, default: `tool_home/exported_cert/cert.cer`)

**Example:**
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "export_certificate",
    "system_cert_name": "GoogleCA",
    "export_path": "tool_home/exported_cert/server.cer"
  }
}
```

### 🗑️ Remove Trusted Root Certificate
Removes a certificate from the Windows LocalMachine Trusted Root Certification Authorities store by thumbprint.

**Parameters:**
- `action`: `"remove_user_trusted_root_cert"` (required)
- `thumbprint`: Thumbprint of the certificate to remove (required)

**Example:**
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "remove_user_trusted_root_cert",
    "thumbprint": "ABCDEFG1234567890HIJKLMNOPQRSTUV"
  }
}
```


[//]: # (## Special Handling)

[//]: # ()
[//]: # (- If `system_cert_name` is provided, the framework first exports the certificate from the Windows store before extracting values.)

[//]: # (- If `delete_after_get_value` is `"true"`, the exported certificate file is deleted after extraction.)

[//]: # ()
[//]: # (## Timeout Handling)

[//]: # ()
[//]: # (- Certificate operations may involve external processes &#40;PowerShell&#41; for exporting or extracting values.)

[//]: # (- The framework handles process timeouts and logs errors if the operation does not complete.)

## Common Parameters

| Parameter             | Description                                                        |
|-----------------------|--------------------------------------------------------------------|
| `action`              | Specifies the certificate file operation to perform (required)     |
| `cert_path`           | Path to the certificate file (required for value extraction/import)|
| `key_name`            | Property name to extract (required for `get_cert_value`)           |
| `expected_value`      | Value to compare against the extracted property (optional)         |
| `system_cert_name`    | Subject name in Windows certificate store (optional)               |
| `export_path`         | Path to export the certificate file (optional)                     |
| `delete_after_get_value` | `"true"` to delete exported certificate after extraction (optional) |
| `thumbprint`          | Certificate thumbprint (required for `remove_user_trusted_root_cert`) |
| `store_name`          | Certificate store name (optional, default: `"Root"`)               |
| `store_location`      | Store location: `"CurrentUser"` or `"LocalMachine"` (optional, default: `"LocalMachine"`) |

[//]: # (## Path Variables)

[//]: # ()
[//]: # (The framework automatically resolves these path variables:)

[//]: # ()
[//]: # (| Variable      | Description                               |)

[//]: # (|---------------|-------------------------------------------|)

[//]: # (| `tool_home`   | GOAT tool installed directory             |)

[//]: # (| `server_home` | Server installation directory             |)

## Examples

### Extract Subject from Certificate
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "get_cert_value",
    "cert_path": "tool_home/certs/server.cer",
    "key_name": "subject",
    "note": "CERT_SUBJECT"
  }
}
```

### Export Certificate and Extract Thumbprint
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "get_cert_value",
    "system_cert_name": "CertificateName",
    "key_name": "thumbprint",
    "export_path": "tool_home/exported_cert/server.cer",
    "delete_after_get_value": "true",
    "note": "CERT_THUMBPRINT"
  }
}
```

### Validate Certificate Expiry
```json
{
  "operation_type": "certificate_operation",
  "parameters": {
    "action": "get_cert_value",
    "cert_path": "tool_home/certs/server.cer",
    "key_name": "validTo",
    "expected_value": "2025-12-31"
  }
}
```

---

> **Note:**
> The framework handles certificate parsing, exporting, value extraction, and cleanup automatically. On non-Windows platforms, PowerShell-based export may not be supported.
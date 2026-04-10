package com.me.testcases.fileOperation;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.me.testcases.DataBaseOperationHandler.saveNote;
import static com.me.testcases.ServerUtils.getToolServerHome;

/**
 * Handler for certificate file operations
 */
public class GetValueFromCertificatefile {
    private static final Logger LOGGER = LogManager.getLogger(GetValueFromCertificatefile.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a certificate operation
     *
     * @param op The operation containing certificate parameters
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        StringBuilder remarksBuilder = new StringBuilder();

        String action = op.getParameter("action");
        String certPath = op.getParameter("cert_path");
        String keyName = op.getParameter("key_name");
        String expectedValue = op.getParameter("expected_value");
        String systemPath = op.getParameter("system_cert_name");
        String exportLocation = op.getParameter("export_path");
        String deleteAfterGetValue = op.getParameter("delete_after_get_value");

        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for certificate operation");
            remarksBuilder.append("error=Action parameter is missing\n");
            op.setRemarks(remarksBuilder.toString());
            return false;
        }

        try {
            boolean result;
            switch (action.toLowerCase()) {
                case "get_cert_value":
                    result = handleGetCertValueOperation(op, certPath, keyName, expectedValue, systemPath, exportLocation, deleteAfterGetValue, remarksBuilder);
                    break;
                case "export_certificate":
                    result = exportCertificateFromStore(systemPath, exportLocation, op, remarksBuilder);
                    break;
                case "remove_user_trusted_root_cert":
                    result = removeUserTrustedRootCert(remarksBuilder, op);
                    break;
                case "import_certificate_to_trusted_root":
                    result = importCertificateToTrustedRoot(remarksBuilder, op);
                    break;
                default:
                    LOGGER.warning("Unsupported action: " + action);
                    remarksBuilder.append("error=Unsupported action " + action + "\n");
                    op.setRemarks(remarksBuilder.toString());
                    return false;
            }
            op.setRemarks(remarksBuilder.toString());
            LOGGER.info(op.getRemarks());
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error performing certificate operation", e);
            remarksBuilder.append("error=").append(e.getMessage()).append("\n");
            op.setRemarks(remarksBuilder.toString());
            return false;
        }
    }

    private static boolean importCertificateToTrustedRoot(StringBuilder remarksBuilder, Operation op) {
        try {
            String certPath = op.getParameter("cert_path");
            String storeName = op.getParameter("store_name"); // Optional: defaults to "Root"
            String storeLocation = op.getParameter("store_location"); // Optional: defaults to "LocalMachine"

            // Validate cert_path
            if (certPath == null || certPath.isEmpty()) {
                remarksBuilder.append("error=cert_path parameter is required for certificate import\n");
                return false;
            }

            // Validate file exists
            File certFile = new File(certPath);
            if (!certFile.exists() || !certFile.canRead()) {
                remarksBuilder.append("error=Certificate file does not exist or cannot be read: ").append(certPath).append("\n");
                return false;
            }

            // Set defaults and validate store location
            if (storeName == null || storeName.isEmpty()) {
                storeName = "Root";
            }
            if (storeLocation == null || storeLocation.isEmpty()) {
                storeLocation = "LocalMachine";
            }

            // Validate storeLocation is valid enum value
            if (!storeLocation.equalsIgnoreCase("CurrentUser") && !storeLocation.equalsIgnoreCase("LocalMachine")) {
                remarksBuilder.append("error=Invalid store_location. Must be 'CurrentUser' or 'LocalMachine', got: ").append(storeLocation).append("\n");
                return false;
            }

            // Escape path for PowerShell
            String escapedPath = certPath.replace("\\", "\\\\").replace("'", "''");

            // Build PowerShell command with proper error handling
            String psCommand = "try { " +
                    "$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2('" + escapedPath + "'); " +
                    "$store = New-Object System.Security.Cryptography.X509Certificates.X509Store('" + storeName + "', [System.Security.Cryptography.X509Certificates.StoreLocation]::" + storeLocation + "); " +
                    "$store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite); " +
                    "$store.Add($cert); " +
                    "$store.Close(); " +
                    "Write-Output 'Certificate imported successfully'; " +
                    "exit 0; " +
                    "} catch { " +
                    "Write-Output \"Error: $($_.Exception.Message)\"; " +
                    "exit 1; " +
                    "}";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-Command", psCommand);
            pb.redirectErrorStream(true);
            LOGGER.info("Executing command: powershell.exe -ExecutionPolicy Bypass -Command [certificate import script]");
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("PS Output: " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && output.toString().contains("imported successfully")) {
                remarksBuilder.append("import_status=success\n");
                remarksBuilder.append("store_location=").append(storeLocation).append("\n");
                remarksBuilder.append("store_name=").append(storeName).append("\n");
                remarksBuilder.append("imported_cert_path=").append(certPath).append("\n");

                if (op.hasNote()) {
                    saveNote(op, certPath);
                }
                return true;
            } else {
                remarksBuilder.append("import_status=failure\n");
                remarksBuilder.append("exit_code=").append(exitCode).append("\n");
                remarksBuilder.append("error=").append(output.toString().trim()).append("\n");
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error importing certificate to trusted root", e);
            remarksBuilder.append("import_status=exception\n");
            remarksBuilder.append("error=").append(e.getMessage()).append("\n");
            return false;
        }
    }



    private static boolean removeUserTrustedRootCert(StringBuilder remarksBuilder, Operation op) {
        try {
            String thumbprint = op.getParameter("thumbprint");
            if (thumbprint == null || thumbprint.isEmpty()) {
                remarksBuilder.append("error=Thumbprint is required for certificate removal\n");
                return false;
            }

            String psCommand = "\"Remove-Item \"Cert:\\LocalMachine\\Root\\" + thumbprint + "\" -Force\"";

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", psCommand);
            pb.redirectErrorStream(true);
            LOGGER.info("Executing command: " + pb.command());
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("PS Output: " + line);
                }
            }
            int exitCode = process.waitFor();
            remarksBuilder.append("remove_cert_output=").append(output.toString()).append("\n");
            return exitCode == 0;
        } catch (Exception e) {
            remarksBuilder.append("error=Failed to remove certificate: ").append(e.getMessage()).append("\n");
            return false;
        }
    }



    private static boolean exportCertificateFromStore(String certSubject, String exportLocation, Operation op, StringBuilder remarksBuilder) {
        try {
            if (certSubject == null || certSubject.isEmpty()) {
                remarksBuilder.append("error=system_path parameter is required for export_certificate\n");
                op.setRemarks(remarksBuilder.toString());
                return false;
            }
            if (exportLocation == null || exportLocation.isEmpty()) {
                exportLocation = getToolServerHome() + "\\exported_cert\\cert.cer";
            }

            File exportFile = new File(exportLocation);
            File parentDir = exportFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (exportFile.exists()) {
                String baseName = exportLocation.substring(0, exportLocation.lastIndexOf('.'));
                String ext = exportLocation.substring(exportLocation.lastIndexOf('.'));
                String timeStamp = String.valueOf(System.currentTimeMillis());
                exportLocation = baseName + "_" + timeStamp + ext;
                LOGGER.info("Export file already exists. New export location: " + exportLocation);
            }

            ProcessBuilder processBuilder = new ProcessBuilder("powershell.exe", "Export-Certificate", "-Cert",
                    "(Get-ChildItem -Path Cert:\\CurrentUser\\Root | Where-Object { $_.Subject -like '*" + certSubject + "*' } | Select-Object -First 1)",
                    "-FilePath", exportLocation);
            processBuilder.redirectErrorStream(true);
            LOGGER.info("Executing command: " + String.join(" ", processBuilder.command()));
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("PS Output: " + line);
                output.append(line).append("\n");
            }

            if (exitCode == 0) {
                remarksBuilder.append("export_status=success\n");
                remarksBuilder.append("$$$$$$$$$$ export_path=").append(exportLocation).append(" $$$$$$$$$$").append("\n");
                if (op.hasNote() && Objects.equals(op.getParameter("action"), "export_certificate")){
                    saveNote(op,exportLocation);
                }
                return true;
            } else {
                remarksBuilder.append("export_status=failure\n");
                remarksBuilder.append("error=").append(output.toString()).append("\n");
                return false;
            }
        } catch (Exception e) {
            remarksBuilder.append("export_status=exception\n");
            remarksBuilder.append("error=").append(e.getMessage()).append("\n");
            return false;
        }
    }

    /**
     * Handle get certificate value operation with validation and execution
     */
    private static boolean handleGetCertValueOperation(Operation op, String certPath, String keyName, String expectedValue,
                                                       String systemPath, String exportLocation, String deleteAfterGetValue,
                                                       StringBuilder remarksBuilder) {
        // Perform pre-validation checks
        String validatedCertPath = precheckGetCertValue(op, certPath, keyName, systemPath, exportLocation, remarksBuilder);
        if (validatedCertPath == null) {
            LOGGER.warning("Prechecks failed for certificate operation");
            return false;
        }
        // Execute the action with validated parameters
        return executeCertValueAction(op, validatedCertPath, keyName, expectedValue, exportLocation, deleteAfterGetValue, remarksBuilder);
    }

    /**
     * Validate parameters and prepare certificate path
     * @return The validated certificate path if successful, null if validation fails
     */
    private static String precheckGetCertValue(Operation op, String certPath, String keyName, String systemPath,
                                               String exportLocation, StringBuilder remarksBuilder) {
        LOGGER.info("Starting pre-validation for certificate operation");

        // Validate key_name parameter
        if (keyName == null || keyName.isEmpty()) {
            LOGGER.warning("key_name parameter is missing");
            remarksBuilder.append("error=key_name parameter is required\n");
            return null;
        }

        String resolvedCertPath = certPath;
        String resolvedExportLocation = exportLocation;

        // Handle system certificate export if needed
        if (systemPath != null && !systemPath.isEmpty()) {
            LOGGER.info("System path provided, preparing certificate export: " + systemPath);

            // Prepare export location
            if (resolvedExportLocation == null || resolvedExportLocation.isEmpty()) {
                resolvedExportLocation = getToolServerHome() + "\\exported_cert\\cert.cer";
            }

            // Create parent directory if it doesn't exist
            File exportFile = new File(resolvedExportLocation);
            File parentDir = exportFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean dirsCreated = parentDir.mkdirs();
                LOGGER.info("Created export directories: " + dirsCreated + " - " + parentDir.getAbsolutePath());
            }

            // Generate unique filename if file already exists
            if (exportFile.exists()) {
                String baseName = resolvedExportLocation.substring(0, resolvedExportLocation.lastIndexOf('.'));
                String ext = resolvedExportLocation.substring(resolvedExportLocation.lastIndexOf('.'));
                String timeStamp = String.valueOf(System.currentTimeMillis());
                resolvedExportLocation = baseName + "_" + timeStamp + ext;
                LOGGER.info("Export file already exists. New location: " + resolvedExportLocation);
            }

            // Export certificate from system store
            boolean exportSuccess = exportCertificateFromStore(systemPath, resolvedExportLocation, op, remarksBuilder);
            if (!exportSuccess) {
                LOGGER.warning("Failed to export certificate from system store");
                return null;
            }

            LOGGER.info("Certificate exported successfully from system store");
            resolvedCertPath = resolvedExportLocation;
        }

        // Validate certificate path
        if (resolvedCertPath == null || resolvedCertPath.isEmpty()) {
            LOGGER.warning("cert_path parameter is missing after resolution");
            remarksBuilder.append("error=cert_path parameter is missing after resolution\n");
            return null;
        }

        // Validate certificate file exists and is readable
        File certFile = new File(resolvedCertPath);
        if (!certFile.exists()) {
            LOGGER.warning("Certificate file does not exist: " + resolvedCertPath);
            remarksBuilder.append("error=Certificate file does not exist: ").append(resolvedCertPath).append("\n");
            return null;
        }

        if (!certFile.canRead()) {
            LOGGER.warning("Certificate file cannot be read: " + resolvedCertPath);
            remarksBuilder.append("error=Certificate file cannot be read: ").append(resolvedCertPath).append("\n");
            return null;
        }

        LOGGER.info("Pre-validation completed successfully");
        remarksBuilder.append("pre_validation=success\n");
        return resolvedCertPath;
    }

    /**
     * Execute certificate value extraction and validation with validated parameters
     */
    private static boolean executeCertValueAction(Operation op, String certPath, String keyName, String expectedValue,
                                                  String exportLocation, String deleteAfterGetValue, StringBuilder remarksBuilder) {
        try {
            LOGGER.info("Extracting certificate values from: " + certPath);

            // Read certificate content
            String certContent = new String(Files.readAllBytes(Paths.get(certPath)), StandardCharsets.UTF_8);

            // Extract certificate values
            Map<String, String> certValues = new HashMap<>();
            try {
                LOGGER.info("Attempting PowerShell certificate extraction for key: " + keyName);
                File certFile = new File(certPath);
                certValues = extractValuesFromCertificateViaPowerShell(certFile, keyName);
                remarksBuilder.append("extraction_method=powershell\n");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to parse certificate using PowerShell, falling back to text scanning", e);
                remarksBuilder.append("powershell_extraction_failed=").append(e.getMessage()).append("\n");
                certValues = extractValuesFromTextFile(certContent, keyName);
                remarksBuilder.append("extraction_method=text_parsing\n");
            }

            if (certValues.isEmpty()) {
                LOGGER.warning("No values found for key: " + keyName);
                remarksBuilder.append("status=No values found\n");
                remarksBuilder.append("key_name=").append(keyName).append("\n");
                return false;
            }

            // Process found values
            remarksBuilder.append("status=Values found\n");
            String foundValue = "";

            // Store each value in output variables and add to remarks
            for (Map.Entry<String, String> entry : certValues.entrySet()) {
                String key = entry.getKey();
                foundValue = entry.getValue();
                op.setOutputValue(key + "=" + foundValue);
                remarksBuilder.append(key).append("=").append(foundValue).append("\n");
                LOGGER.info("Extracted certificate value: " + key + " = " + foundValue);
            }

            // Validate against expected value if provided
            boolean result = true;
            if (expectedValue != null && !expectedValue.isEmpty()) {
                boolean matchFound = false;

                if (keyName != null && !keyName.equalsIgnoreCase("all")) {
                    String actualValue = certValues.get(keyName);
                    matchFound = actualValue != null && actualValue.equals(expectedValue);
                } else {
                    matchFound = certValues.values().stream().anyMatch(value -> value.equals(expectedValue));
                }

                remarksBuilder.append("match_result=").append(matchFound).append("\n");
                remarksBuilder.append("expected_value=").append(expectedValue).append("\n");
                remarksBuilder.append("key_name=").append(keyName).append("\n");

                op.setOutputValue("match_result=" + matchFound);

                if (matchFound && op.hasNote()) {
                    saveNote(op, expectedValue);
                }

                result = matchFound;
            }

            // Handle post-processing cleanup
            if (deleteAfterGetValue != null && deleteAfterGetValue.equalsIgnoreCase("true") &&
                    exportLocation != null && !exportLocation.isEmpty()) {

                File toDelete = new File(exportLocation);
                if (toDelete.exists()) {
                    boolean deleted = toDelete.delete();
                    remarksBuilder.append("exported_cert_deleted=").append(deleted).append("\n");

                    if (!deleted) {
                        LOGGER.warning("Failed to delete exported certificate file: " + exportLocation);
                        remarksBuilder.append("error=Failed to delete exported certificate file\n");
                    }
                }
            }

            // Save note if needed
            if (op.hasNote() && !foundValue.isEmpty()) {
                saveNote(op, foundValue);
            }

            LOGGER.info("Certificate value action completed successfully, result: " + result);
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing certificate value action", e);
            remarksBuilder.append("error=Certificate value extraction failed: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    private static Map<String, String> extractValuesFromCertificateViaPowerShell(File certFile, String keyName) {
        Map<String, String> values = new HashMap<>();
        try {
            String psCommand;
            String certPath = certFile.getAbsolutePath();
            if (keyName == null || keyName.equalsIgnoreCase("all")) {
                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                        "$san = ($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Subject Alternative Name' }); " +
                        "$sanValue = if ($san) { [System.Text.Encoding]::UTF8.GetString($san.RawData) } else { '' }; " +
                        "$props = @{ " +
                        "'Version' = $cert.Version; " +
                        "'SerialNumber' = $cert.SerialNumber; " +
                        "'SignatureAlgorithm' = $cert.SignatureAlgorithm.FriendlyName; " +
                        "'Issuer' = $cert.Issuer; " +
                        "'NotBefore' = $cert.NotBefore; " +
                        "'NotAfter' = $cert.NotAfter; " +
                        "'Subject' = $cert.Subject; " +
                        "'Thumbprint' = $cert.Thumbprint; " +
                        "'ValidFrom' = $cert.NotBefore; " +
                        "'ValidTo' = $cert.NotAfter; " +
                        "'SubjectAlternativeName' = $sanValue; " +
                        "}; $props | ConvertTo-Json";
            }  else if (keyName.equalsIgnoreCase("subjectalternativename")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "$san = ($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Subject Alternative Name' }); " +
                    "if ($san) { " +
                    "$formattedSan = $san.Format($true); " +
                    "if($formattedSan -match 'DNS Name=|IP Address=') { " +
                    "$formattedSan -replace '^[^=]+=', '' -replace '\\s+', ' ' " +
                    "} else { $formattedSan } " +
                    "} else { '' }";
            } else if (keyName.equalsIgnoreCase("validfrom")) {
                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                        "$cert.NotBefore.ToString('yyyy-MM-dd HH:mm:ss')";
            } else if (keyName.equalsIgnoreCase("publickey")) {
                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                        "$pubKey = $cert.PublicKey.Key; " +
                        "if ($pubKey -is [System.Security.Cryptography.RSA]) { " +
                        "  'RSA Key, Size: ' + $pubKey.KeySize + ' bits' " +
                        "} elseif ($pubKey -is [System.Security.Cryptography.DSA]) { " +
                        "  'DSA Key, Size: ' + $pubKey.KeySize + ' bits' " +
                        "} elseif ($pubKey -is [System.Security.Cryptography.ECDsa]) { " +
                        "  'ECDSA Key, Size: ' + $pubKey.KeySize + ' bits' " +
                        "} else { " +
                        "  $pubKey.ToString() " +
                        "}";
            }else if (keyName.equalsIgnoreCase("validto")) {
                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                        "$cert.NotAfter.ToString('yyyy-MM-dd HH:mm:ss')";
            }else if (keyName.equalsIgnoreCase("basicconstraints")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Basic Constraints' }).Format($true)";
        } else if (keyName.equalsIgnoreCase("subjectkeyidentifier")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Subject Key Identifier' }).Format($true)";
        } else if (keyName.equalsIgnoreCase("authoritykeyidentifier")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Authority Key Identifier' }).Format($true)";
        } else if (keyName.equalsIgnoreCase("enhancedkeyusage")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Enhanced Key Usage' }).Format($true)";
        } else if (keyName.equalsIgnoreCase("keyusage")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "($cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Key Usage' }).Format($true)";
        } else if (keyName.equalsIgnoreCase("thumbprint")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.Thumbprint";
        } else if (keyName.equalsIgnoreCase("version")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.Version";
        } else if (keyName.equalsIgnoreCase("serialnumber")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.SerialNumber";
        } else if (keyName.equalsIgnoreCase("signaturealgorithm")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.SignatureAlgorithm.FriendlyName";
        } else if (keyName.equalsIgnoreCase("signaturehashalgorithm")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); " +
                    "$alg = $cert.SignatureAlgorithm.FriendlyName; " +
                    "if ($alg -match '(?:MD5|SHA\\d+)') { $matches[0] } else { $alg }";
        } else if (keyName.equalsIgnoreCase("issuer")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.Issuer";
        } else if (keyName.equalsIgnoreCase("subject")) {
            psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert.Subject";
            } else if (keyName.contains("_")) {
                String[] parts = keyName.split("_", 2);
                String parentKey = parts[0].toLowerCase();
                String innerKey = parts[1].toLowerCase();

                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); ";

                switch(parentKey) {
                    case "basicconstraints":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Basic Constraints' }; ";
                        switch(innerKey) {
                            case "subjecttype":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Match($formatted, 'Subject Type=([^\\r\\n]+)'); " +
                                        "if ($matches.Success) { $matches.Groups[1].Value.Trim() } else { '' } } else { '' }";
                                break;
                            case "pathlengthconstraint":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Match($formatted, 'Path Length Constraint=([^\\r\\n]+)'); " +
                                        "if ($matches.Success) { $matches.Groups[1].Value.Trim() } else { '' } } else { '' }";
                                break;
                            default:
                                psCommand += "''";
                                break;
                        }
                        break;
                    case "subjectalternativename":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Subject Alternative Name' }; ";
                        switch(innerKey) {
                            case "dnsname":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Matches($formatted, 'DNS Name=([^\\r\\n,]+)'); " +
                                        "if ($matches.Count -gt 0) { $matches | ForEach-Object { $_.Groups[1].Value.Trim() } | Out-String } else { '' } } else { '' }";
                                break;
                            case "ipaddress":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Matches($formatted, 'IP Address=([^\\r\\n,]+)'); " +
                                        "if ($matches.Count -gt 0) { $matches | ForEach-Object { $_.Groups[1].Value.Trim() } | Out-String } else { '' } } else { '' }";
                                break;
                            default:
                                psCommand += "''";
                                break;
                        }
                        break;
                    case "authoritykeyidentifier":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Authority Key Identifier' }; ";
                        switch(innerKey) {
                            case "keyid":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Match($formatted, 'KeyID=([^\\r\\n]+)'); " +
                                        "if ($matches.Success) { $matches.Groups[1].Value.Trim() } else { '' } } else { '' }";
                                break;
                            default:
                                psCommand += "''";
                                break;
                        }
                        break;
                    case "enhancedkeyusage":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Enhanced Key Usage' }; ";
                        switch(innerKey) {
                            case "oid":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Matches($formatted, '\\(([0-9\\.]+)\\)'); " +
                                        "if ($matches.Count -gt 0) { $matches | ForEach-Object { $_.Groups[1].Value.Trim() } | Out-String } else { '' } } else { '' }";
                                break;
                            case "purpose":
                                psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                        "$matches = [regex]::Matches($formatted, '([^\\(\\)\\r\\n]+)\\s*\\([0-9\\.]+\\)'); " +
                                        "if ($matches.Count -gt 0) { $matches | ForEach-Object { $_.Groups[1].Value.Trim() } | Out-String } else { '' } } else { '' }";
                                break;
                            default:
                                psCommand += "if ($ext) { $ext.Format($true) } else { '' }";
                                break;
                        }
                        break;
                    case "keyusage":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Key Usage' }; " +
                                "if ($ext) { $formatted = $ext.Format($true); " +
                                "$matches = [regex]::Match($formatted, '(?i)" + innerKey + "\\s*\\(([^\\)]+)\\)'); " +
                                "if ($matches.Success) { $matches.Groups[1].Value.Trim() } else { " +
                                "if ($formatted -like '*" + innerKey + "*') { 'true' } else { 'false' } } } else { '' }";
                        break;
                    case "subjectkeyidentifier":
                        psCommand += "$ext = $cert.Extensions | Where-Object { $_.Oid.FriendlyName -eq 'Subject Key Identifier' }; ";
                        psCommand += "if ($ext) { $formatted = $ext.Format($true); " +
                                "$matches = [regex]::Match($formatted, 'KeyID=([^\\r\\n]+)'); " +
                                "if ($matches.Success) { $matches.Groups[1].Value.Trim() } else { '' } } else { '' }";
                        break;
                    default:
                        // Handle DN components like Subject_CN, Issuer_O
                        if (parentKey.equals("subject") || parentKey.equals("issuer")) {
                            psCommand += "$dn = $cert." + parentKey.substring(0, 1).toUpperCase() + parentKey.substring(1) + "; " +
                                    "if ($dn -match '" + innerKey.toUpperCase() + "=([^,]+)') { $matches[1] } else { '' }";
                        } else {
                            psCommand += "''";
                        }
                        break;
                }
            } else {
                String prop = keyName.substring(0, 1).toUpperCase() + keyName.substring(1);
                psCommand = "$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new('" + certPath + "'); $cert." + prop;
            }
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", psCommand);
            LOGGER.info("Executing command: " + String.join(" ", pb.command()));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder outputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }
            process.waitFor();
            String output = outputBuilder.toString().trim();

            if (keyName == null || keyName.equalsIgnoreCase("all")) {
                Pattern p = Pattern.compile("\"(\\w+)\":\\s*\"?([^\"]+)\"?");
                Matcher m = p.matcher(output);
                while (m.find()) {
                    values.put(m.group(1), m.group(2).trim());
                }
            } else {
                if (!output.isEmpty()) {
                    values.put(keyName, output.trim());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting certificate values via PowerShell", e);
        }
        return values;
    }

    /**
     * Extract values by scanning a text file for key-value pairs
     */
    private static Map<String, String> extractValuesFromTextFile(String content, String keyName) {
        Map<String, String> values = new HashMap<>();

        // Common patterns for key-value pairs in certificate-related files
        extractCommonFields(content, values);

        // Try to find lines containing the specific key name
        if (!keyName.equalsIgnoreCase("all")) {
            Pattern specificPattern = Pattern.compile("(?i)\\b" + Pattern.quote(keyName) + "\\s*[:=]\\s*([^\\n\\r]+)");
            Matcher specificMatcher = specificPattern.matcher(content);

            if (specificMatcher.find()) {
                values.put(keyName, specificMatcher.group(1).trim());
            }
        }

        return values;
    }

    /**
     * Extract common fields found in certificate files
     */
    private static void extractCommonFields(String content, Map<String, String> values) {
        // Look for common certificate fields
        extractField(content, "(?i)subject\\s*[:=]\\s*([^\\n\\r]+)", "subject", values);
        extractField(content, "(?i)issuer\\s*[:=]\\s*([^\\n\\r]+)", "issuer", values);
        extractField(content, "(?i)serial\\s*(?:number)?\\s*[:=]\\s*([^\\n\\r]+)", "serialNumber", values);
        extractField(content, "(?i)not\\s*before\\s*[:=]\\s*([^\\n\\r]+)", "notBefore", values);
        extractField(content, "(?i)not\\s*after\\s*[:=]\\s*([^\\n\\r]+)", "notAfter", values);
        extractField(content, "(?i)valid\\s*from\\s*[:=]\\s*([^\\n\\r]+)", "validFrom", values);
        extractField(content, "(?i)valid\\s*(?:until|to)\\s*[:=]\\s*([^\\n\\r]+)", "validTo", values);
        extractField(content, "(?i)signature\\s*algorithm\\s*[:=]\\s*([^\\n\\r]+)", "signatureAlgorithm", values);
        extractField(content, "(?i)public\\s*key\\s*(?:algorithm)?\\s*[:=]\\s*([^\\n\\r]+)", "publicKeyAlgorithm", values);
        extractField(content, "(?i)version\\s*[:=]\\s*([^\\n\\r]+)", "version", values);
    }

    /**
     * Extract a field using regex pattern
     */
    private static void extractField(String content, String pattern, String key, Map<String, String> values) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(content);

        if (m.find()) {
            values.put(key, m.group(1).trim());
        }
    }
}
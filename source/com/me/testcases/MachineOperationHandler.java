package com.me.testcases;

import com.me.Operation;
import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

public class MachineOperationHandler {
    private static final Logger LOGGER = LogManager.getLogger(MachineOperationHandler.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation operation) {
        String action = operation.getParameter("action");
        if (action == null || action.isEmpty()) {
            LOGGER.warning("No action specified for machine_operation");
            operation.setRemarks("No action specified for machine_operation");
            return false;
        }
        LOGGER.info("Executing machine operation: " + action);
        try {
            switch (action.toLowerCase()) {
//                case "start":
//                    return startMachine(operation);//
//                case "stop":
//                    return stopMachine(operation);//
                case "restart":
                    return restartMachine(operation);
                case "rename":
                    return renameMachine(operation);
                case "connect_network":
                    return connectNetwork(operation);
                case "disconnect_network":
                    return disconnectNetwork(operation);//TODO Disconnect the public network
                case "change_domain":
                    return changeDomain(operation);//TODO need to handle domain change to domain to workgroup it was not return properly
//                case "get_spec":
//                    return getMachineSpecification(operation);
                case "change_datetime":
                    return changeDateTime(operation);
                case "change_browser":
                    return changeDefaultBrowser(operation);
                case "get_machine_spec":
                    return getMachineSpec(operation);
                case "change_timezone":
                    return changeTimeZone(operation);
                default:
                    LOGGER.warning("Unknown machine_operation action: " + action);
                    operation.setRemarks("Unknown machine_operation action: " + action);
                    return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing machine_operation: " + e);
            operation.setRemarks("Error: " + e.getMessage());
            return false;
        }
    }

    private static boolean startMachine(Operation operation) {
        LOGGER.info("Start machine operation requested.");
        LOGGER.warning("Start machine operation is not supported via software (requires physical power on).");
        operation.setRemarks("Start machine operation is not supported via software (requires physical power on).");
        return false;
    }

    private static boolean changeTimeZone(Operation operation) {
        String timeZoneId = operation.getParameter("timezone_id");
        LOGGER.info("Changing timezone to: " + timeZoneId);

        if (timeZoneId == null || timeZoneId.isEmpty()) {
            LOGGER.warning("No timezone ID specified for change_timezone operation.");
            operation.setRemarks("No timezone ID specified.");
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Set-TimeZone -Id '" + timeZoneId + "'"
            );

            LOGGER.info("Executing Set-TimeZone command: " + pb.command());

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            LOGGER.info("Set-TimeZone command exit code: " + exitCode + ", output: " + output);

            boolean success = exitCode == 0;
            operation.setRemarks(success ?
                    "Timezone changed to " + timeZoneId :
                    "Failed to change timezone. Output: " + output);

            return success;
        } catch (Exception e) {
            LOGGER.severe("Error changing timezone: " + e.getMessage());
            operation.setRemarks("Error changing timezone: " + e.getMessage());
            return false;
        }
    }


    private static boolean getMachineSpec(Operation operation) {
        String infoType = operation.getParameter("info_type"); // e.g. "computer name", "domain name"
        if (infoType == null || infoType.isEmpty() && operation.hasNote()) {
            operation.setRemarks("No info_type or note specified.");
            return false;
        }

        String command;
        switch (infoType.trim().toLowerCase()) {
            case "computer_name":
                command = "powershell -NoProfile -Command \"([System.Net.Dns]::GetHostName())\"";
                break;
            case "domain_name":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_ComputerSystem).Domain\"";
                break;
            case "fqdn":
                command = "powershell -NoProfile -Command \"([System.Net.Dns]::GetHostByName(($env:computerName))).HostName\"";
                break;
            case "os_name":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_OperatingSystem).Caption\"";
                break;
            case "os_version":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_OperatingSystem).Version\"";
                break;
            case "architecture":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_OperatingSystem).OSArchitecture\"";
                break;
            case "time_zone":
                command = "powershell -NoProfile -Command \"(Get-TimeZone).Id\"";
                break;
            case "language":
                command = "powershell -NoProfile -Command \"(Get-Culture).DisplayName\"";
                break;
            case "logged_user":
                command = "powershell -NoProfile -Command \"$env:USERNAME\"";
                break;
            case "mac_address":
                command = "powershell -NoProfile -Command \"(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1).MacAddress\"";
                break;
            case "service_tag":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_BIOS).SerialNumber\"";
                break;
//Commands for Agent Troubleshooting tool (System Information)
            case "admin$_share":
                command = "powershell -NoProfile -Command \"if (Get-SmbShare -Name 'ADMIN$' -ErrorAction SilentlyContinue) { 'Enabled' } else { 'Disabled' }\"";
                break;
            case "bios_mode":
                command = "powershell -NoProfile -Command \"(Get-ComputerInfo).BiosFirmwareType\"";
                break;
            case "bios_version":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_BIOS).SMBIOSBIOSVersion\"";
                break;
            case "current_time":
                command = "powershell -NoProfile -Command \"Get-Date -Format 'M/d/yyyy h:mm:ss tt'\"";
                break;
            case "day_light_saving_time":
                command = "powershell -NoProfile -Command \"(Get-TimeZone).DaylightName\"";
                break;
            case "is_day_light_saving":
                command = "powershell -NoProfile -Command \"(Get-TimeZone).IsDaylightSavingTime((Get-Date))\"";
                break;
            case "last_ip_address_list":
            case "local_ip_address":
                command = "powershell -NoProfile -Command \"$orderedAdapters = @('vEthernet (Default Switch)', 'Ethernet 2'); "
                        + "$ips = foreach ($adapter in $orderedAdapters) { "
                        + "(Get-NetIPAddress | Where-Object { $_.AddressFamily -eq 'IPv4' -and $_.InterfaceAlias -eq $adapter }).IPAddress }; "
                        + "$ips -join ','\"";
                break;
            case "local_domain_type":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_ComputerSystem).DomainRole\"";
                break;
            case "local_language":
                command = "powershell -NoProfile -Command \"(Get-Culture).TwoLetterISOLanguageName\"";
                break;
            case "local_machine_uuid":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_ComputerSystemProduct).UUID\"";
                break;
            case "local_operating_system":
                command = "powershell -NoProfile -Command \"$os = (Get-CimInstance Win32_OperatingSystem); "
                        + "$arch = $os.OSArchitecture -replace '64-bit','x64' -replace '32-bit','x86'; "
                        + "Write-Output \\\"$($os.Caption) Edition ($arch)\\\"\"";
                break;
            case "local_service_pack":
                command = "powershell -NoProfile -Command \"$arch = (Get-ComputerInfo).OSArchitecture -replace '64-bit','x64' -replace '32-bit','x86'; "
                        + "Write-Output \\\"Windows 11 Version $((Get-ComputerInfo).OSDisplayVersion) ($arch)\\\"\"";
                break;
            case "local_time_zone_offset":
                command = "powershell -NoProfile -Command \"(Get-TimeZone).BaseUtcOffset.TotalMinutes\"";
                break;
            case "machine_type":
                command = "powershell -NoProfile -Command \"if ((Get-CimInstance Win32_SystemEnclosure).ChassisTypes -match '8|9|10|14') { 'Laptop' } else { 'Desktop' }\"";
                break;
            case "standard_time":
                command = "powershell -NoProfile -Command \"(Get-TimeZone).StandardName\"";
                break;
            case "system_spec_name":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_ComputerSystem).Name\"";
                break;
            case "tpm_status":
                command = "powershell -NoProfile -Command \"if ((Get-Tpm).TpmReady) { 'Ready for use' } else { 'Not ready' }\"";
                break;
            case "tpm_version":
                command = "powershell -NoProfile -Command \"(Get-CimInstance -Namespace root\\cimv2\\Security\\MicrosoftTpm -Class Win32_Tpm).SpecVersion.Split(',')[0]\"";
                break;
            case "utc_offset":
                command = "powershell -NoProfile -Command \"([System.TimeZoneInfo]::Local.BaseUtcOffset).ToString()\"";
                break;
            case "utc_time":
                command = "powershell -NoProfile -Command \"(Get-Date).ToUniversalTime().ToString('M/d/yyyy h:mm:ss tt')\"";
                break;
            //Agent Troubleshooting tool (Agent Identity view)
            case "os_full_name":
                command = "powershell -NoProfile -Command \"(Get-CimInstance -ClassName Win32_OperatingSystem).Name\"";
                break;
            case "os_last_bootup_time":
                command = "powershell -NoProfile -Command \"(Get-CimInstance -ClassName Win32_OperatingSystem).LastBootUpTime.ToString('M/d/yyyy h:mm:ss tt')\"";
                break;
            case "os_install_date":
                command = "powershell -NoProfile -Command \"(Get-CimInstance -ClassName Win32_OperatingSystem).InstallDate.ToString('M/d/yyyy h:mm:ss tt')\"";
                break;
            case "identifying_number":
                command = "powershell -NoProfile -Command \"(Get-CimInstance Win32_ComputerSystemProduct).IdentifyingNumber\"";
                break;
            //Agent Troubleshooting tool (WMI view)
            case "wmi_service":
                command = "powershell -NoProfile -Command \"(Get-Service -Name 'Winmgmt').Status\"";
                break;
            //Agent Troubleshooting tool (AntiVirus view)
            case "av_guid_name":
                command = "powershell -NoProfile -Command \"(Get-CimInstance -Namespace root\\SecurityCenter2 -ClassName AntiVirusProduct).displayName\"";
                break;
            case "active_directory_check":
                command = "powershell -NoProfile -Command \"(Get-WmiObject Win32_ComputerSystem).PartOfDomain\"";
                break;
            default:
                operation.setRemarks("Unknown info_type: " + infoType);
                return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            process.waitFor();
            String result = output.toString().trim();
            operation.setRemarks("output" + ": " + result);
            saveNote(operation,result);
            return !result.isEmpty();
        } catch (Exception e) {
            operation.setRemarks("Error retrieving " + infoType + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean stopMachine(Operation operation) {
        LOGGER.info("Attempting to stop machine...");
        try {
            ProcessBuilder pb = new ProcessBuilder("shutdown", "/s", "/t", "0");
            Process process = pb.start();
            int exitCode = process.waitFor();
            LOGGER.info("Shutdown command executed with exit code: " + exitCode);
            operation.setRemarks(exitCode == 0 ? "Shutdown command issued." : "Failed to issue shutdown command.");
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.severe("Error stopping machine: " + e.getMessage());
            operation.setRemarks("Error stopping machine: " + e.getMessage());
            return false;
        }
    }

    private static boolean restartMachine(Operation operation) {
        LOGGER.info("Attempting to restart machine...");
        try {
            ProcessBuilder pb = new ProcessBuilder("shutdown", "/r", "/t", "0");
            Process process = pb.start();
            int exitCode = process.waitFor();
            LOGGER.info("Restart command executed with exit code: " + exitCode);
            operation.setRemarks(exitCode == 0 ? "Restart command issued." : "Failed to issue restart command.");
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.severe("Error restarting machine: " + e.getMessage());
            operation.setRemarks("Error restarting machine: " + e.getMessage());
            return false;
        }
    }

    private static boolean renameMachine(Operation operation) {
        String newName = operation.getParameter("new_name");
        LOGGER.info("Renaming machine to: " + newName);
        if (newName == null || newName.isEmpty()) {
            LOGGER.warning("No new name specified for rename operation.");
            operation.setRemarks("No new name specified for rename operation.");
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Rename-Computer -NewName '" + newName + "' -Force"
            );

            LOGGER.info("Executing rename command: " + pb.command());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            LOGGER.info("Rename command exit code: " + exitCode + ", output: " + output);

            boolean success = exitCode == 0;
            operation.setRemarks(success ?
                    "Machine renamed to " + newName + ". Restart required to apply changes." :
                    "Failed to rename machine. Output: " + output);
            return success;
        } catch (Exception e) {
            LOGGER.severe("Error renaming machine: " + e.getMessage());
            operation.setRemarks("Error renaming machine: " + e.getMessage());
            return false;
        }
    }

    private static boolean disconnectNetwork(Operation operation) {
        LOGGER.info("Disabling network connectivity by blocking firewall outbound connections");
        try {
            // First disable all existing outbound allow rules
            LOGGER.info("Disabling all existing outbound firewall rules");
            ProcessBuilder disableRulesPb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Get-NetFirewallRule -Direction Outbound -Action Allow | Set-NetFirewallRule -Enabled False -ErrorAction SilentlyContinue"
            );

            disableRulesPb.redirectErrorStream(true);
            Process disableRulesProcess = disableRulesPb.start();

            BufferedReader disableReader = new BufferedReader(new InputStreamReader(disableRulesProcess.getInputStream()));
            String disableLine;
            while ((disableLine = disableReader.readLine()) != null) {
                LOGGER.info("Disable rules output: " + disableLine);
            }

            int disableExitCode = disableRulesProcess.waitFor();
            LOGGER.info("Disable outbound rules command exit code: " + disableExitCode);

            // Block only outbound with Windows Firewall
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Set-NetFirewallProfile -DefaultOutboundAction Block -Profile Domain,Private,Public"
            );

            LOGGER.info("Executing disable firewall outbound command: " + pb.command());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("Powershell output: " + line);
            }

            int exitCode = process.waitFor();
            LOGGER.info("Disable firewall outbound command exit code: " + exitCode);

            // Create explicit block rule with highest priority (OUTBOUND ONLY)
            ProcessBuilder blockRulePb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Remove-NetFirewallRule -DisplayName 'Block All Outbound' -ErrorAction SilentlyContinue; New-NetFirewallRule -DisplayName 'Block All Outbound' -Direction Outbound -Action Block -Enabled True -Profile Any -Priority 1"
            );
            LOGGER.info("Creating explicit block rule command: " + blockRulePb.command());
            blockRulePb.redirectErrorStream(true);
            Process blockRuleProcess = blockRulePb.start();
            blockRuleProcess.waitFor();

            // Use netsh but ONLY block outbound (allow inbound)
            ProcessBuilder netshPb = new ProcessBuilder(
                    "netsh", "advfirewall", "set", "allprofiles", "state", "on"
            );
            LOGGER.info("Enabling firewall state with netsh command: " + netshPb.command());
            netshPb.redirectErrorStream(true);
            netshPb.start().waitFor();

            // IMPORTANT: Set policy to ALLOW inbound but BLOCK outbound
            ProcessBuilder netshBlockPb = new ProcessBuilder(
                    "netsh", "advfirewall", "set", "allprofiles", "firewallpolicy", "allowinbound,blockoutbound"
            );
            LOGGER.info("Blocking outbound traffic with netsh command: " + netshBlockPb.command());
            netshBlockPb.redirectErrorStream(true);
            netshBlockPb.start().waitFor();

            // Verify network is disconnected and report status
            boolean isBlocked = verifyNetworkIsDisconnected();
            if (isBlocked) {
                LOGGER.info("Network connectivity verification: Outbound is blocked successfully");
                operation.setRemarks("Network outbound connections blocked successfully");
                return true;
            } else {
                LOGGER.severe("CRITICAL: Failed to block outbound network connectivity");
                operation.setRemarks("Failed to block outbound network connectivity");
                return false;
            }

        } catch (Exception e) {
            LOGGER.severe("Error disabling network connectivity: " + e.getMessage());
            operation.setRemarks("Error disabling network connectivity: " + e.getMessage());
            return false;
        }
    }




    private static boolean connectNetwork(Operation operation) {
        LOGGER.info("Enabling network connectivity by allowing firewall outbound connections");
        try {
            // First allow outbound connections for all profiles
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Set-NetFirewallProfile -DefaultOutboundAction Allow -Profile Domain,Private,Public"
            );

            LOGGER.info("Executing enable firewall outbound command: " + pb.command());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                LOGGER.info("Powershell output: " + line);
            }

            int exitCode = process.waitFor();
            LOGGER.info("Enable firewall outbound command exit code: " + exitCode);

            // Remove any explicit blocking rules we may have created
            ProcessBuilder removeBlockRulePb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Remove-NetFirewallRule -DisplayName 'Block All Outbound' -ErrorAction SilentlyContinue"
            );
            removeBlockRulePb.start().waitFor();

            // Re-enable default outbound allow rules
            ProcessBuilder enableRulesPb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Get-NetFirewallRule -Direction Outbound -Action Allow | Set-NetFirewallRule -Enabled True -ErrorAction SilentlyContinue"
            );
            enableRulesPb.start().waitFor();

            // Verify that outbound connections are actually allowed
            boolean allowSuccess = exitCode == 0;
            if (allowSuccess) {
                // Test if we can reach a domain
                boolean isConnected = verifyNetworkIsConnected();
                if (!isConnected) {
                    LOGGER.warning("Firewall rule set but network connectivity not detected");
                    operation.setRemarks("Warning: Firewall outbound connections set to allowed, but network still appears to be blocked");
                    return false;
                }
                operation.setRemarks("Firewall outbound connections enabled. Network access restored successfully.");
            } else {
                operation.setRemarks("Failed to enable firewall outbound connections. Output: " + output);
            }

            return allowSuccess;
        } catch (Exception e) {
            LOGGER.severe("Error enabling firewall outbound connections: " + e.getMessage());
            operation.setRemarks("Error enabling firewall outbound connections: " + e.getMessage());
            return false;
        }
    }



    // Add these helper methods to verify connectivity
    private static boolean verifyNetworkIsDisconnected() {
        try {
            // Use PowerShell to test connection to google.com with a short timeout
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Test-NetConnection -ComputerName google.com -InformationLevel Quiet -WarningAction SilentlyContinue -ErrorAction SilentlyContinue -TimeoutSeconds 5"
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            // If Test-NetConnection returns non-zero, connection failed (which means our blocking worked)
            return exitCode != 0;
        } catch (Exception e) {
            // If an exception occurs, we'll assume the network is disconnected
            LOGGER.info("Exception while testing network disconnection, assuming disconnected: " + e.getMessage());
            return true;
        }
    }

    private static boolean verifyNetworkIsConnected() {
        try {
            // Use PowerShell to test connection to google.com with a short timeout
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", "Test-NetConnection -ComputerName google.com -InformationLevel Quiet -WarningAction SilentlyContinue -TimeoutSeconds 5"
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            // If Test-NetConnection returns zero, connection succeeded
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.info("Exception while testing network connection, assuming disconnected: " + e.getMessage());
            return false;
        }
    }


    private static boolean changeDomain(Operation operation) {
        String domain = operation.getParameter("domain");
        String user = operation.getParameter("user");
        String password = operation.getParameter("password");
        boolean doRestart = Boolean.parseBoolean(operation.getParameter("do_restart"));
        String workgroup = operation.getParameter("workgroup");
        LOGGER.info("Changing domain to: " + domain + " with user: " + user);

        try {
            ProcessBuilder pb;
            if (domain != null && !domain.isEmpty()) {
                // Join domain
                String psCommand = String.format(
                        "\"Add-Computer -DomainName '%s' -Credential (New-Object System.Management.Automation.PSCredential('%s',(ConvertTo-SecureString '%s' -AsPlainText -Force))) -Force%s",
                        domain, user, password, doRestart ? " -Restart\"" : "\""
                );
                pb = new ProcessBuilder("powershell.exe", "-Command", psCommand);
            } else {
                // Join workgroup
                if (workgroup == null || workgroup.isEmpty()) {
                    workgroup = "WORKGROUP";
                }
                String psCommand = String.format(
                        "\"$cred = New-Object System.Management.Automation.PSCredential('%s',(ConvertTo-SecureString '%s' -AsPlainText -Force)); " +
                                "Remove-Computer -UnjoinDomainCredential $cred -PassThru -Force%s; " +
                                "Add-Computer -WorkGroupName '%s'%s",
                        user, password, doRestart ? " -Restart" : "", workgroup, doRestart ? " -Restart\"" : "\""
                );
                pb = new ProcessBuilder("powershell.exe", "-Command", psCommand);
            }
            pb.redirectErrorStream(true);
            String safeCommand = pb.command().toString().replace(password, "********");
            LOGGER.info("Change domain command: " + safeCommand);
            LOGGER.info("Executing change domain command: " + pb.command());
            Process process = pb.start();
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = outputReader.readLine()) != null) {
                LOGGER.info("Change domain output: " + line);
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            LOGGER.info("Change domain command exit code: " + exitCode);
            operation.setRemarks(exitCode == 0 ? "Domain changed to " + (domain != null ? domain : workgroup) : "Failed to change domain.\n" + output);
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.severe("Error changing domain: " + e.getMessage());
            operation.setRemarks("Error changing domain: " + e.getMessage());
            return false;
        }
    }

    private static boolean changeDateTime(Operation operation) {
        String date = operation.getParameter("date"); // Format: dd-MM-yyyy
        String time = operation.getParameter("time"); // Format: HH:mm:ss
        LOGGER.info("Changing date and time to: " + date + " " + time);

        if (date == null || time == null) {
            LOGGER.warning("Missing date/time for change_datetime operation.");
            operation.setRemarks("Missing date/time for change_datetime operation.");
            return false;
        }

        try {
            // Parse the date components
            String[] dateParts = date.split("-");
            if (dateParts.length != 3) {
                LOGGER.warning("Invalid date format. Expected dd-MM-yyyy, got: " + date);
                operation.setRemarks("Invalid date format. Expected dd-MM-yyyy");
                return false;
            }

            String day = dateParts[0];
            String month = dateParts[1];
            String year = dateParts[2];

            // Format for PowerShell: MM/dd/yyyy HH:mm:ss
            String dateTimeString = month + "/" + day + "/" + year + " " + time;
            String psCommand = "Set-Date '" + dateTimeString + "'";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", psCommand
            );

            LOGGER.info("Executing Set-Date command: " + pb.command());

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            LOGGER.info("Set-Date command exit code: " + exitCode + ", output: " + output);

            boolean success = exitCode == 0;
            operation.setRemarks(success ?
                    "Date and time changed to " + dateTimeString :
                    "Failed to change date/time. Output: " + output);

            return success;
        } catch (Exception e) {
            LOGGER.severe("Error changing date/time: " + e.getMessage());
            operation.setRemarks("Error changing date/time: " + e.getMessage());
            return false;
        }
    }

    private static boolean changeDefaultBrowser(Operation operation) {
        String browserName = operation.getParameter("browser_path");
        LOGGER.info("Changing default browser to: " + browserName);
        if (browserName == null || browserName.isEmpty()) {
            LOGGER.warning("No browser name specified for change_browser operation.");
            operation.setRemarks("No browser name specified.");
            return false;
        }
        try {
            // Scan registry for installed browsers
            Map<String, String> browsers = new HashMap<>();
            Process process = Runtime.getRuntime().exec(
                    "reg query \"HKLM\\SOFTWARE\\Clients\\StartMenuInternet\""
            );
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("HKEY_LOCAL_MACHINE\\\\SOFTWARE\\\\Clients\\\\StartMenuInternet\\\\([^\\\\]+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String key = matcher.group(1);
                    String browserDisplayName = null;

                    // Try LocalizedString first
                    Process nameProc = Runtime.getRuntime().exec(
                            "reg query \"HKLM\\SOFTWARE\\Clients\\StartMenuInternet\\" + key + "\" /v LocalizedString"
                    );
                    BufferedReader nameReader = new BufferedReader(new InputStreamReader(nameProc.getInputStream()));
                    String nameLine;
                    while ((nameLine = nameReader.readLine()) != null) {
                        if (nameLine.contains("LocalizedString")) {
                            String[] parts = nameLine.split("REG_SZ");
                            if (parts.length > 1) {
                                browserDisplayName = parts[1].trim().replace("\"", "");
                            }
                        }
                    }
                    nameReader.close();

                    // Fallback to (Default) value if LocalizedString is not found
                    if (browserDisplayName == null) {
                        Process defaultProc = Runtime.getRuntime().exec(
                                "reg query \"HKLM\\SOFTWARE\\Clients\\StartMenuInternet\\" + key + "\" /ve"
                        );
                        BufferedReader defaultReader = new BufferedReader(new InputStreamReader(defaultProc.getInputStream()));
                        while ((nameLine = defaultReader.readLine()) != null) {
                            if (nameLine.contains("REG_SZ")) {
                                String[] parts = nameLine.split("REG_SZ");
                                if (parts.length > 1) {
                                    browserDisplayName = parts[1].trim().replace("\"", "");
                                }
                            }
                        }
                        defaultReader.close();
                    }

                    if (browserDisplayName != null && !browserDisplayName.isEmpty()) {
                        browsers.put(browserDisplayName.toLowerCase(), key);
                    }
                }
            }
            reader.close();

            // Match browser name
            String progId = browsers.get(browserName.trim().toLowerCase());
            if (progId == null) {
                LOGGER.warning("Browser not found: " + browserName);
                operation.setRemarks("Browser not found: " + browserName);
                return false;
            }
            // Set default browser using ProgID
            String command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process cmd.exe -Verb runAs -ArgumentList '/c assoc .htm=" + progId + " & assoc .html=" + progId + "'\"";
            LOGGER.info("Executing change browser command: " + command);
            Process setProcess = Runtime.getRuntime().exec(command);
            int exitCode = setProcess.waitFor();
            LOGGER.info("Change browser command exit code: " + exitCode);
            if (exitCode == 0) {
                operation.setRemarks("Default browser changed to " + browserName + ".");
                return true;
            } else {
                operation.setRemarks("Failed to change default browser. The ProgID '" + progId + "' may not be valid or registered.");
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error changing default browser: " + e.getMessage());
            operation.setRemarks("Error changing default browser: " + e.getMessage());
            return false;
        }
    }
}
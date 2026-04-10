using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Diagnostics;

namespace GuiAgentUtils.Actions
{
    /// <summary>
    /// Closes all instances of a process by process name
    /// </summary>
    public class AppCloseAllAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                var processName = command.ProcessName;
                if (string.IsNullOrEmpty(processName))
                {
                    throw new InvalidOperationException("ProcessName is required for close_all action");
                }

                // Remove .exe extension if present
                processName = processName.Replace(".exe", "", StringComparison.OrdinalIgnoreCase);

                Logger?.LogToFile($"Closing all instances of: {processName}");

                var processes = Process.GetProcessesByName(processName);

                if (processes.Length == 0)
                {
                    Logger?.LogToFile($"No instances of '{processName}' found");
                    result.Success = true;
                    result.Message = $"No instances of '{processName}' found (already closed)";
                    result.Data = new { ProcessName = processName, InstancesClosed = 0 };

                    HandleSuccess(command, result);
                    return result;
                }

                Logger?.LogToFile($"Found {processes.Length} instance(s) of '{processName}'");

                int closedCount = 0;
                var failedProcesses = new List<int>();

                foreach (var process in processes)
                {
                    try
                    {
                        var pid = process.Id;
                        Logger?.LogToFile($"Closing process PID: {pid}");

                        // Try graceful close first
                        if (!process.HasExited)
                        {
                            process.CloseMainWindow();

                            if (process.WaitForExit(3000))
                            {
                                Logger?.LogToFile($"Process {pid} closed gracefully");
                                closedCount++;
                                continue;
                            }
                        }

                        // Force kill if still running
                        if (!process.HasExited)
                        {
                            Logger?.LogToFile($"Force killing process {pid}");
                            process.Kill();

                            if (process.WaitForExit(2000))
                            {
                                Logger?.LogToFile($"Process {pid} killed successfully");
                                closedCount++;
                            }
                            else
                            {
                                Logger?.LogToFile($"Failed to kill process {pid}");
                                failedProcesses.Add(pid);
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger?.LogToFile($"Error closing process {process.Id}: {ex.Message}");
                        failedProcesses.Add(process.Id);
                    }
                    finally
                    {
                        process.Dispose();
                    }
                }

                if (failedProcesses.Any())
                {
                    result.Success = false;
                    result.Message = $"Closed {closedCount}/{processes.Length} instances. Failed PIDs: {string.Join(", ", failedProcesses)}";
                    result.ErrorMessage = $"Failed to close {failedProcesses.Count} instance(s)";
                    result.Data = new
                    {
                        ProcessName = processName,
                        TotalInstances = processes.Length,
                        InstancesClosed = closedCount,
                        FailedProcessIds = failedProcesses
                    };

                    Logger.OutputRemark(command.Id ?? "unknown", false, result.Message);
                }
                else
                {
                    result.Success = true;
                    result.Message = $"Successfully closed all {closedCount} instance(s) of '{processName}'";
                    result.Data = new
                    {
                        ProcessName = processName,
                        InstancesClosed = closedCount
                    };

                    HandleSuccess(command, result);
                }
            }
            catch (Exception ex)
            {
                HandleScreenshotOnFailure(automator, command, result, ex);
            }
            finally
            {
                sw.Stop();
                result.Duration = sw.Elapsed;
            }

            return result;
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) && !string.IsNullOrEmpty(command.ProcessName);
        }
    }
}
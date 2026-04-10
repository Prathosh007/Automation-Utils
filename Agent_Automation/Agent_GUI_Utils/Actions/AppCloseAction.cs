using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using FlaUI.Core.AutomationElements;
using FlaUI.UIA3;
using FlaUI.Core.Definitions;
using System.Diagnostics;

namespace GuiAgentUtils.Actions
{
    /// <summary>
    /// Closes a specific application window by name
    /// </summary>
    public class AppCloseAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                var windowName = command.WindowName;
                if (string.IsNullOrEmpty(windowName))
                {
                    throw new InvalidOperationException("WindowName is required for close action");
                }

                Logger?.LogToFile($"Attempting to close window: {windowName}");

                using var automation = new UIA3Automation();
                var desktop = automation.GetDesktop();

                // Find the window
                var window = FindWindowByName(desktop, windowName, command.WindowPartialMatch);

                if (window == null)
                {
                    Logger?.LogToFile($"Window not found: {windowName}");
                    result.Success = false;
                    result.Message = $"Window not found: {windowName}";
                    result.ErrorMessage = "Window not found";

                    Logger.OutputRemark(command.Id ?? "unknown", false, $"Window not found: {windowName}");
                    return result;
                }

                Logger?.LogToFile($"Found window: {window.Name} (PID: {window.Properties.ProcessId})");

                // Try graceful close first
                bool closed = TryGracefulClose(window);

                if (!closed)
                {
                    // Force close if graceful failed
                    Logger?.LogToFile("Graceful close failed, attempting force close");
                    closed = TryForceClose(window);
                }

                if (closed)
                {
                    result.Success = true;
                    result.Message = $"Window closed successfully: {windowName}";
                    result.Data = new { WindowName = window.Name };

                    HandleSuccess(command, result);
                }
                else
                {
                    throw new InvalidOperationException($"Failed to close window: {windowName}");
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

        private AutomationElement FindWindowByName(AutomationElement desktop, string windowName, bool partialMatch)
        {
            try
            {
                var windows = desktop.FindAllChildren(cf =>
                    cf.ByControlType(ControlType.Window).Or(cf.ByControlType(ControlType.Pane)));

                foreach (var window in windows)
                {
                    try
                    {
                        var name = window.Name ?? "";

                        if (partialMatch)
                        {
                            if (name.Contains(windowName, StringComparison.OrdinalIgnoreCase))
                            {
                                Logger?.LogToFile($"Found window by partial match: '{name}' contains '{windowName}'");
                                return window;
                            }
                        }
                        else
                        {
                            if (name.Equals(windowName, StringComparison.OrdinalIgnoreCase))
                            {
                                Logger?.LogToFile($"Found window by exact match: '{name}'");
                                return window;
                            }
                        }
                    }
                    catch
                    {
                        continue;
                    }
                }
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error searching for window: {ex.Message}");
            }

            return null;
        }

        private bool TryGracefulClose(AutomationElement window)
        {
            try
            {
                Logger?.LogToFile("Attempting graceful window close...");

                // FlaUI correct way to get WindowPattern
                var windowPattern = window.Patterns.Window;
                if (windowPattern.IsSupported)
                {
                    try
                    {
                        windowPattern.Pattern.Close();
                        Thread.Sleep(1000);

                        // Check if process still exists
                        try
                        {
                            var processId = window.Properties.ProcessId.Value;
                            var process = Process.GetProcessById(processId);

                            if (process.HasExited)
                            {
                                Logger?.LogToFile("Window closed gracefully");
                                return true;
                            }
                        }
                        catch (ArgumentException)
                        {
                            // Process doesn't exist anymore - success
                            Logger?.LogToFile("Process terminated (graceful close succeeded)");
                            return true;
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger?.LogToFile($"WindowPattern.Close() failed: {ex.Message}");
                    }
                }
                else
                {
                    Logger?.LogToFile("WindowPattern not supported for this window");
                }

                return false;
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Graceful close failed: {ex.Message}");
                return false;
            }
        }

        private bool TryForceClose(AutomationElement window)
        {
            try
            {
                Logger?.LogToFile("Attempting force close...");

                var processId = window.Properties.ProcessId.Value;
                if (processId <= 0)
                {
                    Logger?.LogToFile("Invalid process ID");
                    return false;
                }

                var process = Process.GetProcessById(processId);
                process.Kill();

                var exited = process.WaitForExit(5000);

                if (exited)
                {
                    Logger?.LogToFile("Process terminated forcefully");
                    return true;
                }
                else
                {
                    Logger?.LogToFile("Process did not exit within timeout");
                    return false;
                }
            }
            catch (ArgumentException)
            {
                // Process doesn't exist - consider it closed
                Logger?.LogToFile("Process already terminated");
                return true;
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Force close failed: {ex.Message}");
                return false;
            }
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) && !string.IsNullOrEmpty(command.WindowName);
        }
    }
}
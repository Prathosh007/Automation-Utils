using GuiAgentUtils.Core;
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Conditions;
using FlaUI.Core.Definitions;
using FlaUI.UIA3;
using System.Reflection;

namespace GuiAgentUtils.Actions
{
    public abstract class BaseAction : IAction
    {
        protected Logger? Logger { get; private set; }

        public abstract CommandResult Execute(GuiAutomatorBase automator, Command command);

        public virtual bool ValidateCommand(Command command)
        {
            return !string.IsNullOrEmpty(command.Action);
        }

        public void SetLogger(Logger logger)
        {
            Logger = logger;
        }

        protected CommandResult CreateResult(Command command)
        {
            return new CommandResult
            {
                CommandId = command.Id ?? "unknown",
                Action = command.Action
            };
        }

        protected void LogExecution(Command command)
        {
            Logger?.LogToFile($"EXECUTING: {command.Action} - {command.Description ?? command.Id ?? "No description"}");
        }

        /// <summary>
        /// Find the target window for this command
        /// </summary>
        protected AutomationElement FindTargetWindow(GuiAutomatorBase automator, Command command)
        {
            try
            {
                // If no window targeting specified, use current main window
                if (string.IsNullOrEmpty(command.WindowName) &&
                    string.IsNullOrEmpty(command.WindowTitle) &&
                    string.IsNullOrEmpty(command.WindowAutomationId))
                {
                    return GetMainWindow(automator);
                }

                Logger?.LogToFile($"Searching for target window: '{command.WindowName ?? command.WindowTitle}' (PartialMatch: {command.WindowPartialMatch})");

                // Search for specific window
                using var automation = new UIA3Automation();
                var desktop = automation.GetDesktop();
                var windows = desktop.FindAllChildren()
                    .Where(w => w.ControlType == ControlType.Window || w.ControlType == ControlType.Pane)
                    .ToList();

                Logger?.LogToFile($"Found {windows.Count} windows to search");

                foreach (var window in windows)
                {
                    try
                    {
                        if (IsTargetWindow(window, command))
                        {
                            Logger?.LogToFile($"Found target window: '{window.Name}' for command {command.Id}");
                            return window;
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger?.LogToFile($"Error checking window: {ex.Message}");
                        continue;
                    }
                }

                Logger?.LogToFile($"Target window not found for command {command.Id}, using main window");
                return GetMainWindow(automator);
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error finding target window: {ex.Message}");
                return GetMainWindow(automator);
            }
        }

        /// <summary>
        /// Check if window matches the targeting criteria
        /// </summary>
        private bool IsTargetWindow(AutomationElement window, Command command)
        {
            try
            {
                var windowName = window.Name ?? "";
                var windowAutomationId = window.AutomationId ?? "";

                Logger?.LogToFile($"Checking window: '{windowName}' (AutomationId: '{windowAutomationId}')");

                // Check by AutomationId
                if (!string.IsNullOrEmpty(command.WindowAutomationId))
                {
                    if (windowAutomationId.Equals(command.WindowAutomationId, StringComparison.OrdinalIgnoreCase))
                    {
                        Logger?.LogToFile($"Window matched by AutomationId: {command.WindowAutomationId}");
                        return true;
                    }
                }

                // Check by WindowName
                if (!string.IsNullOrEmpty(command.WindowName))
                {
                    if (command.WindowPartialMatch)
                    {
                        if (windowName.Contains(command.WindowName, StringComparison.OrdinalIgnoreCase))
                        {
                            Logger?.LogToFile($"Window matched by partial WindowName: '{command.WindowName}' in '{windowName}'");
                            return true;
                        }
                    }
                    else
                    {
                        if (windowName.Equals(command.WindowName, StringComparison.OrdinalIgnoreCase))
                        {
                            Logger?.LogToFile($"Window matched by exact WindowName: {command.WindowName}");
                            return true;
                        }
                    }
                }

                // Check by WindowTitle (alias for WindowName)
                if (!string.IsNullOrEmpty(command.WindowTitle))
                {
                    if (command.WindowPartialMatch)
                    {
                        if (windowName.Contains(command.WindowTitle, StringComparison.OrdinalIgnoreCase))
                        {
                            Logger?.LogToFile($"Window matched by partial WindowTitle: '{command.WindowTitle}' in '{windowName}'");
                            return true;
                        }
                    }
                    else
                    {
                        if (windowName.Equals(command.WindowTitle, StringComparison.OrdinalIgnoreCase))
                        {
                            Logger?.LogToFile($"Window matched by exact WindowTitle: {command.WindowTitle}");
                            return true;
                        }
                    }
                }

                return false;
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error in IsTargetWindow: {ex.Message}");
                return false;
            }
        }

        /// <summary>
        /// Get main window from automator using reflection
        /// </summary>
        private AutomationElement GetMainWindow(GuiAutomatorBase automator)
        {
            try
            {
                var mainWindowField = typeof(GuiAutomatorBase)
                    .GetField("_mainWindow", BindingFlags.NonPublic | BindingFlags.Instance);

                var mainWindow = (AutomationElement)mainWindowField?.GetValue(automator);
                if (mainWindow != null)
                {
                    Logger?.LogToFile($"Using main window: '{mainWindow.Name}'");
                    return mainWindow;
                }

                Logger?.LogToFile("Main window is null, using desktop");
                return new UIA3Automation().GetDesktop();
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error getting main window: {ex.Message}");
                return new UIA3Automation().GetDesktop();
            }
        }

        /// <summary>
        /// Find element within the target window
        /// </summary>
        protected AutomationElement FindElementInTargetWindow(GuiAutomatorBase automator, Command command)
        {
            var targetWindow = FindTargetWindow(automator, command);
            if (targetWindow == null)
            {
                throw new InvalidOperationException("Target window not found");
            }

            return FindElementInWindow(targetWindow, command);
        }

        /// <summary>
        /// Get the window title for Win32 fallback lookups
        /// </summary>
        protected string? GetWin32WindowTitle(Command command)
        {
            return command.WindowName ?? command.WindowTitle ?? command.DialogTitle;
        }

        /// <summary>
        /// Win32 fallback: Find a child element in an elevated/protected window.
        /// Returns a Win32ElementInfo when UIA3 fails with E_ACCESSDENIED.
        /// </summary>
        protected Win32ElementInfo? FindWin32ElementInTargetWindow(GuiAutomatorBase automator, Command command)
        {
            var windowTitle = GetWin32WindowTitle(command);
            if (string.IsNullOrEmpty(windowTitle))
            {
                Logger?.LogToFile("[WIN32] No window title specified for Win32 fallback");
                return null;
            }

            Logger?.LogToFile($"[WIN32] Attempting Win32 fallback for window: '{windowTitle}'");

            var window = automator.Win32FindWindow(windowTitle, partialMatch: command.WindowPartialMatch || true);
            if (window == null)
            {
                Logger?.LogToFile($"[WIN32] Window not found: '{windowTitle}'");
                return null;
            }

            Logger?.LogToFile($"[WIN32] Found window: '{window.Text}' (Handle: 0x{window.Handle:X})");

            // Search for the element by name/text
            var element = Win32ElementInfo.FindChild(
                window.Handle,
                text: command.Name ?? command.ButtonName,
                className: null,
                partialMatch: command.MatchPartial
            );

            if (element != null)
            {
                Logger?.LogToFile($"[WIN32] Found element: '{element.Text}' ({element.ClassName}, Handle: 0x{element.Handle:X})");
            }
            else
            {
                Logger?.LogToFile($"[WIN32] Element not found: Name='{command.Name}' in window '{windowTitle}'");
            }

            return element;
        }

        /// <summary>
        /// Find element within a specific window with timeout
        /// </summary>
        private AutomationElement FindElementInWindow(AutomationElement window, Command command)
        {
            var timeout = command.Timeout ?? 10;
            var startTime = DateTime.Now;

            while (DateTime.Now - startTime < TimeSpan.FromSeconds(timeout))
            {
                try
                {
                    var element = FindElementInWindowOnce(window, command);
                    if (element != null)
                    {
                        Logger?.LogToFile($"Element found: AutomationId='{element.AutomationId}', Name='{element.Name}'");
                        return element;
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Error during element search: {ex.Message}");
                }

                Thread.Sleep(500); // Wait 500ms between attempts
            }

            return null; // Element not found within timeout
        }

        /// <summary>
        /// Single attempt to find element in window
        /// </summary>
        private AutomationElement FindElementInWindowOnce(AutomationElement window, Command command)
        {
            try
            {
                var cf = new ConditionFactory(new UIA3PropertyLibrary());

                if (command.MatchPartial && !string.IsNullOrWhiteSpace(command.Name))
                {
                    var elements = window.FindAllDescendants();
                    return elements.FirstOrDefault(el =>
                        (command.ControlType == null || el.Properties.ControlType.ValueOrDefault == command.ControlType) &&
                        el.Properties.Name.ValueOrDefault != null &&
                        el.Properties.Name.ValueOrDefault.Contains(command.Name, StringComparison.OrdinalIgnoreCase));
                }

                var conditions = new List<ConditionBase>();

                if (!string.IsNullOrWhiteSpace(command.AutomationId))
                    conditions.Add(cf.ByAutomationId(command.AutomationId));

                if (!string.IsNullOrWhiteSpace(command.Name) && !command.MatchPartial)
                    conditions.Add(cf.ByName(command.Name));

                if (command.ControlType.HasValue)
                    conditions.Add(cf.ByControlType(command.ControlType.Value));

                ConditionBase condition = null;
                if (conditions.Count == 1)
                    condition = conditions[0];
                else if (conditions.Count > 1)
                    condition = new AndCondition(conditions.ToArray());

                return condition == null ? null : window.FindFirstDescendant(condition);
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error in FindElementInWindowOnce: {ex.Message}");
                return null;
            }
        }

        // Helper method to suppress console output from GuiAutomatorBase methods
        protected T ExecuteWithSuppressedOutput<T>(Func<T> action)
        {
            var originalOut = Console.Out;
            var stringWriter = new StringWriter();

            try
            {
                Console.SetOut(stringWriter);
                var result = action();

                // Log captured output to file
                var capturedOutput = stringWriter.ToString();
                if (!string.IsNullOrEmpty(capturedOutput))
                {
                    Logger?.LogToFile($"GuiAutomator Output: {capturedOutput}");
                }

                return result;
            }
            finally
            {
                Console.SetOut(originalOut);
                stringWriter.Dispose();
            }
        }

        protected void ExecuteWithSuppressedOutput(Action action)
        {
            ExecuteWithSuppressedOutput(() => { action(); return true; });
        }

        protected void HandleScreenshotOnFailure(GuiAutomatorBase automator, Command command, CommandResult result, Exception ex)
        {
            result.Success = false;
            result.ErrorMessage = ex.Message;
            result.Message = $"Command failed: {ex.Message}";

            // Log detailed error to file
            Logger?.LogToFile($"ERROR - Command: {command.Id}, Action: {command.Action}, Error: {ex.Message}");
            if (!string.IsNullOrEmpty(command.WindowName) || !string.IsNullOrEmpty(command.WindowTitle))
            {
                Logger?.LogToFile($"  Target Window: {command.WindowName ?? command.WindowTitle}");
            }

            if (command.TakeScreenshotOnFailure)
            {
                try
                {
                    var errorScreenshot = $"Screenshots/error_{command.Id}_{DateTime.Now:yyyyMMdd_HHmmss}.png";
                    ExecuteWithSuppressedOutput(() => automator.CaptureScreenshot(errorScreenshot));
                    result.ScreenshotPath = errorScreenshot;
                    Logger?.LogToFile($"Error screenshot saved: {errorScreenshot}");
                }
                catch (Exception screenshotEx)
                {
                    Logger?.LogToFile($"Could not capture error screenshot: {screenshotEx.Message}");
                }
            }

            // Output clean result to console for GOAT server
            var remarks = ExtractRemarks(command, ex.Message);
            Logger.OutputRemark(command.Id ?? "unknown", false, remarks);
        }

        protected void HandleSuccess(Command command, CommandResult result)
        {
            result.Success = true;
            result.Message = $"{command.Action} command completed successfully";

            // Log success to file
            Logger?.LogToFile($"SUCCESS - Command: {command.Id}, Action: {command.Action}, Duration: {result.Duration.TotalMilliseconds}ms");
            if (!string.IsNullOrEmpty(command.WindowName) || !string.IsNullOrEmpty(command.WindowTitle))
            {
                Logger?.LogToFile($"  Target Window: {command.WindowName ?? command.WindowTitle}");
            }

            // Output clean result to console for GOAT server
            var remarks = ExtractRemarks(command);
            Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
        }

        private string ExtractRemarks(Command command, string? errorMessage = null)
        {
            if (!string.IsNullOrEmpty(errorMessage))
            {
                return $"Failed: {errorMessage}";
            }

            if (!string.IsNullOrEmpty(command.ExpectedResult))
            {
                return command.ExpectedResult;
            }

            if (!string.IsNullOrEmpty(command.Description))
            {
                return command.Description;
            }

            return $"{command.Action} completed successfully";
        }
    }
}
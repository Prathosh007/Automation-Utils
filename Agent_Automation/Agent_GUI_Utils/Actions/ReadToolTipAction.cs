using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Conditions;
using FlaUI.Core.Definitions;
using FlaUI.Core.Input;
using FlaUI.UIA3;
using System.Drawing;

namespace GuiAgentUtils.Actions
{
    public class ReadTooltipAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                // Find the target element to hover over
                var targetElement = FindElementInTargetWindow(automator, command);
                if (targetElement == null)
                {
                    // Win32 fallback: try to read text from the elevated window
                    Logger?.LogToFile("UIA3 element not found for tooltip, attempting Win32 fallback...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        var win32Text = automator.Win32ReadText(windowTitle, command.Name, null, command.MatchPartial);
                        if (!string.IsNullOrEmpty(win32Text))
                        {
                            result.Data = win32Text;
                            Logger?.LogToFile($"[WIN32] Read text via Win32 fallback: '{win32Text}'");

                            if (!string.IsNullOrEmpty(command.ExpectedValue))
                            {
                                var vType = command.ValidationType ?? "contains";
                                var isValid = ValidateTooltipText(win32Text, command.ExpectedValue, vType, command.CaseSensitive);
                                if (!isValid)
                                {
                                    var remarks = GenerateFailureRemarks(win32Text, command.ExpectedValue, vType);
                                    Logger.OutputRemark(command.Id ?? "unknown", false, remarks);
                                    result.Success = false;
                                    result.Message = remarks;
                                    result.ErrorMessage = remarks;
                                    return result;
                                }
                            }

                            HandleSuccess(command, result);
                            return result;
                        }
                    }

                    var windowInfo = !string.IsNullOrEmpty(command.WindowName)
                        ? $" in window '{command.WindowName}'"
                        : "";
                    throw new InvalidOperationException($"Target element not found{windowInfo} for tooltip hover: AutomationId='{command.AutomationId}', Name='{command.Name}'");
                }

                Logger?.LogToFile($"Found target element for tooltip: {targetElement.Name ?? targetElement.AutomationId ?? "Unknown"}");

                // STRATEGY 1: Try to get HelpText directly from the element
                string tooltipText = "";
                try
                {
                    tooltipText = targetElement.HelpText;
                    if (!string.IsNullOrWhiteSpace(tooltipText))
                    {
                        Logger?.LogToFile($"Tooltip found directly from HelpText property: '{tooltipText}'");
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Could not read HelpText property: {ex.Message}");
                }

                // STRATEGY 2: Try to get tooltip from Name property
                if (string.IsNullOrWhiteSpace(tooltipText))
                {
                    try
                    {
                        var name = targetElement.Name;
                        if (!string.IsNullOrWhiteSpace(name) && IsTooltipLikeText(name))
                        {
                            tooltipText = name;
                            Logger?.LogToFile($"Tooltip found from Name property: '{tooltipText}'");
                        }
                    }
                    catch (Exception ex)
                    {
                        Logger?.LogToFile($"Could not read Name property: {ex.Message}");
                    }
                }

                // STRATEGY 3: If HelpText and Name are empty, try hovering
                if (string.IsNullOrWhiteSpace(tooltipText))
                {
                    Logger?.LogToFile("HelpText and Name are empty, trying hover method");
                    tooltipText = ExecuteWithSuppressedOutput(() => ReadTooltipFromElement(targetElement, command));
                }

                result.Data = tooltipText;
                Logger?.LogToFile($"Final tooltip text: '{tooltipText}'");

                // Apply validation if specified
                if (!string.IsNullOrEmpty(command.ExpectedValue))
                {
                    var validationType = command.ValidationType ?? "contains"; // Default to contains for tooltips
                    var isValid = ValidateTooltipText(tooltipText, command.ExpectedValue, validationType, command.CaseSensitive);

                    if (!isValid)
                    {
                        var remarks = GenerateFailureRemarks(tooltipText, command.ExpectedValue, validationType);
                        Logger?.LogToFile($"Tooltip validation failed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                        result.Success = false;
                        result.Message = remarks;
                        result.ErrorMessage = remarks;
                        return result;
                    }
                    else
                    {
                        var remarks = GenerateSuccessRemarks(tooltipText, command.ExpectedValue, validationType);
                        Logger?.LogToFile($"Tooltip validation passed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    }
                }
                else
                {
                    // No validation, just read
                    var remarks = !string.IsNullOrEmpty(tooltipText)
                        ? $"Tooltip read: '{tooltipText}'"
                        : "No tooltip text found";
                    Logger.OutputRemark(command.Id ?? "unknown", !string.IsNullOrEmpty(tooltipText), remarks);
                }

                if (string.IsNullOrEmpty(tooltipText))
                {
                    throw new InvalidOperationException("No tooltip text found after all attempts");
                }

                HandleSuccess(command, result);
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

        /// <summary>
        /// Move mouse to element and read tooltip text with retries
        /// </summary>
        private string ReadTooltipFromElement(AutomationElement targetElement, Command command)
        {
            try
            {
                // Store original mouse position
                var originalPosition = Mouse.Position;

                // Get element center point for more reliable hovering
                var rect = targetElement.BoundingRectangle;
                var centerX = (int)(rect.X + rect.Width / 2);
                var centerY = (int)(rect.Y + rect.Height / 2);

                Logger?.LogToFile($"Element bounds: X={rect.X}, Y={rect.Y}, Width={rect.Width}, Height={rect.Height}");
                Logger?.LogToFile($"Calculated center point: ({centerX}, {centerY})");

                // Validate coordinates are reasonable (not off-screen or invalid)
                if (centerY < 0 || centerY > 10000 || centerX < 0 || centerX > 10000)
                {
                    Logger?.LogToFile($"WARNING: Element coordinates seem invalid. Coordinates: ({centerX}, {centerY})");
                    return "";
                }

                // Move mouse to element center
                Logger?.LogToFile($"Moving mouse to element center: ({centerX}, {centerY})");
                Mouse.MoveTo(new Point(centerX, centerY));

                // Wait longer for tooltip with multiple retry attempts
                var tooltipDelay = Math.Max(command.Timeout ?? 2000, 2000); // At least 2 seconds
                var maxRetries = 3;
                string tooltipText = "";

                for (int retry = 0; retry < maxRetries && string.IsNullOrEmpty(tooltipText); retry++)
                {
                    if (retry > 0)
                    {
                        Logger?.LogToFile($"Retry attempt {retry + 1}/{maxRetries} to find tooltip");
                        Thread.Sleep(1000); // Wait 1 second between retries
                    }
                    else
                    {
                        var waitTime = Math.Min(tooltipDelay, 3000);
                        Logger?.LogToFile($"Waiting {waitTime}ms for tooltip to appear");
                        Thread.Sleep(waitTime); // Initial wait
                    }

                    // Search for tooltip
                    tooltipText = FindTooltipText();

                    if (!string.IsNullOrEmpty(tooltipText))
                    {
                        Logger?.LogToFile($"Tooltip found on attempt {retry + 1}: '{tooltipText}'");
                        break;
                    }
                    else
                    {
                        Logger?.LogToFile($"No tooltip found on attempt {retry + 1}");
                    }
                }

                // Restore original mouse position
                Mouse.MoveTo(originalPosition);
                Logger?.LogToFile($"Mouse restored to original position: ({originalPosition.X}, {originalPosition.Y})");

                return tooltipText;
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error reading tooltip: {ex.Message}");
                Logger?.LogToFile($"Stack trace: {ex.StackTrace}");
                return "";
            }
        }

        /// <summary>
        /// Find tooltip text using multiple strategies with error handling
        /// </summary>
        private string FindTooltipText()
        {
            try
            {
                using var automation = new UIA3Automation();
                var desktop = automation.GetDesktop();

                // Strategy 1: Look for ToolTip control type
                try
                {
                    Logger?.LogToFile("Strategy 1: Looking for ToolTip control type");
                    var tooltips = desktop.FindAllDescendants()
                        .Where(el => el.ControlType == ControlType.ToolTip)
                        .ToList();

                    Logger?.LogToFile($"Found {tooltips.Count} tooltip controls");

                    foreach (var tooltip in tooltips)
                    {
                        var text = GetElementText(tooltip);
                        if (!string.IsNullOrWhiteSpace(text))
                        {
                            Logger?.LogToFile($"Found tooltip via ToolTip control: '{text}'");
                            return text;
                        }
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Strategy 1 (ToolTip control) error: {ex.Message}");
                }

                // Strategy 2: Look for recently appeared text elements with tooltip-like content
                try
                {
                    Logger?.LogToFile("Strategy 2: Looking for text elements");
                    var recentTextElements = desktop.FindAllDescendants()
                        .Where(el => el.ControlType == ControlType.Text ||
                                    el.ControlType == ControlType.Document ||
                                    el.ClassName?.Contains("tooltip", StringComparison.OrdinalIgnoreCase) == true)
                        .ToList();

                    Logger?.LogToFile($"Found {recentTextElements.Count} text-like elements");

                    foreach (var element in recentTextElements)
                    {
                        var text = GetElementText(element);
                        if (!string.IsNullOrWhiteSpace(text) && IsTooltipLikeText(text))
                        {
                            Logger?.LogToFile($"Found tooltip via text element: '{text}'");
                            return text;
                        }
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Strategy 2 (Text elements) error: {ex.Message}");
                }

                // Strategy 3: Look for popup windows that might contain tooltip text
                try
                {
                    Logger?.LogToFile("Strategy 3: Looking for popup windows");
                    var popupWindows = desktop.FindAllChildren()
                        .Where(w => w.ControlType == ControlType.Window &&
                                   (w.ClassName?.Contains("tooltip", StringComparison.OrdinalIgnoreCase) == true ||
                                    w.ClassName?.Contains("popup", StringComparison.OrdinalIgnoreCase) == true))
                        .ToList();

                    Logger?.LogToFile($"Found {popupWindows.Count} popup windows");

                    foreach (var popup in popupWindows)
                    {
                        var text = GetElementText(popup);
                        if (!string.IsNullOrWhiteSpace(text))
                        {
                            Logger?.LogToFile($"Found tooltip via popup window: '{text}'");
                            return text;
                        }
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Strategy 3 (Popup windows) error: {ex.Message}");
                }

                // Strategy 4: Look for any element with HelpText property
                try
                {
                    Logger?.LogToFile("Strategy 4: Looking for elements with HelpText");
                    var elementsWithHelp = desktop.FindAllDescendants()
                        .Where(el => !string.IsNullOrWhiteSpace(el.HelpText))
                        .ToList();

                    Logger?.LogToFile($"Found {elementsWithHelp.Count} elements with HelpText");

                    foreach (var element in elementsWithHelp)
                    {
                        var helpText = element.HelpText;
                        if (!string.IsNullOrWhiteSpace(helpText) && IsTooltipLikeText(helpText))
                        {
                            Logger?.LogToFile($"Found tooltip via HelpText property: '{helpText}'");
                            return helpText;
                        }
                    }
                }
                catch (Exception ex)
                {
                    Logger?.LogToFile($"Strategy 4 (HelpText) error: {ex.Message}");
                }

                Logger?.LogToFile("No tooltip text found using any strategy");
                return "";
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error in FindTooltipText: {ex.Message}");
                return "";
            }
        }

        /// <summary>
        /// Extract text from an automation element
        /// </summary>
        private string GetElementText(AutomationElement element)
        {
            try
            {
                // Try multiple ways to get text
                var text = element.Name ??
                          element.AsLabel()?.Text ??
                          element.AsTextBox()?.Text ??
                          "";

                // Try value pattern
                if (string.IsNullOrEmpty(text) && element.Patterns.Value.IsSupported)
                {
                    try
                    {
                        text = element.Patterns.Value.Pattern.Value;
                    }
                    catch { }
                }

                // Try text pattern for document-like elements
                if (string.IsNullOrEmpty(text) && element.Patterns.Text.IsSupported)
                {
                    try
                    {
                        text = element.Patterns.Text.Pattern.DocumentRange.GetText(-1);
                    }
                    catch { }
                }

                return text?.Trim() ?? "";
            }
            catch
            {
                return "";
            }
        }

        /// <summary>
        /// Check if text looks like tooltip content
        /// </summary>
        private bool IsTooltipLikeText(string text)
        {
            // Tooltip text is usually short and descriptive
            if (string.IsNullOrWhiteSpace(text) || text.Length > 200)
                return false;

            // Common tooltip keywords for your use case
            var tooltipKeywords = new[]
            {
                "not reachable", "reachable", "port", "connection", "error",
                "failed", "success", "status", "unable", "timeout", "refused",
                "no issues", "issue", "warning", "ready", "completed"
            };

            return tooltipKeywords.Any(keyword =>
                text.Contains(keyword, StringComparison.OrdinalIgnoreCase));
        }

        /// <summary>
        /// Validate tooltip text against expected value
        /// </summary>
        private bool ValidateTooltipText(string actualText, string expectedValue, string validationType, bool caseSensitive)
        {
            try
            {
                var comparison = caseSensitive ? StringComparison.Ordinal : StringComparison.OrdinalIgnoreCase;

                return validationType.ToLower() switch
                {
                    "exact" => actualText.Equals(expectedValue, comparison),
                    "contains" => actualText.Contains(expectedValue, comparison),
                    "startswith" => actualText.StartsWith(expectedValue, comparison),
                    "endswith" => actualText.EndsWith(expectedValue, comparison),
                    "notempty" => !string.IsNullOrWhiteSpace(actualText),
                    _ => actualText.Contains(expectedValue, comparison) // Default to contains
                };
            }
            catch
            {
                return false;
            }
        }

        /// <summary>
        /// Generate failure remarks for tooltip validation
        /// </summary>
        private string GenerateFailureRemarks(string actualText, string expectedValue, string validationType)
        {
            return validationType.ToLower() switch
            {
                "exact" => $"Expected tooltip '{expectedValue}' but got '{actualText}'",
                "contains" => $"Expected tooltip to contain '{expectedValue}' but got '{actualText}'",
                "startswith" => $"Expected tooltip to start with '{expectedValue}' but got '{actualText}'",
                "endswith" => $"Expected tooltip to end with '{expectedValue}' but got '{actualText}'",
                "notempty" => $"Expected non-empty tooltip but got '{actualText}'",
                _ => $"Tooltip validation failed: Expected '{expectedValue}' but got '{actualText}'"
            };
        }

        /// <summary>
        /// Generate success remarks for tooltip validation
        /// </summary>
        private string GenerateSuccessRemarks(string actualText, string expectedValue, string validationType)
        {
            return validationType.ToLower() switch
            {
                "exact" => $"Tooltip matches exactly: '{actualText}'",
                "contains" => $"Tooltip contains expected text: '{actualText}'",
                "startswith" => $"Tooltip starts with expected text: '{actualText}'",
                "endswith" => $"Tooltip ends with expected text: '{actualText}'",
                "notempty" => $"Tooltip has content: '{actualText}'",
                _ => $"Tooltip validation passed: '{actualText}'"
            };
        }

        /// <summary>
        /// Find the target window for this command using enhanced BaseAction method
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
                    catch
                    {
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

                // Check by WindowName
                if (!string.IsNullOrEmpty(command.WindowName))
                {
                    if (command.WindowPartialMatch)
                    {
                        if (windowName.Contains(command.WindowName, StringComparison.OrdinalIgnoreCase))
                            return true;
                    }
                    else
                    {
                        if (windowName.Equals(command.WindowName, StringComparison.OrdinalIgnoreCase))
                            return true;
                    }
                }

                // Check by WindowTitle
                if (!string.IsNullOrEmpty(command.WindowTitle))
                {
                    if (command.WindowPartialMatch)
                    {
                        if (windowName.Contains(command.WindowTitle, StringComparison.OrdinalIgnoreCase))
                            return true;
                    }
                    else
                    {
                        if (windowName.Equals(command.WindowTitle, StringComparison.OrdinalIgnoreCase))
                            return true;
                    }
                }

                return false;
            }
            catch
            {
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
                    .GetField("_mainWindow", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);

                var mainWindow = (AutomationElement)mainWindowField?.GetValue(automator);
                if (mainWindow != null)
                {
                    return mainWindow;
                }

                return new UIA3Automation().GetDesktop();
            }
            catch
            {
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

                Thread.Sleep(500);
            }

            return null;
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
            catch
            {
                return null;
            }
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Conditions;
using FlaUI.Core.Definitions;
using FlaUI.UIA3;
using System.Reflection;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System;

namespace GuiAgentUtils.Actions
{
    public class ReadStatusByRowAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                // Find the label element (like textbox5 for "Active Directory Connectivity")
                var labelElement = FindElementInTargetWindow(automator, command);
                if (labelElement == null)
                {
                    // Win32 fallback: try to read all text from the elevated window
                    Logger?.LogToFile("UIA3 element not found, attempting Win32 fallback for ReadStatusByRow...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        var allText = automator.Win32ReadText(windowTitle, command.Name, null, command.MatchPartial);
                        if (!string.IsNullOrEmpty(allText))
                        {
                            result.Data = allText;
                            Logger?.LogToFile($"[WIN32] Read text via Win32: '{allText}'");

                            if (!string.IsNullOrEmpty(command.ExpectedValue))
                            {
                                var validationType = command.ValidationType ?? "exact";
                                var isValid = ValidateStatus(allText, command.ExpectedValue, validationType, command.CaseSensitive);
                                if (!isValid)
                                {
                                    var remarks = GenerateFailureRemarks(allText, command.ExpectedValue, validationType);
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
                    throw new InvalidOperationException($"Label element not found{windowInfo}: AutomationId='{command.AutomationId}', Name='{command.Name}'");
                }

                Logger?.LogToFile($"Found label element: '{labelElement.Name}' at position ({labelElement.BoundingRectangle.X}, {labelElement.BoundingRectangle.Y})");

                // Find the status text on the same row
                var statusText = FindStatusTextOnSameRow(automator, labelElement);

                if (string.IsNullOrEmpty(statusText))
                {
                    throw new InvalidOperationException($"No status text found on same row as '{labelElement.Name}'");
                }

                result.Data = statusText;
                Logger?.LogToFile($"Status text found: '{statusText}'");

                // Validation if needed
                if (!string.IsNullOrEmpty(command.ExpectedValue))
                {
                    var validationType = command.ValidationType ?? "exact";
                    var isValid = ValidateStatus(statusText, command.ExpectedValue, validationType, command.CaseSensitive);

                    if (!isValid)
                    {
                        var remarks = GenerateFailureRemarks(statusText, command.ExpectedValue, validationType);
                        Logger?.LogToFile($"Status validation failed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                        result.Success = false;
                        result.Message = remarks;
                        result.ErrorMessage = remarks;
                        return result;
                    }
                    else
                    {
                        var remarks = GenerateSuccessRemarks(statusText, command.ExpectedValue);
                        Logger?.LogToFile($"Status validation passed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    }
                }
                else
                {
                    // No validation, just read
                    var remarks = $"Status read: '{statusText}'";
                    Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
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
        /// Find status text on the same row as the label element
        /// </summary>
        private string FindStatusTextOnSameRow(GuiAutomatorBase automator, AutomationElement labelElement)
        {
            try
            {
                var labelBounds = labelElement.BoundingRectangle;
                var window = GetTargetWindow(automator, labelElement);

                Logger?.LogToFile($"Searching for status text on row at Y={labelBounds.Y}");

                // Get all text elements in the window
                var allTextElements = window.FindAllDescendants()
                    .Where(el => el.ControlType == ControlType.Text || el.ControlType == ControlType.Edit)
                    .Where(el => !string.IsNullOrWhiteSpace(el.Name))
                    .ToList();

                Logger?.LogToFile($"Found {allTextElements.Count} text elements to search");

                // Find text elements on the same row (similar Y coordinate) and to the right of label
                var candidateElements = allTextElements
                    .Where(el => {
                        var elementY = el.BoundingRectangle.Y;
                        var elementX = el.BoundingRectangle.X;
                        var yDiff = Math.Abs(elementY - labelBounds.Y);
                        var isRightOfLabel = elementX > labelBounds.Right;

                        Logger?.LogToFile($"  Checking element '{el.Name}' at ({elementX}, {elementY}), Y-diff={yDiff}, Right={isRightOfLabel}");

                        return yDiff < 30 && isRightOfLabel;
                    })
                    .OrderBy(el => el.BoundingRectangle.X)
                    .ToList();

                Logger?.LogToFile($"Found {candidateElements.Count} candidate status elements on same row");

                // Try to find a status-like text element
                foreach (var element in candidateElements)
                {
                    var text = element.Name?.Trim() ?? "";
                    Logger?.LogToFile($"  Candidate text: '{text}'");

                    // Skip if it's another label (too long or contains specific keywords)
                    if (IsLikelyLabel(text))
                    {
                        Logger?.LogToFile($"    Skipped (likely a label)");
                        continue;
                    }

                    // If it looks like a status, return it
                    if (IsLikelyStatus(text))
                    {
                        Logger?.LogToFile($"    Selected as status text");
                        return text;
                    }
                }

                // If no status-like text found, return the first candidate if any
                if (candidateElements.Any())
                {
                    var firstCandidate = candidateElements.First().Name?.Trim() ?? "";
                    Logger?.LogToFile($"No status-like text found, returning first candidate: '{firstCandidate}'");
                    return firstCandidate;
                }

                Logger?.LogToFile("No status text found on same row");
                return "";
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error in FindStatusTextOnSameRow: {ex.Message}");
                return "";
            }
        }

        /// <summary>
        /// Check if text is likely a label rather than status
        /// </summary>
        private bool IsLikelyLabel(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return false;

            // Labels are typically longer and contain descriptive words
            if (text.Length > 50) return true;

            var labelKeywords = new[]
            {
                "connectivity", "verification", "detection", "retrieval",
                "status of", "checking", "validating", "service", "directory"
            };

            return labelKeywords.Any(keyword =>
                text.Contains(keyword, StringComparison.OrdinalIgnoreCase));
        }

        /// <summary>
        /// Check if text looks like a status value
        /// </summary>
        private bool IsLikelyStatus(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return false;
            if (text.Length > 50) return false;

            var statusKeywords = new[]
            {
                "success", "failed", "error", "pass", "fail", "ok",
                "pending", "running", "completed", "active", "inactive",
                "enabled", "disabled", "connected", "disconnected"
            };

            var lowerText = text.ToLower();
            return statusKeywords.Any(keyword => lowerText.Contains(keyword));
        }

        /// <summary>
        /// Get the target window for searching
        /// </summary>
        private AutomationElement GetTargetWindow(GuiAutomatorBase automator, AutomationElement labelElement)
        {
            // Try to get the root window by traversing up from the label
            var current = labelElement;
            while (current != null)
            {
                if (current.ControlType == ControlType.Window || current.ControlType == ControlType.Pane)
                {
                    return current;
                }
                current = current.Parent;
            }

            // Fall back to main window
            return GetMainWindow(automator);
        }

        /// <summary>
        /// Get main window from automator
        /// </summary>
        private AutomationElement GetMainWindow(GuiAutomatorBase automator)
        {
            try
            {
                var mainWindowField = typeof(GuiAutomatorBase)
                    .GetField("_mainWindow", BindingFlags.NonPublic | BindingFlags.Instance);

                var mainWindow = (AutomationElement)mainWindowField?.GetValue(automator);
                if (mainWindow != null)
                    return mainWindow;

                return new UIA3Automation().GetDesktop();
            }
            catch
            {
                return new UIA3Automation().GetDesktop();
            }
        }

        /// <summary>
        /// Validate status text
        /// </summary>
        private bool ValidateStatus(string actualText, string expectedValue, string validationType, bool caseSensitive)
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
                    "oneof" => ValidateOneOf(actualText, expectedValue, comparison),
                    _ => actualText.Equals(expectedValue, comparison)
                };
            }
            catch
            {
                return false;
            }
        }

        private bool ValidateOneOf(string text, string expectedValues, StringComparison comparison)
        {
            var values = expectedValues.Split(',').Select(v => v.Trim());
            return values.Any(value => text.Equals(value, comparison));
        }

        private string GenerateFailureRemarks(string actualText, string expectedValue, string validationType)
        {
            return validationType.ToLower() switch
            {
                "exact" => $"Expected status '{expectedValue}' but got '{actualText}'",
                "contains" => $"Expected status to contain '{expectedValue}' but got '{actualText}'",
                "oneof" => $"Expected status to be one of [{expectedValue}] but got '{actualText}'",
                _ => $"Status validation failed: Expected '{expectedValue}' but got '{actualText}'"
            };
        }

        private string GenerateSuccessRemarks(string actualText, string expectedValue)
        {
            return $"Status validated successfully: '{actualText}'";
        }

        /// <summary>
        /// Find element in target window (from base implementation pattern)
        /// </summary>
        protected AutomationElement FindElementInTargetWindow(GuiAutomatorBase automator, Command command)
        {
            var targetWindow = FindTargetWindowForCommand(automator, command);
            if (targetWindow == null)
            {
                throw new InvalidOperationException("Target window not found");
            }

            return FindElementInWindow(targetWindow, command);
        }

        private AutomationElement FindTargetWindowForCommand(GuiAutomatorBase automator, Command command)
        {
            try
            {
                if (string.IsNullOrEmpty(command.WindowName) &&
                    string.IsNullOrEmpty(command.WindowTitle))
                {
                    return GetMainWindow(automator);
                }

                using var automation = new UIA3Automation();
                var desktop = automation.GetDesktop();
                var windows = desktop.FindAllChildren()
                    .Where(w => w.ControlType == ControlType.Window)
                    .ToList();

                foreach (var window in windows)
                {
                    try
                    {
                        var windowName = window.Name ?? "";
                        if (command.WindowPartialMatch)
                        {
                            if (windowName.Contains(command.WindowName ?? command.WindowTitle ?? "", StringComparison.OrdinalIgnoreCase))
                                return window;
                        }
                        else
                        {
                            if (windowName.Equals(command.WindowName ?? command.WindowTitle ?? "", StringComparison.OrdinalIgnoreCase))
                                return window;
                        }
                    }
                    catch
                    {
                        continue;
                    }
                }

                return GetMainWindow(automator);
            }
            catch
            {
                return GetMainWindow(automator);
            }
        }

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
                        return element;
                }
                catch { }

                Thread.Sleep(500);
            }

            return null;
        }

        private AutomationElement FindElementInWindowOnce(AutomationElement window, Command command)
        {
            try
            {
                var cf = new ConditionFactory(new UIA3PropertyLibrary());

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
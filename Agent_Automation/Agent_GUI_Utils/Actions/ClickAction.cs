using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using FlaUI.Core.Tools;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Input;
using FlaUI.Core.Definitions;
using System.Runtime.InteropServices;
using System.Windows;  // ✅ Add this - Point is here

namespace GuiAgentUtils.Actions
{
    public class ClickAction : BaseAction
    {
        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern bool AllowSetForegroundWindow(int dwProcessId);

        private const int SW_RESTORE = 9;
        private const int SW_SHOW = 5;

        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                // Find element in the target window
                var element = FindElementInTargetWindow(automator, command);

                if (element == null)
                {
                    // Win32 fallback for elevated/protected windows
                    Logger?.LogToFile("UIA3 element not found, attempting Win32 fallback...");
                    var win32Element = FindWin32ElementInTargetWindow(automator, command);

                    if (win32Element != null)
                    {
                        Logger?.LogToFile($"[WIN32] Clicking element: '{win32Element.Text}' ({win32Element.ClassName})");
                        bool win32Clicked = win32Element.ClassName == "Button"
                            ? Win32ElementInfo.ClickButton(win32Element.Handle)
                            : Win32ElementInfo.ClickControl(win32Element.Handle);

                        if (win32Clicked)
                        {
                            HandleSuccess(command, result);
                            return result;
                        }
                        throw new InvalidOperationException("Win32 click failed");
                    }

                    var windowInfo = !string.IsNullOrEmpty(command.WindowName) ? $" in window '{command.WindowName}'" : "";
                    throw new InvalidOperationException($"Button not found{windowInfo}: AutomationId='{command.AutomationId}', Name='{command.Name}'");
                }

                Logger?.LogToFile($"Found element: {element.Name} (AutomationId: {element.AutomationId})");

                // CRITICAL: Activate window before clicking
                if (!EnsureWindowActivated(element))
                {
                    Logger?.LogToFile("WARNING: Could not activate window, attempting click anyway");
                }

                // Try multiple click methods in order of preference
                bool clicked = false;

                // Method 1: Try Invoke Pattern (most reliable for buttons)
                clicked = TryInvokePattern(element);

                // Method 2: UIA pattern fallbacks (SelectionItem / LegacyIAccessible /
                // ExpandCollapse). These go through UIA, which crosses process integrity
                // boundaries — unlike SendInput, they keep working when the target window
                // is elevated or not in the foreground (the "Access is denied" case).
                // Required for WPF list/tree items (e.g. TextBlock inside a TreeViewItem)
                // that do not support Invoke and have no native HWND.
                if (!clicked)
                {
                    clicked = TryUiaPatternFallbacks(element);
                }

                // Method 3: Standard FlaUI Click
                if (!clicked)
                {
                    clicked = TryStandardClick(element);
                }

                // Method 4: Coordinate-based click (last resort; fails under UIPI/elevation)
                if (!clicked)
                {
                    clicked = TryCoordinateClick(element);
                }

                if (!clicked)
                {
                    throw new InvalidOperationException("All click methods failed");
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

        private bool EnsureWindowActivated(AutomationElement element)
        {
            try
            {
                // Find parent window
                var window = element;
                while (window != null && window.ControlType != ControlType.Window)
                {
                    window = window.Parent;
                }

                if (window == null)
                {
                    Logger?.LogToFile("Could not find parent window");
                    return false;
                }

                var windowHandle = window.Properties.NativeWindowHandle.ValueOrDefault;
                if (windowHandle == IntPtr.Zero)
                {
                    Logger?.LogToFile("Window handle is invalid");
                    return false;
                }

                Logger?.LogToFile($"Activating window: {window.Name} (Handle: {windowHandle})");

                // Get process ID and allow foreground
                var processId = window.Properties.ProcessId.ValueOrDefault;
                if (processId > 0)
                {
                    AllowSetForegroundWindow(processId);
                }

                // Restore if minimized
                ShowWindow(windowHandle, SW_RESTORE);
                Thread.Sleep(300);

                // Show window
                ShowWindow(windowHandle, SW_SHOW);
                Thread.Sleep(200);

                // Set foreground
                bool success = SetForegroundWindow(windowHandle);
                Thread.Sleep(500); // Give it time to activate

                // Verify it's in foreground
                var foregroundWindow = GetForegroundWindow();
                bool isActive = foregroundWindow == windowHandle;

                Logger?.LogToFile($"Window activation: {(isActive ? "SUCCESS" : "FAILED")}");

                if (!isActive)
                {
                    // Try one more time with focus
                    window.Focus();
                    Thread.Sleep(300);
                }

                return true;
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Window activation error: {ex.Message}");
                return false;
            }
        }

        private bool TryInvokePattern(AutomationElement element)
        {
            try
            {
                Logger?.LogToFile("Attempting Invoke Pattern...");

                var invokePattern = element.Patterns.Invoke;
                if (invokePattern.IsSupported)
                {
                    ExecuteWithSuppressedOutput(() => {
                        invokePattern.Pattern.Invoke();
                    });

                    Wait.UntilInputIsProcessed();
                    Logger?.LogToFile("Invoke Pattern succeeded");
                    return true;
                }
                else
                {
                    Logger?.LogToFile("Invoke Pattern not supported");
                }
            }
            catch (UnauthorizedAccessException ex)
            {
                Logger?.LogToFile($"Invoke Pattern failed - Access Denied: {ex.Message}");
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Invoke Pattern failed: {ex.Message}");
            }

            return false;
        }

        private bool TryStandardClick(AutomationElement element)
        {
            try
            {
                Logger?.LogToFile("Attempting standard click...");

                ExecuteWithSuppressedOutput(() => {
                    var btn = element.AsButton();
                    if (btn != null)
                    {
                        Logger?.LogToFile($"Clicking button: AutomationId={btn.AutomationId}, Name={btn.Name}");
                        btn.Click();
                    }
                    else
                    {
                        Logger?.LogToFile($"Clicking element: AutomationId={element.AutomationId}, Name={element.Name}");
                        element.Click();
                    }
                });

                Wait.UntilInputIsProcessed();
                Logger?.LogToFile("Standard click succeeded");
                return true;
            }
            catch (UnauthorizedAccessException ex)
            {
                Logger?.LogToFile($"Standard click failed - Access Denied: {ex.Message}");
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Standard click failed: {ex.Message}");
            }

            return false;
        }

        private bool TryCoordinateClick(AutomationElement element)
        {
            try
            {
                Logger?.LogToFile("Attempting coordinate-based click...");

                // Try to get clickable point
                try
                {
                    var clickablePoint = element.GetClickablePoint();

                    Logger?.LogToFile($"Clicking at point: X={clickablePoint.X}, Y={clickablePoint.Y}");

                    Mouse.MoveTo(clickablePoint);
                    Thread.Sleep(100);
                    Mouse.Click();

                    Wait.UntilInputIsProcessed();
                    Logger?.LogToFile("Coordinate click succeeded");
                    return true;
                }
                catch
                {
                    // If GetClickablePoint fails, use bounding rectangle center
                    var bounds = element.BoundingRectangle;
                    var centerX = bounds.Left + (bounds.Width / 2);
                    var centerY = bounds.Top + (bounds.Height / 2);

                    Logger?.LogToFile($"Clicking at center: X={centerX}, Y={centerY}");

                    // Create Point using System.Windows.Point
                    var centerPoint = new Point(centerX, centerY);
                    Mouse.MoveTo(centerPoint);
                    Thread.Sleep(100);
                    Mouse.Click();

                    Wait.UntilInputIsProcessed();
                    Logger?.LogToFile("Center coordinate click succeeded");
                    return true;
                }
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Coordinate click failed: {ex.Message}");
            }

            return false;
        }

        /// <summary>
        /// Activate an element using UIA control patterns instead of synthetic input.
        /// UIA calls cross process-integrity boundaries, so they succeed where SendInput
        /// gets "Access is denied" (elevated target / window not in foreground).
        /// Walks up from the target element to find an ancestor that supports a pattern,
        /// because in WPF the named element (e.g. a TextBlock with AutomationId 'dtcb')
        /// is often a child of the actionable item (e.g. a TreeViewItem).
        /// </summary>
        private bool TryUiaPatternFallbacks(AutomationElement element)
        {
            try
            {
                Logger?.LogToFile("Attempting UIA pattern fallbacks (SelectionItem/LegacyIAccessible/ExpandCollapse)...");

                // Search the element and up to a few ancestors for a usable pattern.
                var current = element;
                for (int depth = 0; depth < 5 && current != null; depth++)
                {
                    // SelectionItemPattern: selects a list/tree/tab item (the usual way to
                    // "click" a navigation tree node).
                    var selectionItem = current.Patterns.SelectionItem;
                    if (selectionItem.IsSupported)
                    {
                        ExecuteWithSuppressedOutput(() => selectionItem.Pattern.Select());
                        Wait.UntilInputIsProcessed();
                        Logger?.LogToFile($"SelectionItem.Select succeeded (ancestor depth {depth}: {current.ControlType})");
                        return true;
                    }

                    // LegacyIAccessiblePattern: invokes the control's default MSAA action.
                    var legacy = current.Patterns.LegacyIAccessible;
                    if (legacy.IsSupported)
                    {
                        ExecuteWithSuppressedOutput(() => legacy.Pattern.DoDefaultAction());
                        Wait.UntilInputIsProcessed();
                        Logger?.LogToFile($"LegacyIAccessible.DoDefaultAction succeeded (ancestor depth {depth}: {current.ControlType})");
                        return true;
                    }

                    // Some ancestors support Invoke even when the named child does not.
                    var invoke = current.Patterns.Invoke;
                    if (invoke.IsSupported)
                    {
                        ExecuteWithSuppressedOutput(() => invoke.Pattern.Invoke());
                        Wait.UntilInputIsProcessed();
                        Logger?.LogToFile($"Ancestor Invoke succeeded (depth {depth}: {current.ControlType})");
                        return true;
                    }

                    current = current.Parent;
                }

                Logger?.LogToFile("No usable UIA pattern found on element or ancestors");
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"UIA pattern fallback failed: {ex.Message}");
            }

            return false;
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}
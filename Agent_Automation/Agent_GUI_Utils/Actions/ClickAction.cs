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

                // Method 2: Standard FlaUI Click
                if (!clicked)
                {
                    clicked = TryStandardClick(element);
                }

                // Method 3: Coordinate-based click
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

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}
using FlaUIApplication = FlaUI.Core.Application;
using FlaUI.Core;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Conditions;
using FlaUI.Core.Definitions;
using FlaUI.Core.Input;
using FlaUI.Core.Patterns;
using FlaUI.Core.Tools;
using FlaUI.Core.WindowsAPI;
using FlaUI.UIA3;
using System.Collections.Concurrent;
using System.Drawing;
using System.Drawing.Imaging;
using GuiAgentUtils.Models;
using System.ServiceProcess;
using Microsoft.Win32;
using System.Runtime.InteropServices;
using System.Diagnostics;

namespace GuiAgentUtils.Utils
{
    public class GuiAutomatorBase : IDisposable
    {
        // Windows API imports for window management
        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern bool IsIconic(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern bool BringWindowToTop(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        // Constants for window management
        private const int SW_RESTORE = 9;
        private const int SW_SHOW = 5;
        private const int SW_NORMAL = 1;
        private static readonly IntPtr HWND_TOP = IntPtr.Zero;
        private const uint SWP_NOMOVE = 0x0002;
        private const uint SWP_NOSIZE = 0x0001;
        private const uint SWP_SHOWWINDOW = 0x0040;

        private readonly string? _appPath;
        protected FlaUIApplication? _app;
        protected UIA3Automation _automation;
        protected AutomationElement _mainWindow;
        private readonly ConcurrentDictionary<string, AutomationElement> _storedElements = new();

        public bool AutoFocusEnabled { get; set; } = true;

        public GuiAutomatorBase(string? appPath = null)
        {
            _appPath = appPath;
            _automation = new UIA3Automation();

            if (string.IsNullOrWhiteSpace(_appPath))
            {
                AttachToDesktop();
            }
            else
            {
                LaunchApp();
            }
        }

        private void LaunchApp()
        {
            try
            {
                var beforeProcesses = Process.GetProcesses().Select(p => p.ProcessName).ToHashSet();

                var launcherProcess = Process.Start(new ProcessStartInfo
                {
                    FileName = _appPath!,
                    UseShellExecute = true
                });

                Thread.Sleep(5000);
                AttachToDesktop();
            }
            catch (Exception ex)
            {
                AttachToDesktop();
            }
        }

        private void AttachToDesktop()
        {
            _mainWindow = _automation.GetDesktop();
        }

        private bool EnsureWindowVisible()
        {
            if (!AutoFocusEnabled)
                return true;

            try
            {
                if (_mainWindow == null)
                    return false;

                var windowHandle = _mainWindow.Properties.NativeWindowHandle.Value;
                var currentForeground = GetForegroundWindow();

                if (currentForeground != windowHandle)
                {
                    if (IsIconic(windowHandle))
                    {
                        ShowWindow(windowHandle, SW_RESTORE);
                        Thread.Sleep(300);
                    }

                    BringWindowToTop(windowHandle);
                    SetForegroundWindow(windowHandle);
                    SetWindowPos(windowHandle, HWND_TOP, 0, 0, 0, 0,
                        SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW);

                    Thread.Sleep(200);
                }

                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Could not ensure window visibility: {ex.Message}");
                return false;
            }
        }

        public void RefreshMainWindow()
        {
            Thread.Sleep(500);
            if (_app != null)
            {
                try
                {
                    var windows = _app.GetAllTopLevelWindows(_automation);
                    _mainWindow = windows
                        .FirstOrDefault(w => w.Title == _mainWindow.Properties.Name.Value)
                        ?? windows.FirstOrDefault()
                        ?? _mainWindow;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[WARN] Could not refresh main window: {ex.Message}");
                }
            }
        }

        public AutomationElement? ElementFromPoint(Point point)
        {
            try
            {
                return _automation.FromPoint(point);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Could not get element from point: {ex.Message}");
                return null;
            }
        }

        /// <summary>
        /// Win32 API fallback for when UIA3 ElementFromPoint fails (e.g. E_ACCESSDENIED on elevated windows).
        /// Returns element info at the given point, plus the full dialog child tree.
        /// </summary>
        public Win32ElementInfo? Win32ElementFromPoint(Point point)
        {
            return Win32ElementInfo.GetDialogTree(point.X, point.Y);
        }

        #region Win32 Fallback Methods for Actions

        /// <summary>
        /// Find a window by title using Win32 APIs. Works for elevated/protected windows.
        /// </summary>
        public Win32ElementInfo? Win32FindWindow(string? windowTitle, bool partialMatch = false)
        {
            if (string.IsNullOrEmpty(windowTitle)) return null;
            return Win32ElementInfo.FindWindowByTitle(windowTitle, partialMatch);
        }

        /// <summary>
        /// Find a child element in a window using Win32 APIs.
        /// Matches by text (Name) and/or className.
        /// </summary>
        public Win32ElementInfo? Win32FindElement(string? windowTitle, string? name, string? className = null, bool partialMatch = false)
        {
            // Find the target window first
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return null;

            return Win32ElementInfo.FindChild(window.Handle, name, className, partialMatch);
        }

        /// <summary>
        /// Click a button in an elevated window using Win32 BM_CLICK.
        /// </summary>
        public bool Win32ClickButton(string? windowTitle, string? buttonName, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null)
            {
                Console.WriteLine($"[WIN32] Window not found: '{windowTitle}'");
                return false;
            }

            var button = Win32ElementInfo.FindChild(window.Handle, buttonName, "Button", partialMatch);
            if (button == null)
            {
                Console.WriteLine($"[WIN32] Button not found: '{buttonName}' in window '{windowTitle}'");
                return false;
            }

            Console.WriteLine($"[WIN32] Clicking button: '{button.Text}' (Handle: 0x{button.Handle:X})");
            return Win32ElementInfo.ClickButton(button.Handle);
        }

        /// <summary>
        /// Read text from a control in an elevated window using Win32 WM_GETTEXT.
        /// </summary>
        public string Win32ReadText(string? windowTitle, string? name, string? className = null, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return "";

            if (!string.IsNullOrEmpty(name) || !string.IsNullOrEmpty(className))
            {
                var element = Win32ElementInfo.FindChild(window.Handle, name, className, partialMatch);
                if (element != null)
                    return Win32ElementInfo.ReadText(element.Handle);
            }

            // If no specific element targeted, read all Static text from the window
            var statics = Win32ElementInfo.FindAllChildren(window.Handle, className: "Static");
            var texts = statics
                .Select(s => Win32ElementInfo.ReadText(s.Handle))
                .Where(t => !string.IsNullOrEmpty(t))
                .ToList();

            return string.Join("\n", texts);
        }

        /// <summary>
        /// Enter text into an Edit control in an elevated window using Win32 WM_SETTEXT.
        /// </summary>
        public bool Win32EnterText(string? windowTitle, string? name, string text, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return false;

            var edit = Win32ElementInfo.FindChild(window.Handle, name, "Edit", partialMatch);
            if (edit == null)
            {
                // Try finding by just Edit class if no name match
                edit = Win32ElementInfo.FindChild(window.Handle, null, "Edit", false);
            }
            if (edit == null) return false;

            Console.WriteLine($"[WIN32] Entering text into Edit (Handle: 0x{edit.Handle:X})");
            return Win32ElementInfo.SetText(edit.Handle, text);
        }

        /// <summary>
        /// Toggle a checkbox in an elevated window using Win32 BM_CLICK.
        /// </summary>
        public bool Win32ToggleCheckbox(string? windowTitle, string? name, bool? checkState, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return false;

            var checkbox = Win32ElementInfo.FindChild(window.Handle, name, "Button", partialMatch);
            if (checkbox == null) return false;

            if (checkState.HasValue)
                return Win32ElementInfo.SetCheckState(checkbox.Handle, checkState.Value);

            return Win32ElementInfo.ClickButton(checkbox.Handle); // Just toggle
        }

        /// <summary>
        /// Select an item in a ComboBox in an elevated window using Win32 CB_SELECTSTRING.
        /// </summary>
        public bool Win32SelectComboItem(string? windowTitle, string? name, string itemText, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return false;

            var combo = Win32ElementInfo.FindChild(window.Handle, name, "ComboBox", partialMatch);
            if (combo == null)
            {
                combo = Win32ElementInfo.FindChild(window.Handle, null, "ComboBox", false);
            }
            if (combo == null) return false;

            Console.WriteLine($"[WIN32] Selecting '{itemText}' in ComboBox (Handle: 0x{combo.Handle:X})");
            return Win32ElementInfo.SelectComboItem(combo.Handle, itemText);
        }

        /// <summary>
        /// Double-click a control in an elevated window.
        /// </summary>
        public bool Win32DoubleClick(string? windowTitle, string? name, string? className = null, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return false;

            var element = Win32ElementInfo.FindChild(window.Handle, name, className, partialMatch);
            if (element == null) return false;

            return Win32ElementInfo.DoubleClickControl(element.Handle);
        }

        /// <summary>
        /// Right-click a control in an elevated window.
        /// </summary>
        public bool Win32RightClick(string? windowTitle, string? name, string? className = null, bool partialMatch = false)
        {
            var window = Win32FindWindow(windowTitle, partialMatch: true);
            if (window == null) return false;

            var element = Win32ElementInfo.FindChild(window.Handle, name, className, partialMatch);
            if (element == null) return false;

            return Win32ElementInfo.RightClickControl(element.Handle);
        }

        #endregion

        public void WaitForElement(string? automationId = null,
            string? name = null,
            ControlType? controlType = null,
            bool matchPartial = false,
            int timeoutSeconds = 30)
        {
            try
            {
                EnsureWindowVisible();

                if (matchPartial && !string.IsNullOrWhiteSpace(name))
                {
                    Retry.WhileNull(
                        () => _mainWindow
                            .FindAllDescendants()
                            .FirstOrDefault(el =>
                                el.Properties.ControlType.Value == controlType &&
                                el.Properties.Name.Value != null &&
                                el.Properties.Name.Value.Contains(name, StringComparison.OrdinalIgnoreCase)),
                        TimeSpan.FromSeconds(timeoutSeconds),
                        TimeSpan.FromSeconds(1),
                        throwOnTimeout: true);
                    return;
                }

                var cf = new ConditionFactory(new UIA3PropertyLibrary());
                var cond = BuildCondition(cf, automationId, name, controlType, matchPartial);
                var result = Retry.WhileNull(
                    () => cond == null ? _mainWindow : _mainWindow.FindFirstDescendant(cond),
                    TimeSpan.FromSeconds(timeoutSeconds),
                    TimeSpan.FromSeconds(1),
                    throwOnTimeout: true).Result;

                Console.WriteLine($"[LOG] Element found after waiting");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Wait for element failed: {ex.Message}");
                throw new System.TimeoutException($"Element not found within {timeoutSeconds} seconds: AutomationId='{automationId}', Name='{name}'", ex);
            }
        }

        public AutomationElement? FindElement(string? automationId = null, string? name = null, ControlType? controlType = null, bool matchPartial = false)
        {
            try
            {
                var element = FindElementInWindow(_mainWindow, automationId, name, controlType, matchPartial);
                if (element != null) return element;

                var desktop = _automation.GetDesktop();
                var allWindows = desktop.FindAllChildren()
                    .Where(w => w.ControlType == ControlType.Window || w.ControlType == ControlType.Pane)
                    .ToList();

                foreach (var window in allWindows)
                {
                    element = FindElementInWindow(window, automationId, name, controlType, matchPartial);
                    if (element != null)
                    {
                        Console.WriteLine($"[DEBUG] Found element in window: {window.Name}, switching main window");
                        _mainWindow = window;
                        return element;
                    }
                }

                return null;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Find element failed: {ex.Message}");
                return null;
            }
        }

        private AutomationElement? FindElementInWindow(AutomationElement window, string? automationId, string? name, ControlType? controlType, bool matchPartial)
        {
            try
            {
                var cf = new ConditionFactory(new UIA3PropertyLibrary());

                if (matchPartial && !string.IsNullOrWhiteSpace(name))
                {
                    return window.FindAllDescendants()
                        .FirstOrDefault(el =>
                            (controlType == null || el.Properties.ControlType.ValueOrDefault == controlType) &&
                            el.Properties.Name.ValueOrDefault != null &&
                            el.Properties.Name.ValueOrDefault.Contains(name, StringComparison.OrdinalIgnoreCase));
                }

                var cond = BuildCondition(cf, automationId, name, controlType, matchPartial);
                return cond == null ? window : window.FindFirstDescendant(cond);
            }
            catch
            {
                return null;
            }
        }

        private ConditionBase? BuildCondition(ConditionFactory cf,
            string? automationId,
            string? name,
            ControlType? controlType,
            bool matchPartial = false)
        {
            var conditions = new List<ConditionBase>();

            if (!string.IsNullOrWhiteSpace(automationId))
                conditions.Add(cf.ByAutomationId(automationId));

            if (!string.IsNullOrWhiteSpace(name) && !matchPartial)
                conditions.Add(cf.ByName(name));

            if (controlType.HasValue)
                conditions.Add(cf.ByControlType(controlType.Value));

            if (conditions.Count > 0)
                return conditions.Count == 1 ? conditions[0] : new AndCondition(conditions.ToArray());

            return null;
        }

        public void ClickButton(string? automationId = null,
            string? name = null,
            ControlType? controlType = null,
            bool matchPartial = false)
        {
            try
            {
                EnsureWindowVisible();

                var element = FindElement(automationId, name, controlType, matchPartial);
                if (element == null)
                {
                    Console.WriteLine($"[ERROR] Button not found: '{automationId}' / '{name}'");
                    throw new InvalidOperationException($"Button not found: AutomationId='{automationId}', Name='{name}'");
                }

                var btn = element.AsButton();
                if (btn != null)
                {
                    Console.WriteLine($"[LOG] Clicking button: AutomationId={btn.AutomationId}, Name={btn.Name}");
                    btn.Click();
                }
                else
                {
                    Console.WriteLine($"[LOG] Clicking element: AutomationId={element.AutomationId}, Name={element.Name}");
                    element.Click();
                }

                Wait.UntilInputIsProcessed();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Click button failed: {ex.Message}");
                throw;
            }
        }

        public void RightClick(string? automationId = null,
            string? name = null,
            ControlType? controlType = null,
            bool matchPartial = false)
        {
            try
            {
                EnsureWindowVisible();

                var el = FindElement(automationId, name, controlType, matchPartial);
                if (el == null)
                {
                    Console.WriteLine($"[ERROR] Element not found for right-click: '{automationId}'/'{name}'");
                    throw new InvalidOperationException($"Element not found for right-click: AutomationId='{automationId}', Name='{name}'");
                }

                Console.WriteLine($"[LOG] Right-clicking: AutoId={SafeGet(() => el.AutomationId)}, Name={SafeGet(() => el.Name)}");
                el.RightClick();
                Wait.UntilInputIsProcessed();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Right click failed: {ex.Message}");
                throw;
            }
        }

        public void EnterText(string? automationId = null, string? name = null, string text = "")
        {
            try
            {
                EnsureWindowVisible();

                var element = FindElement(automationId, name);
                if (element == null)
                {
                    Console.WriteLine($"[ERROR] TextBox not found: '{automationId}' / '{name}'");
                    throw new InvalidOperationException($"TextBox not found: AutomationId='{automationId}', Name='{name}'");
                }

                var tb = element.AsTextBox();
                if (tb != null)
                {
                    Console.WriteLine($"[LOG] Entering text '{text}' into textbox: {tb.AutomationId}");
                    tb.Text = text;
                }
                else
                {
                    Console.WriteLine($"[LOG] Entering text '{text}' into element: {element.AutomationId}");
                    element.Focus();
                    Keyboard.TypeSimultaneously(VirtualKeyShort.CONTROL, VirtualKeyShort.KEY_A);
                    Keyboard.Type(text);
                }

                Wait.UntilInputIsProcessed();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Enter text failed: {ex.Message}");
                throw;
            }
        }

        public void CaptureScreenshot(string filePath)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(filePath) ?? "Screenshots");

                var rect = _mainWindow.BoundingRectangle;
                using var bmp = new Bitmap((int)rect.Width, (int)rect.Height);
                using var g = Graphics.FromImage(bmp);

                g.CopyFromScreen((int)rect.X, (int)rect.Y, 0, 0, bmp.Size, CopyPixelOperation.SourceCopy);
                bmp.Save(filePath, ImageFormat.Png);

                Console.WriteLine($"[LOG] Screenshot saved to: {filePath}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Screenshot failed: {ex.Message}");
                throw;
            }
        }

        public GridResult ReadDataGridGeneric(string automationId = null, string name = null)
        {
            var result = new GridResult
            {
                GridName = name,
                GridAutomationId = automationId
            };

            try
            {
                EnsureWindowVisible();

                Console.WriteLine($"[LOG] Attempting to read grid: AutomationId='{automationId}', Name='{name}'");

                var grid = FindElement(automationId, name, ControlType.DataGrid);
                if (grid == null)
                {
                    Console.WriteLine("[ERROR] DataGrid not found; saving screenshot");
                    CaptureScreenshot("grid-not-found.png");
                    result.Success = false;
                    result.ErrorMessage = "DataGrid not found";
                    return result;
                }

                var gp = grid.Patterns.Grid.Pattern;
                var totalRows = gp.RowCount;
                var totalColumns = gp.ColumnCount;

                Console.WriteLine($"[LOG] Grid found: {totalRows} rows, {totalColumns} columns");

                result.TotalRows = totalRows;
                result.TotalColumns = totalColumns;

                DetectGridHeaders(gp, totalColumns, result);
                ReadAllGridRows(gp, totalRows, totalColumns, result);
                DisplayGridResults(result);

                result.Success = true;
                Console.WriteLine($"[LOG] Grid read successfully: {totalRows} rows x {totalColumns} columns");

            }
            catch (Exception ex)
            {
                result.Success = false;
                result.ErrorMessage = ex.Message;
                Console.WriteLine($"[ERROR] ReadDataGrid failed: {ex.Message}");
            }

            return result;
        }

        private void DetectGridHeaders(IGridPattern gp, int totalColumns, GridResult result)
        {
            try
            {
                for (int col = 0; col < totalColumns; col++)
                {
                    try
                    {
                        var headerCell = gp.GetItem(0, col);
                        var headerText = ExtractVisibleCellText(headerCell);

                        if (IsLikelyHeader(headerText) || result.Headers.Count == 0)
                        {
                            result.Headers.Add(string.IsNullOrWhiteSpace(headerText) ? $"Column {col + 1}" : headerText);
                        }
                        else
                        {
                            result.Headers.Add($"Column {col + 1}");
                        }
                    }
                    catch
                    {
                        result.Headers.Add($"Column {col + 1}");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Could not detect headers: {ex.Message}");
                for (int col = 0; col < totalColumns; col++)
                {
                    result.Headers.Add($"Column {col + 1}");
                }
            }
        }

        private bool IsLikelyHeader(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return false;

            var headerKeywords = new[] { "name", "status", "domain", "server", "url", "result", "message", "error", "id", "type", "value" };
            return headerKeywords.Any(keyword => text.Contains(keyword, StringComparison.OrdinalIgnoreCase)) ||
                   text.Length < 50;
        }

        private void ReadAllGridRows(IGridPattern gp, int totalRows, int totalColumns, GridResult result)
        {
            for (int row = 0; row < totalRows; row++)
            {
                var rowData = new List<string>();

                for (int col = 0; col < totalColumns; col++)
                {
                    try
                    {
                        var cell = gp.GetItem(row, col);
                        var cellText = ExtractCellTextByColumn(cell, col);

                        // Use empty string for truly empty cells
                        rowData.Add(string.IsNullOrWhiteSpace(cellText) ? "" : cellText);

                        Console.WriteLine($"[DEBUG] Row {row}, Col {col}: '{cellText}'");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[WARN] Could not read cell [{row},{col}]: {ex.Message}");
                        rowData.Add("[ERROR]");
                    }
                }

                result.Rows.Add(rowData);
            }
        }

        /// <summary>
        /// Extract cell text with column-aware logic
        /// </summary>
        private string ExtractCellTextByColumn(AutomationElement cell, int columnIndex)
        {
            if (cell == null) return "";

            try
            {
                Console.WriteLine($"[DEBUG] Extracting text from cell column {columnIndex}: {cell.ControlType}");

                // Column 0: Usually URLs/IPs - look for hyperlinks first
                if (columnIndex == 0)
                {
                    var cf = new ConditionFactory(new UIA3PropertyLibrary());
                    var hyperlink = cell.FindFirstDescendant(cf.ByControlType(ControlType.Hyperlink));
                    if (hyperlink != null)
                    {
                        var linkText = ExtractElementText(hyperlink);
                        if (!string.IsNullOrWhiteSpace(linkText) && !IsComplexObject(linkText))
                        {
                            Console.WriteLine($"[DEBUG] Column {columnIndex} extracted hyperlink: '{linkText}'");
                            return CleanCellText(linkText);
                        }
                    }
                }

                // Try standard extraction
                var extractedText = ExtractVisibleCellText(cell);
                Console.WriteLine($"[DEBUG] Column {columnIndex} extracted: '{extractedText}'");
                return extractedText;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DEBUG] ExtractCellTextByColumn error column {columnIndex}: {ex.Message}");
                return "[ERROR]";
            }
        }

        /// <summary>
        /// Enhanced cell text extraction
        /// </summary>
        private string ExtractVisibleCellText(AutomationElement cell)
        {
            if (cell == null) return "";

            try
            {
                Console.WriteLine($"[DEBUG] Extracting cell text, ControlType: {cell.ControlType}");

                // Method 1: Value pattern (most reliable for data)
                if (cell.Patterns.Value.IsSupported)
                {
                    var valuePattern = cell.Patterns.Value.Pattern;
                    var val = valuePattern.Value;
                    if (!string.IsNullOrWhiteSpace(val) && !IsComplexObject(val) && !IsTooltipLikeText(val))
                    {
                        Console.WriteLine($"[DEBUG] Value pattern: '{val}'");
                        return CleanCellText(val);
                    }
                }

                // Method 2: Text pattern
                if (cell.Patterns.Text.IsSupported)
                {
                    var textPattern = cell.Patterns.Text.Pattern;
                    var textContent = textPattern.DocumentRange.GetText(-1);
                    if (!string.IsNullOrWhiteSpace(textContent) && !IsComplexObject(textContent) && !IsTooltipLikeText(textContent))
                    {
                        Console.WriteLine($"[DEBUG] Text pattern: '{textContent}'");
                        return CleanCellText(textContent);
                    }
                }

                // Method 3: Direct cell Name (filtered)
                if (!string.IsNullOrWhiteSpace(cell.Name) &&
                    !IsComplexObject(cell.Name) &&
                    !IsTooltipLikeText(cell.Name))
                {
                    Console.WriteLine($"[DEBUG] Cell Name: '{cell.Name}'");
                    return CleanCellText(cell.Name);
                }

                // Method 4: Look for data child elements (excluding interactive elements)
                var cf = new ConditionFactory(new UIA3PropertyLibrary());

                // Hyperlinks (for URLs in Domain Name column)
                var hyperlink = cell.FindFirstDescendant(cf.ByControlType(ControlType.Hyperlink));
                if (hyperlink != null)
                {
                    var linkText = ExtractElementText(hyperlink);
                    if (!string.IsNullOrWhiteSpace(linkText))
                    {
                        Console.WriteLine($"[DEBUG] Hyperlink: '{linkText}'");
                        return CleanCellText(linkText);
                    }
                }

                // Text elements (actual data, not buttons)
                var textElements = cell.FindAllChildren(cf.ByControlType(ControlType.Text));
                foreach (var txt in textElements)
                {
                    var textContent = ExtractElementText(txt);
                    if (!string.IsNullOrWhiteSpace(textContent))
                    {
                        Console.WriteLine($"[DEBUG] Text child: '{textContent}'");
                        return CleanCellText(textContent);
                    }
                }

                // Edit controls
                var editElements = cell.FindAllChildren(cf.ByControlType(ControlType.Edit));
                foreach (var edit in editElements)
                {
                    var editText = ExtractElementText(edit);
                    if (!string.IsNullOrWhiteSpace(editText))
                    {
                        Console.WriteLine($"[DEBUG] Edit child: '{editText}'");
                        return CleanCellText(editText);
                    }
                }

                // ❌ DO NOT use GetVisibleTextRecursive - it picks up buttons/tooltips

                Console.WriteLine("[DEBUG] Cell appears empty (no data text found)");
                return "";

            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DEBUG] ExtractVisibleCellText error: {ex.Message}");
            }

            return "";
        }

        private string ExtractElementText(AutomationElement element)
        {
            try
            {
                // Skip interactive elements that aren't data
                var skipTypes = new[]
                {
            ControlType.Button,
            ControlType.ToolTip,
            ControlType.Image,
            ControlType.MenuItem,
            ControlType.SplitButton
        };

                if (skipTypes.Contains(element.ControlType))
                {
                    return "";
                }

                // Prefer Value pattern for actual cell data
                if (element.Patterns.Value.IsSupported)
                {
                    var val = element.Patterns.Value.Pattern.Value.Value;
                    if (!string.IsNullOrWhiteSpace(val) && !IsTooltipLikeText(val))
                        return val.Trim();
                }

                // Try Text pattern
                if (element.Patterns.Text.IsSupported)
                {
                    var textContent = element.Patterns.Text.Pattern.DocumentRange.GetText(-1);
                    if (!string.IsNullOrWhiteSpace(textContent) && !IsTooltipLikeText(textContent))
                        return textContent.Trim();
                }

                // Use Name only for data-holding control types
                var dataTypes = new[]
                {
            ControlType.Text,
            ControlType.Edit,
            ControlType.Hyperlink,
            ControlType.DataItem,
            ControlType.Document
        };

                if (dataTypes.Contains(element.ControlType))
                {
                    if (!string.IsNullOrWhiteSpace(element.Name) && !IsTooltipLikeText(element.Name))
                        return element.Name.Trim();
                }

            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DEBUG] Error extracting element text: {ex.Message}");
            }

            return "";
        }

        private bool IsTextElement(AutomationElement element)
        {
            var textTypes = new[]
            {
                ControlType.Text,
                ControlType.Edit,
                ControlType.Document,
                ControlType.Hyperlink,
                ControlType.Button,
                ControlType.DataItem
            };

            return textTypes.Contains(element.ControlType);
        }

        private bool IsComplexObject(string text)
        {
            return text.Contains("{ ") ||
                   text.Contains("Item: {") ||
                   text.StartsWith("System.") ||
                   text.Contains("domains =");
        }

        private string GetVisibleTextRecursive(AutomationElement element, int maxDepth = 3)
        {
            if (element == null || maxDepth <= 0) return "";

            var texts = new List<string>();

            try
            {
                var elementText = ExtractElementText(element);
                if (!string.IsNullOrWhiteSpace(elementText) && !IsComplexObject(elementText))
                {
                    texts.Add(elementText);
                }

                if (maxDepth > 1)
                {
                    foreach (var child in element.FindAllChildren())
                    {
                        var childText = GetVisibleTextRecursive(child, maxDepth - 1);
                        if (!string.IsNullOrWhiteSpace(childText))
                        {
                            texts.Add(childText);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DEBUG] Error in recursive text extraction: {ex.Message}");
            }

            return string.Join(" ", texts.Where(t => !string.IsNullOrWhiteSpace(t)).Distinct());
        }

        private string CleanCellText(string text)
        {
            if (string.IsNullOrWhiteSpace(text))
                return "";

            text = text.Trim()
                       .Replace("\r\n", " ")
                       .Replace("\n", " ")
                       .Replace("\r", " ")
                       .Replace("\t", " ");

            while (text.Contains("  "))
            {
                text = text.Replace("  ", " ");
            }

            if (text.Length > 100)
            {
                text = text.Substring(0, 97) + "...";
            }

            return text.Trim();
        }

        private void DisplayGridResults(GridResult result)
        {
            if (!result.Success || result.IsEmpty)
            {
                Console.WriteLine("[LOG] No grid data to display");
                return;
            }

            try
            {
                if (result.Headers.Any())
                {
                    var headerLine = string.Join(" | ", result.Headers);
                    Console.WriteLine(headerLine);
                    Console.WriteLine(new string('-', headerLine.Length));
                }

                for (int i = 0; i < result.Rows.Count; i++)
                {
                    var row = result.Rows[i];
                    var rowLine = string.Join(" | ", row);

                    if (result.FailedRows.Contains(i))
                        rowLine += " ❌";

                    Console.WriteLine(rowLine);
                }

                Console.WriteLine($"\n[LOG] Total: {result.TotalRows} rows, {result.TotalColumns} columns");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Error displaying grid: {ex.Message}");
            }
        }

        public void LogAllElements(string filePath)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(filePath) ?? "Logs");
                using var writer = new StreamWriter(filePath);
                DumpElementsRecursive(_mainWindow, writer, 0);
                Console.WriteLine($"[LOG] Elements logged to: {filePath}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Log elements failed: {ex.Message}");
                throw;
            }
        }

        private void DumpElementsRecursive(AutomationElement? el, StreamWriter w, int indent)
        {
            if (el == null) return;

            try
            {
                var pad = new string(' ', indent * 2);
                var name = SafeGet(() => el.Name);
                var autoId = SafeGet(() => el.AutomationId);
                var ctl = SafeGet(() => el.ControlType.ToString());

                w.WriteLine($"{pad}Name={name}, AutomationId={autoId}, ControlType={ctl}");

                foreach (var child in el.FindAllChildren())
                {
                    DumpElementsRecursive(child, w, indent + 1);
                }
            }
            catch (Exception ex)
            {
                w.WriteLine($"{new string(' ', indent * 2)}[ERROR: {ex.Message}]");
            }
        }

        private string SafeGet(Func<string?> getter)
        {
            try
            {
                return getter() ?? "";
            }
            catch
            {
                return "[N/A]";
            }
        }

        private bool IsTooltipLikeText(string text)
        {
            if (string.IsNullOrWhiteSpace(text))
                return false;

            // Action words that indicate interactive elements, not data
            var actionKeywords = new[]
            {
        "install", "download", "click", "view", "open", "see", "show",
        "learn", "read", "get", "certificate", "help", "more", "info",
        "details", "edit", "delete", "remove", "add", "update"
            };

            var lowerText = text.ToLower().Trim();

            // Short action phrases (likely buttons/links, not data)
            if (text.Length < 30 && actionKeywords.Any(k => lowerText.Contains(k)))
            {
                Console.WriteLine($"[DEBUG] Filtered tooltip/button text: '{text}'");
                return true;
            }

            return false;
        }

        public void ToggleCheckbox(string? automationId = null, string? name = null, bool? checkState = null)
        {
            EnsureWindowVisible();

            var element = FindElement(automationId, name);
            if (element == null) throw new InvalidOperationException($"Checkbox not found: {automationId}/{name}");

            var cb = element.AsCheckBox();
            if (cb != null && checkState.HasValue && cb.IsChecked != checkState.Value)
            {
                Console.WriteLine($"[LOG] Toggling checkbox '{cb.Name}' to {checkState}");
                cb.Click();
                Wait.UntilInputIsProcessed();
            }
        }

        public void SelectComboItem(string? automationId = null, string? name = null, string itemText = "")
        {
            EnsureWindowVisible();

            var element = FindElement(automationId, name);
            if (element == null) throw new InvalidOperationException($"ComboBox not found: {automationId}/{name}");

            var combo = element.AsComboBox();
            if (combo != null)
            {
                Console.WriteLine($"[LOG] Selecting '{itemText}' in ComboBox: {combo.Name}");
                combo.Select(itemText);
                Wait.UntilInputIsProcessed();
            }
        }

        public void DoubleClick(string? automationId = null, string? name = null)
        {
            EnsureWindowVisible();
            var element = FindElement(automationId, name);
            if (element == null) throw new InvalidOperationException($"Element not found for double-click: {automationId}/{name}");

            Console.WriteLine($"[LOG] Double-clicking: {SafeGet(() => element.Name)}");
            element.DoubleClick();
            Wait.UntilInputIsProcessed();
        }

        public string ReadText(string? automationId = null, string? name = null)
        {
            EnsureWindowVisible();

            var element = FindElement(automationId, name);
            if (element == null) return "";

            string text = element.Name ?? element.AsLabel()?.Text ?? element.AsTextBox()?.Text ?? "";
            Console.WriteLine($"[LOG] Read Text: '{text}'");
            return text;
        }

        public void CheckServiceStatus(string serviceName)
        {
            try
            {
                var sc = new System.ServiceProcess.ServiceController(serviceName);
                Console.WriteLine($"[LOG] Service '{serviceName}' status: {sc.Status}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Could not check service '{serviceName}': {ex.Message}");
            }
        }

        public void CheckAppInstalled(string appName)
        {
            try
            {
                string uninstallKey = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall";
                using var baseKey = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(uninstallKey);

                bool found = baseKey?.GetSubKeyNames()
                    .Select(name => baseKey.OpenSubKey(name))
                    .Where(subKey => subKey != null)
                    .Any(subKey => (subKey.GetValue("DisplayName") as string)?.Contains(appName, StringComparison.OrdinalIgnoreCase) == true) ?? false;

                Console.WriteLine(found ? $"[LOG] Application '{appName}' is installed." : $"[LOG] Application '{appName}' NOT found.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Could not check application '{appName}': {ex.Message}");
            }
        }

        public void DisableAutoFocus()
        {
            AutoFocusEnabled = false;
            Console.WriteLine("[LOG] Auto-focus disabled");
        }

        public void EnableAutoFocus()
        {
            AutoFocusEnabled = true;
            Console.WriteLine("[LOG] Auto-focus enabled");
        }

        public void Dispose()
        {
            try
            {
                _automation?.Dispose();
                _app?.Dispose();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Dispose error: {ex.Message}");
            }
        }
    }
}

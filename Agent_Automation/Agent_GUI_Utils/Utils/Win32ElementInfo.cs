using System.Runtime.InteropServices;
using System.Text;

namespace GuiAgentUtils.Utils
{
    /// <summary>
    /// Win32 API fallback for capturing UI element info when UIA3 returns E_ACCESSDENIED.
    /// Useful for inspecting and interacting with elevated/protected windows like Win32 MessageBox dialogs.
    /// </summary>
    public class Win32ElementInfo
    {
        public IntPtr Handle { get; set; }
        public string Text { get; set; } = "";
        public string ClassName { get; set; } = "";
        public Win32Rect Bounds { get; set; }
        public bool IsEnabled { get; set; }
        public bool IsVisible { get; set; }
        public int ControlId { get; set; }
        public IntPtr ParentHandle { get; set; }
        public string ParentText { get; set; } = "";
        public string ParentClassName { get; set; } = "";
        public List<Win32ElementInfo> Children { get; set; } = new();

        public string InferredControlType =>
            ClassName switch
            {
                "#32770" => "Dialog",
                "Button" => "Button",
                "Static" => "Text/Static",
                "Edit" => "Edit",
                "ComboBox" => "ComboBox",
                "ListBox" => "ListBox",
                "SysListView32" => "ListView",
                "SysTreeView32" => "TreeView",
                "msctls_progress32" => "ProgressBar",
                "SysTabControl32" => "Tab",
                "ScrollBar" => "ScrollBar",
                _ => ClassName
            };

        #region Win32 API Declarations

        [DllImport("user32.dll")]
        private static extern IntPtr WindowFromPoint(POINT point);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetWindowTextLength(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

        [DllImport("user32.dll")]
        private static extern bool GetWindowRect(IntPtr hWnd, out Win32Rect lpRect);

        [DllImport("user32.dll")]
        private static extern bool IsWindowEnabled(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern bool IsWindowVisible(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern IntPtr GetParent(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern int GetDlgCtrlID(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern IntPtr GetAncestor(IntPtr hWnd, uint gaFlags);

        [DllImport("user32.dll")]
        private static extern bool EnumChildWindows(IntPtr hWndParent, EnumChildProc lpEnumFunc, IntPtr lParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, StringBuilder lParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, string lParam);

        [DllImport("user32.dll")]
        private static extern bool PostMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr FindWindowEx(IntPtr hWndParent, IntPtr hWndChildAfter, string? lpszClass, string? lpszWindow);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr FindWindow(string? lpClassName, string? lpWindowName);

        private delegate bool EnumChildProc(IntPtr hWnd, IntPtr lParam);

        // Constants
        private const uint GA_ROOT = 2;
        private const uint WM_GETTEXT = 0x000D;
        private const uint WM_GETTEXTLENGTH = 0x000E;
        private const uint WM_SETTEXT = 0x000C;
        private const uint WM_CLOSE = 0x0010;
        private const uint BM_CLICK = 0x00F5;
        private const uint BM_GETCHECK = 0x00F0;
        private const uint BM_SETCHECK = 0x00F1;
        private const uint CB_SELECTSTRING = 0x014D;
        private const uint CB_GETCURSEL = 0x0147;
        private const uint CB_GETLBTEXT = 0x0148;
        private const uint CB_GETLBTEXTLEN = 0x0149;
        private const uint WM_LBUTTONDOWN = 0x0201;
        private const uint WM_LBUTTONUP = 0x0202;
        private const uint WM_LBUTTONDBLCLK = 0x0203;
        private const uint BST_CHECKED = 0x0001;
        private const uint BST_UNCHECKED = 0x0000;

        [StructLayout(LayoutKind.Sequential)]
        public struct POINT
        {
            public int X;
            public int Y;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct Win32Rect
        {
            public int Left, Top, Right, Bottom;

            public int Width => Right - Left;
            public int Height => Bottom - Top;

            public override string ToString() => $"{{L={Left}, T={Top}, R={Right}, B={Bottom}}} ({Width}x{Height})";
        }

        #endregion

        #region Element Finding

        /// <summary>
        /// Find a top-level window by title (exact or partial match).
        /// </summary>
        public static Win32ElementInfo? FindWindowByTitle(string title, bool partialMatch = false)
        {
            if (string.IsNullOrEmpty(title))
                return null;

            if (!partialMatch)
            {
                var hWnd = FindWindow(null, title);
                return hWnd != IntPtr.Zero ? FromHandle(hWnd) : null;
            }

            // Partial match: enumerate all top-level windows
            Win32ElementInfo? result = null;
            EnumChildWindows(IntPtr.Zero, (hWnd, _) =>
            {
                var text = GetWindowTextSafe(hWnd);
                if (!string.IsNullOrEmpty(text) && text.Contains(title, StringComparison.OrdinalIgnoreCase))
                {
                    result = FromHandle(hWnd);
                    EnumerateChildren(hWnd, result.Children);
                    return false; // Stop enumeration
                }
                return true;
            }, IntPtr.Zero);

            return result;
        }

        /// <summary>
        /// Find a child control by text and/or class name within a parent window.
        /// </summary>
        public static Win32ElementInfo? FindChild(IntPtr parentHwnd, string? text = null, string? className = null, bool partialMatch = false)
        {
            if (parentHwnd == IntPtr.Zero)
                return null;

            Win32ElementInfo? result = null;

            EnumChildWindows(parentHwnd, (hWnd, _) =>
            {
                bool matches = true;

                if (!string.IsNullOrEmpty(className))
                {
                    var cls = GetClassNameSafe(hWnd);
                    if (!cls.Equals(className, StringComparison.OrdinalIgnoreCase))
                        matches = false;
                }

                if (matches && !string.IsNullOrEmpty(text))
                {
                    var winText = GetTextSafe(hWnd);
                    if (partialMatch)
                        matches = winText.Contains(text, StringComparison.OrdinalIgnoreCase);
                    else
                        matches = winText.Equals(text, StringComparison.OrdinalIgnoreCase);
                }

                if (matches && (!string.IsNullOrEmpty(text) || !string.IsNullOrEmpty(className)))
                {
                    result = FromHandle(hWnd);
                    return false; // Stop
                }

                return true;
            }, IntPtr.Zero);

            return result;
        }

        /// <summary>
        /// Find all children matching criteria within a parent window.
        /// </summary>
        public static List<Win32ElementInfo> FindAllChildren(IntPtr parentHwnd, string? className = null, string? text = null, bool partialMatch = false)
        {
            var results = new List<Win32ElementInfo>();
            if (parentHwnd == IntPtr.Zero)
                return results;

            EnumChildWindows(parentHwnd, (hWnd, _) =>
            {
                bool matches = true;

                if (!string.IsNullOrEmpty(className))
                {
                    var cls = GetClassNameSafe(hWnd);
                    if (!cls.Equals(className, StringComparison.OrdinalIgnoreCase))
                        matches = false;
                }

                if (matches && !string.IsNullOrEmpty(text))
                {
                    var winText = GetTextSafe(hWnd);
                    if (partialMatch)
                        matches = winText.Contains(text, StringComparison.OrdinalIgnoreCase);
                    else
                        matches = winText.Equals(text, StringComparison.OrdinalIgnoreCase);
                }

                if (matches)
                    results.Add(FromHandle(hWnd));

                return true;
            }, IntPtr.Zero);

            return results;
        }

        #endregion

        #region Element Interactions

        /// <summary>
        /// Click a button using BM_CLICK message. Works across privilege boundaries.
        /// </summary>
        public static bool ClickButton(IntPtr hWnd)
        {
            if (hWnd == IntPtr.Zero) return false;

            // Activate the parent window first
            var root = GetAncestor(hWnd, GA_ROOT);
            if (root != IntPtr.Zero)
                SetForegroundWindow(root);

            SendMessage(hWnd, BM_CLICK, IntPtr.Zero, IntPtr.Zero);
            return true;
        }

        /// <summary>
        /// Click by sending WM_LBUTTONDOWN + WM_LBUTTONUP. Works for non-button controls.
        /// </summary>
        public static bool ClickControl(IntPtr hWnd)
        {
            if (hWnd == IntPtr.Zero) return false;

            var root = GetAncestor(hWnd, GA_ROOT);
            if (root != IntPtr.Zero)
                SetForegroundWindow(root);

            // Click at center of control
            GetWindowRect(hWnd, out var rect);
            int centerX = (rect.Right - rect.Left) / 2;
            int centerY = (rect.Bottom - rect.Top) / 2;
            IntPtr lParam = (IntPtr)((centerY << 16) | (centerX & 0xFFFF));

            PostMessage(hWnd, WM_LBUTTONDOWN, (IntPtr)0x0001, lParam);
            Thread.Sleep(50);
            PostMessage(hWnd, WM_LBUTTONUP, IntPtr.Zero, lParam);

            return true;
        }

        /// <summary>
        /// Double-click a control using WM_LBUTTONDBLCLK.
        /// </summary>
        public static bool DoubleClickControl(IntPtr hWnd)
        {
            if (hWnd == IntPtr.Zero) return false;

            var root = GetAncestor(hWnd, GA_ROOT);
            if (root != IntPtr.Zero)
                SetForegroundWindow(root);

            GetWindowRect(hWnd, out var rect);
            int centerX = (rect.Right - rect.Left) / 2;
            int centerY = (rect.Bottom - rect.Top) / 2;
            IntPtr lParam = (IntPtr)((centerY << 16) | (centerX & 0xFFFF));

            PostMessage(hWnd, WM_LBUTTONDBLCLK, (IntPtr)0x0001, lParam);
            Thread.Sleep(50);
            PostMessage(hWnd, WM_LBUTTONUP, IntPtr.Zero, lParam);

            return true;
        }

        /// <summary>
        /// Read text from a control using WM_GETTEXT. Works for Static, Edit, Button, etc.
        /// </summary>
        public static string ReadText(IntPtr hWnd)
        {
            return GetTextSafe(hWnd);
        }

        /// <summary>
        /// Set text on a control using WM_SETTEXT. Works for Edit controls.
        /// </summary>
        public static bool SetText(IntPtr hWnd, string text)
        {
            if (hWnd == IntPtr.Zero) return false;
            SendMessage(hWnd, WM_SETTEXT, IntPtr.Zero, text);
            return true;
        }

        /// <summary>
        /// Get checkbox state using BM_GETCHECK.
        /// </summary>
        public static bool IsChecked(IntPtr hWnd)
        {
            var result = SendMessage(hWnd, BM_GETCHECK, IntPtr.Zero, IntPtr.Zero);
            return (uint)(long)result == BST_CHECKED;
        }

        /// <summary>
        /// Set checkbox state using BM_SETCHECK + BM_CLICK if needed.
        /// </summary>
        public static bool SetCheckState(IntPtr hWnd, bool check)
        {
            if (hWnd == IntPtr.Zero) return false;

            bool currentlyChecked = IsChecked(hWnd);
            if (currentlyChecked != check)
            {
                ClickButton(hWnd); // Toggle via click
            }
            return true;
        }

        /// <summary>
        /// Select an item in a ComboBox by text using CB_SELECTSTRING.
        /// </summary>
        public static bool SelectComboItem(IntPtr hWnd, string itemText)
        {
            if (hWnd == IntPtr.Zero || string.IsNullOrEmpty(itemText)) return false;
            var result = SendMessage(hWnd, CB_SELECTSTRING, (IntPtr)(-1), itemText);
            return (long)result != -1; // CB_ERR = -1
        }

        /// <summary>
        /// Close a window using WM_CLOSE.
        /// </summary>
        public static bool CloseWindow(IntPtr hWnd)
        {
            if (hWnd == IntPtr.Zero) return false;
            PostMessage(hWnd, WM_CLOSE, IntPtr.Zero, IntPtr.Zero);
            return true;
        }

        /// <summary>
        /// Right-click a control using WM_RBUTTONDOWN + WM_RBUTTONUP.
        /// </summary>
        public static bool RightClickControl(IntPtr hWnd)
        {
            if (hWnd == IntPtr.Zero) return false;

            const uint WM_RBUTTONDOWN = 0x0204;
            const uint WM_RBUTTONUP = 0x0205;

            var root = GetAncestor(hWnd, GA_ROOT);
            if (root != IntPtr.Zero)
                SetForegroundWindow(root);

            GetWindowRect(hWnd, out var rect);
            int centerX = (rect.Right - rect.Left) / 2;
            int centerY = (rect.Bottom - rect.Top) / 2;
            IntPtr lParam = (IntPtr)((centerY << 16) | (centerX & 0xFFFF));

            PostMessage(hWnd, WM_RBUTTONDOWN, (IntPtr)0x0002, lParam);
            Thread.Sleep(50);
            PostMessage(hWnd, WM_RBUTTONUP, IntPtr.Zero, lParam);

            return true;
        }

        #endregion

        /// <summary>
        /// Get element info at a screen point using Win32 APIs.
        /// Works even when UIA3 fails with E_ACCESSDENIED.
        /// </summary>
        public static Win32ElementInfo? FromPoint(int x, int y)
        {
            var pt = new POINT { X = x, Y = y };
            var hWnd = WindowFromPoint(pt);

            if (hWnd == IntPtr.Zero)
                return null;

            var info = FromHandle(hWnd);

            // Also get the top-level dialog and enumerate all children
            var root = GetAncestor(hWnd, GA_ROOT);
            if (root != IntPtr.Zero && root != hWnd)
            {
                var rootInfo = FromHandle(root);
                info.ParentHandle = root;
                info.ParentText = rootInfo.Text;
                info.ParentClassName = rootInfo.ClassName;
            }

            return info;
        }

        /// <summary>
        /// Build element info from a window handle.
        /// </summary>
        public static Win32ElementInfo FromHandle(IntPtr hWnd)
        {
            var info = new Win32ElementInfo
            {
                Handle = hWnd,
                Text = GetTextSafe(hWnd),
                ClassName = GetClassNameSafe(hWnd),
                IsEnabled = IsWindowEnabled(hWnd),
                IsVisible = IsWindowVisible(hWnd),
                ControlId = GetDlgCtrlID(hWnd),
                ParentHandle = GetParent(hWnd)
            };

            GetWindowRect(hWnd, out var rect);
            info.Bounds = rect;

            if (info.ParentHandle != IntPtr.Zero)
            {
                info.ParentText = GetTextSafe(info.ParentHandle);
                info.ParentClassName = GetClassNameSafe(info.ParentHandle);
            }

            return info;
        }

        /// <summary>
        /// Enumerate all children of the top-level window that owns the element at the given point.
        /// Useful for inspecting all controls in a MessageBox dialog.
        /// </summary>
        public static Win32ElementInfo? GetDialogTree(int x, int y)
        {
            var pt = new POINT { X = x, Y = y };
            var hWnd = WindowFromPoint(pt);

            if (hWnd == IntPtr.Zero)
                return null;

            // Find the top-level window (the dialog)
            var root = GetAncestor(hWnd, GA_ROOT);
            if (root == IntPtr.Zero)
                root = hWnd;

            var rootInfo = FromHandle(root);
            EnumerateChildren(root, rootInfo.Children);

            return rootInfo;
        }

        /// <summary>
        /// Recursively enumerate child windows.
        /// </summary>
        private static void EnumerateChildren(IntPtr parentHwnd, List<Win32ElementInfo> children)
        {
            var childHandles = new List<IntPtr>();

            EnumChildWindows(parentHwnd, (hWnd, _) =>
            {
                // Only add direct children (whose parent is parentHwnd)
                if (GetParent(hWnd) == parentHwnd)
                    childHandles.Add(hWnd);
                return true;
            }, IntPtr.Zero);

            foreach (var childHwnd in childHandles)
            {
                var childInfo = FromHandle(childHwnd);
                EnumerateChildren(childHwnd, childInfo.Children);
                children.Add(childInfo);
            }
        }

        private static string GetWindowTextSafe(IntPtr hWnd)
        {
            try
            {
                int len = GetWindowTextLength(hWnd);
                if (len <= 0) return "";
                var sb = new StringBuilder(len + 1);
                GetWindowText(hWnd, sb, sb.Capacity);
                return sb.ToString();
            }
            catch
            {
                return "";
            }
        }

        /// <summary>
        /// Read text using WM_GETTEXT — works for Static controls where GetWindowText may fail.
        /// </summary>
        private static string GetTextSafe(IntPtr hWnd)
        {
            try
            {
                // Try WM_GETTEXT first (works for all control types including Static)
                int len = (int)SendMessage(hWnd, WM_GETTEXTLENGTH, IntPtr.Zero, IntPtr.Zero);
                if (len > 0)
                {
                    var sb = new StringBuilder(len + 1);
                    SendMessage(hWnd, WM_GETTEXT, (IntPtr)sb.Capacity, sb);
                    var text = sb.ToString();
                    if (!string.IsNullOrEmpty(text))
                        return text;
                }

                // Fallback to GetWindowText
                return GetWindowTextSafe(hWnd);
            }
            catch
            {
                return GetWindowTextSafe(hWnd);
            }
        }

        private static string GetClassNameSafe(IntPtr hWnd)
        {
            try
            {
                var sb = new StringBuilder(256);
                GetClassName(hWnd, sb, sb.Capacity);
                return sb.ToString();
            }
            catch
            {
                return "";
            }
        }
    }
}

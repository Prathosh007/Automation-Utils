// Program.cs
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Input;
using GuiAgentUtils.Core;
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Text.Json;


namespace GuiAgentUtils
{
    class Program
    {
        static async Task<int> Main(string[] args)
        {
            try
            {
                if (args.Length == 0)
                {
                    ShowUsage();
                    return 1;
                }

                // Handle different modes
                return args[0].ToLower() switch
                {
                    "spy" => RunSpyMode(args.Length > 1 ? args[1] : null),
                    "explore" => RunExploreMode(args.Length > 1 ? args[1] : null),
                    "validate" => await ValidateCommands(args.Length > 1 ? args[1] : null),
                    _ => await RunTestSuite(args)
                };
            }
            catch (Exception ex)
            {
                Console.WriteLine($"CRITICAL_ERROR|{ex.Message}");
                return 99;
            }
        }

        private static async Task<int> RunTestSuite(string[] args)
        {
            string? appPath = null;
            string? testFile = null;

            if (args.Length == 1)
            {
                testFile = args[0];
            }
            else if (args.Length == 2)
            {
                appPath = args[0];
                testFile = args[1];
            }
            else
            {
                ShowUsage();
                return 1;
            }

            if (!File.Exists(testFile))
            {
                Console.WriteLine($"FILE_NOT_FOUND|{testFile}");
                return 5;
            }

            var engine = new TestEngine();
            TestResult result;

            if (string.IsNullOrEmpty(appPath))
            {
                result = engine.ExecuteTestSuite(testFile);
            }
            else
            {
                var commands = await LoadCommands(testFile);
                result = engine.ExecuteCommands(commands, appPath);
            }

            await GenerateReport(result);

            return result.Success ? 0 : 1;
        }

        private static async Task<List<Command>> LoadCommands(string filePath)
        {
            try
            {
                var json = await File.ReadAllTextAsync(filePath);
                var options = new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true,
                    AllowTrailingCommas = true,
                    ReadCommentHandling = JsonCommentHandling.Skip
                };
                options.Converters.Add(new ControlTypeJsonConverter());

                var commands = JsonSerializer.Deserialize<List<Command>>(json, options);
                return commands ?? new List<Command>();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"LOAD_ERROR|Failed to load commands: {ex.Message}");
                return new List<Command>();
            }
        }
        private static async Task GenerateReport(TestResult result)
        {
            try
            {
                Directory.CreateDirectory("Reports");
                var reportPath = $"Reports/test-report-{DateTime.Now:yyyyMMdd-HHmmss}.json";

                var report = new
                {
                    TestId = result.TestId,
                    TestName = result.TestName,
                    Timestamp = result.StartTime,
                    Duration = result.Duration.ToString(),
                    Success = result.Success,
                    Message = result.Message,
                    Summary = new
                    {
                        Total = result.TotalCommands,
                        Successful = result.SuccessfulCommands,
                        Failed = result.FailedCommands,
                        SuccessRate = result.SuccessRate
                    },
                    CommandResults = result.CommandResults
                };

                var json = JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true });
                await File.WriteAllTextAsync(reportPath, json);
            }
            catch
            {
                // Silent fail for report generation
            }
        }

        private static int RunSpyMode(string appPath)
        {
            var automator = string.IsNullOrWhiteSpace(appPath)
                ? new GuiAutomatorBase()
                : new GuiAutomatorBase(appPath);

            Console.WriteLine("═══════════════════════════════════════════════════════════");
            Console.WriteLine("                  SPY MODE - Element Inspector");
            Console.WriteLine("═══════════════════════════════════════════════════════════");
            Console.WriteLine("Instructions:");
            Console.WriteLine("  • Hover over any element");
            Console.WriteLine("  • Press ENTER to capture");
            Console.WriteLine("  • Type 'exit' and press ENTER to quit");
            Console.WriteLine("═══════════════════════════════════════════════════════════\n");

            using var writer = new StreamWriter("spy-log.txt", append: true);
            writer.WriteLine($"\n{'═',80}");
            writer.WriteLine($"Spy Session: {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
            writer.WriteLine(new string('═', 80));

            int count = 0;

            while (true)
            {
                Console.Write("\n> Hover and press ENTER (or 'exit'): ");
                var input = Console.ReadLine();

                if (input?.Equals("exit", StringComparison.OrdinalIgnoreCase) == true)
                    break;

                Console.WriteLine("  Capturing in 10 seconds...");
                Thread.Sleep(10000);

                try
                {
                    count++;
                    var pos = Mouse.Position;
                    var el = automator.ElementFromPoint(pos);

                    if (el != null)
                    {
                        Console.WriteLine($"\n{'─',70}");
                        Console.WriteLine($"  ELEMENT #{count} at ({pos.X}, {pos.Y})");
                        Console.WriteLine(new string('─', 70));

                        PrintElementInfo(el);
                        WriteToFile(writer, el, pos, count);

                        Console.WriteLine(new string('─', 70));
                        Console.WriteLine("  ✅ Captured (UIA3)");
                    }
                    else
                    {
                        // Win32 API fallback for elevated/protected windows (e.g. MessageBox)
                        Console.WriteLine("  ⚠ UIA3 failed, falling back to Win32 API...");
                        var win32Info = automator.Win32ElementFromPoint(pos);

                        if (win32Info == null)
                        {
                            Console.WriteLine("  ❌ No element found (both UIA3 and Win32 failed)");
                            continue;
                        }

                        Console.WriteLine($"\n{'─',70}");
                        Console.WriteLine($"  ELEMENT #{count} at ({pos.X}, {pos.Y}) [Win32 Fallback]");
                        Console.WriteLine(new string('─', 70));

                        PrintWin32ElementInfo(win32Info);
                        WriteWin32ToFile(writer, win32Info, pos, count);

                        Console.WriteLine(new string('─', 70));
                        Console.WriteLine("  ✅ Captured (Win32 API)");
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"  ❌ Error: {ex.Message}");
                }
            }

            writer.WriteLine($"\n{'═',80}");
            writer.WriteLine($"Session ended. Total captures: {count}");
            writer.WriteLine(new string('═', 80));

            Console.WriteLine($"\n✅ {count} elements captured. Log saved to: spy-log.txt");
            return 0;
        }

        /// <summary>
        /// Print element info to console
        /// </summary>
        private static void PrintElementInfo(AutomationElement el)
        {
            // Basic Info
            Console.WriteLine($"  Name:          {GetSafe(() => el.Name)}");
            Console.WriteLine($"  AutomationId:  {GetSafe(() => el.AutomationId)}");
            Console.WriteLine($"  ControlType:   {GetSafe(() => el.ControlType.ToString())}");
            Console.WriteLine($"  ClassName:     {GetSafe(() => el.ClassName)}");

            // State
            Console.WriteLine($"  IsEnabled:     {GetSafe(() => el.IsEnabled.ToString())}");
            Console.WriteLine($"  IsOffscreen:   {GetSafe(() => el.IsOffscreen.ToString())}");

            // Value (if available)
            var value = GetValue(el);
            if (!string.IsNullOrEmpty(value))
                Console.WriteLine($"  Value:         {value}");

            // Bounds
            var bounds = GetSafe(() => el.BoundingRectangle.ToString());
            if (bounds != "[N/A]")
                Console.WriteLine($"  Bounds:        {bounds}");

            // Help Text
            var helpText = GetSafe(() => el.HelpText);
            if (!string.IsNullOrEmpty(helpText) && helpText != "[N/A]")
                Console.WriteLine($"  HelpText:      {helpText}");

            // Parent
            var parent = GetSafe(() => el.Parent?.Name);
            if (!string.IsNullOrEmpty(parent) && parent != "[N/A]")
                Console.WriteLine($"  Parent:        {parent}");

            // Patterns
            var patterns = GetPatterns(el);
            if (patterns.Any())
                Console.WriteLine($"  Patterns:      {string.Join(", ", patterns)}");
        }
        /// <summary>
        /// Write detailed info to file
        /// </summary>
        private static void WriteToFile(StreamWriter w, AutomationElement el, Point pos, int count)
        {
            w.WriteLine($"\n{new string('-', 80)}");
            w.WriteLine($"Element #{count} - {DateTime.Now:HH:mm:ss} - Position: ({pos.X}, {pos.Y})");
            w.WriteLine(new string('-', 80));

            w.WriteLine($"Name:            {GetSafe(() => el.Name)}");
            w.WriteLine($"AutomationId:    {GetSafe(() => el.AutomationId)}");
            w.WriteLine($"ControlType:     {GetSafe(() => el.ControlType.ToString())}");
            w.WriteLine($"ClassName:       {GetSafe(() => el.ClassName)}");
            w.WriteLine($"LocalizedControlType: {GetSafe(() => el.Properties.LocalizedControlType.ValueOrDefault)}");
            w.WriteLine();

            w.WriteLine($"IsEnabled:       {GetSafe(() => el.IsEnabled.ToString())}");
            w.WriteLine($"IsOffscreen:     {GetSafe(() => el.IsOffscreen.ToString())}");
            w.WriteLine($"IsKeyboardFocusable: {GetSafe(() => el.Properties.IsKeyboardFocusable.ToString())}");
            w.WriteLine();

            w.WriteLine($"BoundingRectangle: {GetSafe(() => el.BoundingRectangle.ToString())}");
            w.WriteLine($"HelpText:        {GetSafe(() => el.HelpText)}");
            w.WriteLine($"AcceleratorKey:  {GetSafe(() => el.Properties.AcceleratorKey)}");
            w.WriteLine($"AccessKey:       {GetSafe(() => el.Properties.AccessKey)}");
            w.WriteLine();

            var value = GetValue(el);
            if (!string.IsNullOrEmpty(value))
                w.WriteLine($"Value:           {value}");

            try
            {
                var parent = el.Parent;
                if (parent != null)
                {
                    w.WriteLine($"Parent Name:     {GetSafe(() => parent.Name)}");
                    w.WriteLine($"Parent Type:     {GetSafe(() => parent.ControlType.ToString())}");
                }
            }
            catch { }

            var patterns = GetPatterns(el);
            if (patterns.Any())
                w.WriteLine($"Patterns:        {string.Join(", ", patterns)}");

            w.Flush();
        }

        /// <summary>
        /// Print Win32 element info to console (fallback for elevated/protected windows)
        /// </summary>
        private static void PrintWin32ElementInfo(Win32ElementInfo info)
        {
            Console.WriteLine($"  Text:          {(string.IsNullOrEmpty(info.Text) ? "[N/A]" : info.Text)}");
            Console.WriteLine($"  ClassName:     {info.ClassName}");
            Console.WriteLine($"  ControlType:   {info.InferredControlType}");
            Console.WriteLine($"  Handle:        0x{info.Handle:X}");
            Console.WriteLine($"  IsEnabled:     {info.IsEnabled}");
            Console.WriteLine($"  IsVisible:     {info.IsVisible}");
            Console.WriteLine($"  Bounds:        {info.Bounds}");
            Console.WriteLine($"  ControlId:     {info.ControlId}");

            if (!string.IsNullOrEmpty(info.ParentText))
                Console.WriteLine($"  Parent:        {info.ParentText} ({info.ParentClassName})");

            // Print child controls (e.g. MessageBox buttons, static text)
            if (info.Children.Count > 0)
            {
                Console.WriteLine($"\n  --- Child Controls ({info.Children.Count}) ---");
                PrintWin32Children(info.Children, indent: 2);
            }
        }

        private static void PrintWin32Children(List<Win32ElementInfo> children, int indent)
        {
            var prefix = new string(' ', indent * 2);
            foreach (var child in children)
            {
                var text = string.IsNullOrEmpty(child.Text) ? "" : $" \"{child.Text}\"";
                Console.WriteLine($"{prefix}  [{child.InferredControlType}]{text}  (Handle: 0x{child.Handle:X}, Enabled: {child.IsEnabled})");

                if (child.Children.Count > 0)
                    PrintWin32Children(child.Children, indent + 1);
            }
        }

        /// <summary>
        /// Write Win32 element info to file (fallback for elevated/protected windows)
        /// </summary>
        private static void WriteWin32ToFile(StreamWriter w, Win32ElementInfo info, Point pos, int count)
        {
            w.WriteLine($"\n{new string('-', 80)}");
            w.WriteLine($"Element #{count} [Win32 Fallback] - {DateTime.Now:HH:mm:ss} - Position: ({pos.X}, {pos.Y})");
            w.WriteLine(new string('-', 80));

            w.WriteLine($"Text:            {(string.IsNullOrEmpty(info.Text) ? "[N/A]" : info.Text)}");
            w.WriteLine($"ClassName:       {info.ClassName}");
            w.WriteLine($"InferredType:    {info.InferredControlType}");
            w.WriteLine($"Handle:          0x{info.Handle:X}");
            w.WriteLine($"ControlId:       {info.ControlId}");
            w.WriteLine();
            w.WriteLine($"IsEnabled:       {info.IsEnabled}");
            w.WriteLine($"IsVisible:       {info.IsVisible}");
            w.WriteLine($"Bounds:          {info.Bounds}");
            w.WriteLine();

            if (!string.IsNullOrEmpty(info.ParentText) || !string.IsNullOrEmpty(info.ParentClassName))
            {
                w.WriteLine($"Parent Text:     {(string.IsNullOrEmpty(info.ParentText) ? "[N/A]" : info.ParentText)}");
                w.WriteLine($"Parent Class:    {info.ParentClassName}");
                w.WriteLine($"Parent Handle:   0x{info.ParentHandle:X}");
                w.WriteLine();
            }

            if (info.Children.Count > 0)
            {
                w.WriteLine($"Child Controls ({info.Children.Count}):");
                WriteWin32ChildrenToFile(w, info.Children, indent: 1);
            }

            w.Flush();
        }

        private static void WriteWin32ChildrenToFile(StreamWriter w, List<Win32ElementInfo> children, int indent)
        {
            var prefix = new string(' ', indent * 2);
            foreach (var child in children)
            {
                w.WriteLine($"{prefix}[{child.InferredControlType}] Text=\"{child.Text}\" Handle=0x{child.Handle:X} Enabled={child.IsEnabled} Bounds={child.Bounds}");

                if (child.Children.Count > 0)
                    WriteWin32ChildrenToFile(w, child.Children, indent + 1);
            }
        }

        /// <summary>
        /// Get element value safely
        /// </summary>
        private static string GetValue(AutomationElement el)
        {
            try
            {
                if (el.Patterns.Value.IsSupported)
                {
                    var val = el.Patterns.Value.Pattern.Value.Value;
                    if (!string.IsNullOrWhiteSpace(val) && val.Length < 100)
                        return val;
                }
            }
            catch { }
            return "";
        }

        /// <summary>
        /// Get supported patterns
        /// </summary>
        private static List<string> GetPatterns(AutomationElement el)
        {
            var patterns = new List<string>();

            try
            {
                if (el.Patterns.Invoke.IsSupported) patterns.Add("Invoke");
                if (el.Patterns.Value.IsSupported) patterns.Add("Value");
                if (el.Patterns.Text.IsSupported) patterns.Add("Text");
                if (el.Patterns.Toggle.IsSupported) patterns.Add("Toggle");
                if (el.Patterns.Selection.IsSupported) patterns.Add("Selection");
                if (el.Patterns.Grid.IsSupported) patterns.Add("Grid");
                if (el.Patterns.Window.IsSupported) patterns.Add("Window");
            }
            catch { }

            return patterns;
        }

        /// <summary>
        /// Safe property getter
        /// </summary>
        private static string GetSafe(Func<string?> getter)
        {
            try
            {
                var result = getter();
                return string.IsNullOrWhiteSpace(result) ? "[N/A]" : result;
            }
            catch
            {
                return "[N/A]";
            }
        }

        private static int RunExploreMode(string? appPath)
        {
            Console.WriteLine("=== EXPLORE MODE ===");
            using var automator = new GuiAutomatorBase(appPath);

            var outputFile = $"Logs/explore-{DateTime.Now:yyyyMMdd-HHmmss}.txt";
            automator.LogAllElements(outputFile);

            Console.WriteLine($"UI elements exploration completed. Results saved to: {outputFile}");
            return 0;
        }

        private static async Task<int> ValidateCommands(string? commandsFile)
        {
            if (string.IsNullOrEmpty(commandsFile) || !File.Exists(commandsFile))
            {
                Console.WriteLine("VALIDATION_ERROR|Commands file is required");
                return 1;
            }

            var commands = await LoadCommands(commandsFile);

            if (!commands.Any())
            {
                Console.WriteLine("VALIDATION_ERROR|No commands found");
                return 1;
            }

            var registry = new ActionRegistry();
            var isValid = true;

            foreach (var command in commands)
            {
                var errors = new List<string>();

                if (string.IsNullOrEmpty(command.Action))
                    errors.Add("Action is required");

                try
                {
                    var action = registry.GetAction(command.Action);
                    if (!action.ValidateCommand(command))
                        errors.Add($"Command validation failed for action '{command.Action}'");
                }
                catch (NotSupportedException)
                {
                    errors.Add($"Action '{command.Action}' is not supported");
                }

                if (errors.Any())
                {
                    isValid = false;
                    Console.WriteLine($"VALIDATION_ERROR|{command.Id ?? "unknown"}|{string.Join(", ", errors)}");
                }
                else
                {
                    Console.WriteLine($"VALIDATION_OK|{command.Id ?? "unknown"}|{command.Action}");
                }
            }

            Console.WriteLine($"VALIDATION_SUMMARY|{(isValid ? "PASSED" : "FAILED")}");
            return isValid ? 0 : 3;
        }

        private static void ShowUsage()
        {
            Console.WriteLine("GUI Agent Utils v2.0 - Usage:");
            Console.WriteLine();
            Console.WriteLine("Test Execution:");
            Console.WriteLine("  GuiAgentUtils.exe <test-file.json>              # Run on desktop");
            Console.WriteLine("  GuiAgentUtils.exe <app.exe> <test-file.json>    # Launch app and run");
            Console.WriteLine();
            Console.WriteLine("Utility Modes:");
            Console.WriteLine("  GuiAgentUtils.exe spy [app.exe]                 # Element inspection");
            Console.WriteLine("  GuiAgentUtils.exe explore [app.exe]             # Dump all elements");
            Console.WriteLine("  GuiAgentUtils.exe validate <test-file.json>     # Validate commands");
            Console.WriteLine();
            Console.WriteLine("Exit Codes:");
            Console.WriteLine("  0 - Success");
            Console.WriteLine("  1 - Test execution failed");
            Console.WriteLine("  3 - Validation failed");
            Console.WriteLine("  5 - File not found");
            Console.WriteLine("  99 - Critical error");
        }
    }

    // TestResult and CommandResult classes for this implementation
    public class TestResult
    {
        public string TestId { get; set; } = Guid.NewGuid().ToString();
        public string TestName { get; set; } = "";
        public bool Success { get; set; } = true;
        public string Message { get; set; } = "";
        public DateTime StartTime { get; set; } = DateTime.UtcNow;
        public DateTime EndTime { get; set; }
        public TimeSpan Duration => EndTime - StartTime;
        public List<CommandResult> CommandResults { get; set; } = new();

        public int TotalCommands => CommandResults.Count;
        public int SuccessfulCommands => CommandResults.Count(r => r.Success);
        public int FailedCommands => CommandResults.Count(r => !r.Success);
        public double SuccessRate => TotalCommands > 0 ? (double)SuccessfulCommands / TotalCommands * 100 : 0;
    }

    public class CommandResult
    {
        public string CommandId { get; set; } = "";
        public string Action { get; set; } = "";
        public bool Success { get; set; } = true;
        public string Message { get; set; } = "";
        public string? ErrorMessage { get; set; }
        public TimeSpan Duration { get; set; }
        public string? ScreenshotPath { get; set; }
        public object? Data { get; set; }
        public DateTime ExecutedAt { get; set; } = DateTime.UtcNow;
    }
}
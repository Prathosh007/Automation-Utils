// Utils/Logger.cs
namespace GuiAgentUtils.Utils
{
    public class Logger : IDisposable
    {
        private readonly StreamWriter _logWriter;
        private readonly string _logFilePath;
        public static bool ShowFailuresOnly { get; set; } = true;

        public Logger()
        {
            Directory.CreateDirectory("Logs");
            _logFilePath = $"Logs/execution-{DateTime.Now:yyyyMMdd-HHmmss}.log";
            _logWriter = new StreamWriter(_logFilePath, append: true);

            _logWriter.WriteLine($"=== Test Execution Started: {DateTime.Now} ===");
            _logWriter.WriteLine(new string('=', 80));
            _logWriter.Flush();
        }

        public void LogToFile(string message)
        {
            var timestamp = DateTime.Now.ToString("HH:mm:ss.fff");
            _logWriter.WriteLine($"[{timestamp}] {message}");
            _logWriter.Flush();
        }

        // Output clean remarks to console for GOAT server
        public static void OutputRemark(string testCaseId, bool passed, string remarks)
        {
            // If showing failures only and test passed, skip output
            if (ShowFailuresOnly && passed)
                return;

            var status = passed ? "PASSED" : "FAILED";
            Console.WriteLine($"{testCaseId}|{status}|{remarks}");
        }

        // Output test summary - always show regardless of filter
        public static void OutputSummary(int total, int passed, int failed)
        {
            Console.WriteLine($"SUMMARY|Total:{total}|Passed:{passed}|Failed:{failed}");
        }

        public void Dispose()
        {
            _logWriter?.WriteLine($"=== Test Execution Ended: {DateTime.Now} ===");
            _logWriter?.WriteLine(new string('=', 80));
            _logWriter?.Dispose();
        }
    }
}
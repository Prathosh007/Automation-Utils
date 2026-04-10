namespace GuiAgentUtils.Models
{
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

        // Statistics
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
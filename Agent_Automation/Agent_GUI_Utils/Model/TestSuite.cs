namespace GuiAgentUtils.Models
{
    public class TestSuite
    {
        public string Name { get; set; } = "";
        public string Description { get; set; } = "";
        public string? AppPath { get; set; }
        public int Timeout { get; set; } = 30;
        public bool ContinueOnFailure { get; set; } = true;
        public List<Command> Commands { get; set; } = new();
    }
}
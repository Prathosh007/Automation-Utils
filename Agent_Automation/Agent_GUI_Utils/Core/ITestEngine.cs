using GuiAgentUtils.Models;

namespace GuiAgentUtils.Core
{
    public interface ITestEngine
    {
        TestResult ExecuteTestSuite(string jsonPath);
        TestResult ExecuteCommands(List<Command> commands, string? appPath = null);
    }
}

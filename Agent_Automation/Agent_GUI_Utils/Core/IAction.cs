// Core/IAction.cs
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Core
{
    public interface IAction
    {
        CommandResult Execute(GuiAutomatorBase automator, Command command);
        bool ValidateCommand(Command command);
    }
}
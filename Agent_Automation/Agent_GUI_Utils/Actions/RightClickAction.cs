using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class RightClickAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                Console.WriteLine($"[EXEC] {command.Action} - {command.Description ?? "No description"}");

                try
                {
                    automator.RightClick(command.AutomationId, command.Name, command.ControlType, command.MatchPartial);
                    automator.RefreshMainWindow();
                }
                catch (Exception uiaEx)
                {
                    // Win32 fallback for elevated/protected windows
                    Logger?.LogToFile($"UIA3 RightClick failed: {uiaEx.Message}, attempting Win32 fallback...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        bool clicked = automator.Win32RightClick(windowTitle, command.Name, null, command.MatchPartial);
                        if (!clicked)
                            throw new InvalidOperationException($"Win32 right-click also failed for '{command.Name}' in '{windowTitle}'");
                        Logger?.LogToFile("[WIN32] RightClick succeeded via Win32 fallback");
                    }
                    else
                    {
                        throw;
                    }
                }

                result.Success = true;
                result.Message = "RightClick command completed successfully";
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

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class DoubleClickAction : BaseAction
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
                    automator.DoubleClick(command.AutomationId, command.Name);
                    automator.RefreshMainWindow();
                }
                catch (Exception uiaEx)
                {
                    // Win32 fallback for elevated/protected windows
                    Logger?.LogToFile($"UIA3 DoubleClick failed: {uiaEx.Message}, attempting Win32 fallback...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        bool clicked = automator.Win32DoubleClick(windowTitle, command.Name, null, command.MatchPartial);
                        if (!clicked)
                            throw new InvalidOperationException($"Win32 double-click also failed for '{command.Name}' in '{windowTitle}'");
                        Logger?.LogToFile("[WIN32] DoubleClick succeeded via Win32 fallback");
                    }
                    else
                    {
                        throw; // No window title for fallback
                    }
                }

                result.Success = true;
                result.Message = "DoubleClick command completed successfully";
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
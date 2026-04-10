using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class EnterTextAction : BaseAction
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
                    automator.EnterText(command.AutomationId, command.Name, command.Text ?? "");
                    automator.RefreshMainWindow();
                }
                catch (Exception uiaEx)
                {
                    // Win32 fallback for elevated/protected windows
                    Logger?.LogToFile($"UIA3 EnterText failed: {uiaEx.Message}, attempting Win32 fallback...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        bool entered = automator.Win32EnterText(windowTitle, command.Name, command.Text ?? "", command.MatchPartial);
                        if (!entered)
                            throw new InvalidOperationException($"Win32 enter text also failed for '{command.Name}' in '{windowTitle}'");
                        Logger?.LogToFile("[WIN32] EnterText succeeded via Win32 fallback");
                    }
                    else
                    {
                        throw;
                    }
                }

                result.Success = true;
                result.Message = "EnterText command completed successfully";
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
                   !string.IsNullOrEmpty(command.Text) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}

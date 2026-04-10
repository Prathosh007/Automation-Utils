using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class ToggleAction : BaseAction
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
                    automator.ToggleCheckbox(command.AutomationId, command.Name, command.Checked);
                    automator.RefreshMainWindow();
                }
                catch (Exception uiaEx)
                {
                    // Win32 fallback for elevated/protected windows
                    Logger?.LogToFile($"UIA3 Toggle failed: {uiaEx.Message}, attempting Win32 fallback...");
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        bool toggled = automator.Win32ToggleCheckbox(windowTitle, command.Name, command.Checked, command.MatchPartial);
                        if (!toggled)
                            throw new InvalidOperationException($"Win32 toggle also failed for '{command.Name}' in '{windowTitle}'");
                        Logger?.LogToFile("[WIN32] Toggle succeeded via Win32 fallback");
                    }
                    else
                    {
                        throw;
                    }
                }

                result.Success = true;
                result.Message = "Toggle command completed successfully";
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
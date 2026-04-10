using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class WaitAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                // Use the enhanced window targeting with built-in timeout
                var element = FindElementInTargetWindow(automator, command);

                if (element == null)
                {
                    // Win32 fallback: check if element exists in elevated window
                    Logger?.LogToFile("UIA3 element not found for wait, attempting Win32 fallback...");
                    var win32Element = FindWin32ElementInTargetWindow(automator, command);

                    if (win32Element != null)
                    {
                        Logger?.LogToFile($"[WIN32] Element found via Win32: '{win32Element.Text}' ({win32Element.ClassName})");
                        HandleSuccess(command, result);
                        return result;
                    }

                    var windowInfo = !string.IsNullOrEmpty(command.WindowName)
                        ? $" in window '{command.WindowName}'"
                        : "";
                    throw new System.TimeoutException($"Element not found within {command.Timeout ?? 10} seconds{windowInfo}: AutomationId='{command.AutomationId}', Name='{command.Name}'");
                }

                Logger?.LogToFile($"Element found successfully: {element.Name ?? element.AutomationId ?? "Unknown"}");
                HandleSuccess(command, result);
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
            return base.ValidateCommand(command);
        }
    }
}
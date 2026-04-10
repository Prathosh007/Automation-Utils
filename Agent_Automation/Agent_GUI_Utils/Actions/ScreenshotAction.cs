using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;

namespace GuiAgentUtils.Actions
{
    public class ScreenshotAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                Console.WriteLine($"[EXEC] {command.Action} - {command.Description ?? "No description"}");

                var screenshotPath = command.FilePath ?? $"Screenshots/screenshot_{DateTime.Now:yyyyMMdd_HHmmss}.png";
                automator.CaptureScreenshot(screenshotPath);
                result.Data = screenshotPath;

                result.Success = true;
                result.Message = "Screenshot command completed successfully";
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
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Diagnostics;

namespace GuiAgentUtils.Actions
{
    /// <summary>
    /// Opens/launches an application
    /// </summary>
    public class AppOpenAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                var exePath = command.AppPath;
                if (string.IsNullOrEmpty(exePath))
                {
                    throw new InvalidOperationException("AppPath is required for open action");
                }

                if (!File.Exists(exePath))
                {
                    throw new FileNotFoundException($"Application not found: {exePath}");
                }

                Logger?.LogToFile($"Opening application: {exePath}");

                // Start the process
                var processStartInfo = new ProcessStartInfo
                {
                    FileName = exePath,
                    UseShellExecute = true,
                    WorkingDirectory = Path.GetDirectoryName(exePath)
                };

                var process = Process.Start(processStartInfo);
                if (process == null)
                {
                    throw new InvalidOperationException("Failed to start process");
                }

                Logger?.LogToFile($"Process started. PID: {process.Id}");

                // Wait for main window with timeout
                var launchTimeout = command.LaunchTimeout ?? command.Timeout ?? 10;
                var startTime = DateTime.Now;

                while ((DateTime.Now - startTime).TotalSeconds < launchTimeout)
                {
                    process.Refresh();

                    if (!process.HasExited && process.MainWindowHandle != IntPtr.Zero)
                    {
                        Logger?.LogToFile($"Main window detected: {process.MainWindowTitle}");
                        Thread.Sleep(1000);

                        result.Success = true;
                        result.Message = $"Application opened successfully: {Path.GetFileName(exePath)}";
                        result.Data = new
                        {
                            ProcessId = process.Id,
                            ProcessName = process.ProcessName,
                            MainWindowTitle = process.MainWindowTitle
                        };

                        HandleSuccess(command, result);
                        return result;
                    }

                    Thread.Sleep(500);
                }

                // Timeout - but process started
                Logger?.LogToFile($"Application opened but main window not detected within {launchTimeout} seconds");
                result.Success = true;
                result.Message = $"Application opened (PID: {process.Id}) but main window not detected";
                result.Data = new { ProcessId = process.Id, ProcessName = process.ProcessName };

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
            return base.ValidateCommand(command) && !string.IsNullOrEmpty(command.AppPath);
        }
    }
}
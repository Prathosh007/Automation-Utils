using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Text.Json;
using GuiAgentUtils.Actions;

namespace GuiAgentUtils.Core
{
    public class TestEngine : ITestEngine
    {
        private readonly ActionRegistry _actionRegistry;
        private Logger? _logger;
        private readonly VariableContext _variableContext;

        public TestEngine()
        {
            _actionRegistry = new ActionRegistry();
            _variableContext = new VariableContext();
        }

        public TestResult ExecuteTestSuite(string jsonPath)
        {
            var testResult = new TestResult();
            _logger = new Logger();

            try
            {
                if (!File.Exists(jsonPath))
                {
                    testResult.Success = false;
                    testResult.Message = $"Test file not found: {jsonPath}";
                    return testResult;
                }

                _logger.LogToFile($"Loading test suite from: {jsonPath}");

                var testData = LoadTestData(jsonPath);
                testResult.TestName = testData.TestSuite?.Name ?? Path.GetFileNameWithoutExtension(jsonPath);
                testResult.StartTime = DateTime.UtcNow;

                var commands = testData.TestSuite?.Commands ?? testData.Commands ?? new List<Command>();
                var appPath = testData.TestSuite?.AppPath;

                if (!commands.Any())
                {
                    testResult.Success = false;
                    testResult.Message = "No commands found to execute";
                    testResult.EndTime = DateTime.UtcNow;
                    return testResult;
                }

                _logger.LogToFile($"Executing {commands.Count} commands with app path: {appPath ?? "Desktop mode"}");

                // Suppress console output during app launch
                var originalOut = Console.Out;
                var stringWriter = new StringWriter();

                try
                {
                    Console.SetOut(stringWriter);
                    using var automator = new GuiAutomatorBase(appPath);
                    Console.SetOut(originalOut);

                    // Log launch output to file
                    var launchOutput = stringWriter.ToString();
                    if (!string.IsNullOrEmpty(launchOutput))
                    {
                        _logger.LogToFile($"Application Launch Output: {launchOutput}");
                    }

                    foreach (var command in commands)
                    {
                        // Apply variable substitution to command
                        var processedCommand = ProcessCommandWithVariables(command);

                        var commandResult = ExecuteSingleCommand(automator, processedCommand);
                        testResult.CommandResults.Add(commandResult);

                        // Store command result in variable context
                        if (!string.IsNullOrEmpty(command.Id))
                        {
                            _variableContext.StoreCommandResult(command.Id, commandResult);
                            _logger.LogToFile($"Stored result for step '{command.Id}': Success={commandResult.Success}, Data='{commandResult.Data}'");
                        }

                        if (!commandResult.Success)
                        {
                            _logger.LogToFile($"Command failed: {command.Id} - {commandResult.ErrorMessage}");

                            if (!command.ContinueOnFailure)
                            {
                                _logger.LogToFile("Stopping execution due to failure");
                                break;
                            }
                        }
                        else
                        {
                            _logger.LogToFile($"Command completed successfully: {command.Id}");
                        }

                        Thread.Sleep(500);
                    }
                }
                finally
                {
                    Console.SetOut(originalOut);
                    stringWriter.Dispose();
                }

                testResult.EndTime = DateTime.UtcNow;
                testResult.Success = testResult.FailedCommands == 0;
                testResult.Message = GenerateTestSummary(testResult);

                _logger.LogToFile($"Test execution completed: {testResult.Message}");

                // Output final summary to console for GOAT server
                Logger.OutputSummary(
                    testResult.TotalCommands,
                    testResult.SuccessfulCommands,
                    testResult.FailedCommands
                );
            }
            catch (Exception ex)
            {
                testResult.Success = false;
                testResult.Message = $"Test execution failed: {ex.Message}";
                testResult.EndTime = DateTime.UtcNow;
                _logger?.LogToFile($"Critical error: {ex.Message}");
                Console.WriteLine($"CRITICAL_ERROR|{ex.Message}");
            }
            finally
            {
                _logger?.Dispose();
            }

            return testResult;
        }

        public TestResult ExecuteCommands(List<Command> commands, string? appPath = null)
        {
            var testResult = new TestResult
            {
                TestName = "Command Execution",
                StartTime = DateTime.UtcNow
            };

            _logger = new Logger();

            try
            {
                if (!commands.Any())
                {
                    testResult.Success = false;
                    testResult.Message = "No commands provided to execute";
                    testResult.EndTime = DateTime.UtcNow;
                    return testResult;
                }

                _logger.LogToFile($"Executing {commands.Count} commands with app path: {appPath ?? "Desktop mode"}");

                using var automator = new GuiAutomatorBase(appPath);

                foreach (var command in commands)
                {
                    // Apply variable substitution to command
                    var processedCommand = ProcessCommandWithVariables(command);

                    var commandResult = ExecuteSingleCommand(automator, processedCommand);
                    testResult.CommandResults.Add(commandResult);

                    // Store command result in variable context
                    if (!string.IsNullOrEmpty(command.Id))
                    {
                        _variableContext.StoreCommandResult(command.Id, commandResult);
                        _logger.LogToFile($"Stored result for step '{command.Id}': Success={commandResult.Success}, Data='{commandResult.Data}'");
                    }

                    if (!commandResult.Success && !command.ContinueOnFailure)
                    {
                        _logger.LogToFile("Execution stopped due to failure");
                        break;
                    }

                    Thread.Sleep(500);
                }

                testResult.EndTime = DateTime.UtcNow;
                testResult.Success = testResult.FailedCommands == 0;
                testResult.Message = GenerateTestSummary(testResult);

                // Output final summary
                Logger.OutputSummary(
                    testResult.TotalCommands,
                    testResult.SuccessfulCommands,
                    testResult.FailedCommands
                );
            }
            catch (Exception ex)
            {
                testResult.Success = false;
                testResult.Message = $"Command execution failed: {ex.Message}";
                testResult.EndTime = DateTime.UtcNow;
                _logger?.LogToFile($"Critical error: {ex.Message}");
                Console.WriteLine($"CRITICAL_ERROR|{ex.Message}");
            }
            finally
            {
                _logger?.Dispose();
            }

            return testResult;
        }

        /// <summary>
        /// Process a command and substitute variables in its properties
        /// </summary>
        private Command ProcessCommandWithVariables(Command originalCommand)
        {
            try
            {
                var processedCommand = new Command
                {
                    Id = originalCommand.Id,
                    Action = originalCommand.Action,
                    AutomationId = SubstituteIfNeeded(originalCommand.AutomationId),
                    Name = SubstituteIfNeeded(originalCommand.Name),
                    Text = SubstituteIfNeeded(originalCommand.Text),
                    ExpectedValue = SubstituteIfNeeded(originalCommand.ExpectedValue),
                    ExpectedResult = SubstituteIfNeeded(originalCommand.ExpectedResult),
                    Description = SubstituteIfNeeded(originalCommand.Description),
                    FilePath = SubstituteIfNeeded(originalCommand.FilePath),
                    AppName = SubstituteIfNeeded(originalCommand.AppName),
                    ServiceName = SubstituteIfNeeded(originalCommand.ServiceName),

                    AppPath = SubstituteIfNeeded(originalCommand.AppPath),
                    ProcessName = SubstituteIfNeeded(originalCommand.ProcessName),
                    ForceKill = originalCommand.ForceKill,
                    WindowClassName = SubstituteIfNeeded(originalCommand.WindowClassName),

                    Checked = originalCommand.Checked,
                    ControlType = originalCommand.ControlType,
                    Timeout = originalCommand.Timeout,
                    MatchPartial = originalCommand.MatchPartial,
                    ContinueOnFailure = originalCommand.ContinueOnFailure,
                    TakeScreenshotOnFailure = originalCommand.TakeScreenshotOnFailure,
                    Validation = originalCommand.Validation,
                    CellValidations = originalCommand.CellValidations,
                    ValidationType = SubstituteIfNeeded(originalCommand.ValidationType),
                    CaseSensitive = originalCommand.CaseSensitive,
                    Row = originalCommand.Row,
                    Column = originalCommand.Column,

                    ColumnValidations = originalCommand.ColumnValidations,
                    KeyValuePairs = originalCommand.KeyValuePairs,
                    KeyColumn = originalCommand.KeyColumn,
                    ValueColumn = originalCommand.ValueColumn,

                    WindowName = SubstituteIfNeeded(originalCommand.WindowName),
                    WindowTitle = SubstituteIfNeeded(originalCommand.WindowTitle),
                    WindowAutomationId = SubstituteIfNeeded(originalCommand.WindowAutomationId),
                    WindowPartialMatch = originalCommand.WindowPartialMatch,

                    ButtonName = SubstituteIfNeeded(originalCommand.ButtonName),
                    ButtonAutomationId = SubstituteIfNeeded(originalCommand.ButtonAutomationId),
                    TextBoxAutomationId = SubstituteIfNeeded(originalCommand.TextBoxAutomationId),
                    DialogTitle = SubstituteIfNeeded(originalCommand.DialogTitle)
                };

                LogSubstitutions(originalCommand, processedCommand);
                return processedCommand;
            }
            catch (Exception ex)
            {
                _logger?.LogToFile($"Error processing variables for command {originalCommand.Id}: {ex.Message}");
                return originalCommand;
            }
        }
        /// <summary>
        /// Substitute variables in a string if it contains variable references
        /// </summary>
        private string? SubstituteIfNeeded(string? value)
        {
            if (string.IsNullOrEmpty(value))
                return value;

            if (value.Contains("{{") && value.Contains("}}"))
            {
                return _variableContext.SubstituteVariables(value);
            }

            return value;
        }

        /// <summary>
        /// Log variable substitutions for debugging
        /// </summary>
        private void LogSubstitutions(Command original, Command processed)
        {
            if (original.Text != processed.Text)
                _logger?.LogToFile($"Variable substitution in Text: '{original.Text}' -> '{processed.Text}'");

            if (original.ExpectedValue != processed.ExpectedValue)
                _logger?.LogToFile($"Variable substitution in ExpectedValue: '{original.ExpectedValue}' -> '{processed.ExpectedValue}'");

            if (original.AutomationId != processed.AutomationId)
                _logger?.LogToFile($"Variable substitution in AutomationId: '{original.AutomationId}' -> '{processed.AutomationId}'");

            if (original.Name != processed.Name)
                _logger?.LogToFile($"Variable substitution in Name: '{original.Name}' -> '{processed.Name}'");
        }

        private CommandResult ExecuteSingleCommand(GuiAutomatorBase automator, Command command)
        {
            try
            {
                if (string.IsNullOrEmpty(command.Action))
                {
                    var errorResult = new CommandResult
                    {
                        CommandId = command.Id ?? "unknown",
                        Action = command.Action ?? "unknown",
                        Success = false,
                        ErrorMessage = "Action is required"
                    };

                    Logger.OutputRemark(command.Id ?? "unknown", false, "Action is required");
                    return errorResult;
                }

                var action = _actionRegistry.GetAction(command.Action);

                // Set logger for action
                if (action is BaseAction baseAction)
                {
                    baseAction.SetLogger(_logger!);
                }

                if (!action.ValidateCommand(command))
                {
                    var errorResult = new CommandResult
                    {
                        CommandId = command.Id ?? "unknown",
                        Action = command.Action,
                        Success = false,
                        ErrorMessage = $"Command validation failed for action '{command.Action}'"
                    };

                    // ✅ FIX: Use detailed error message from result
                    Logger.OutputRemark(command.Id ?? "unknown", false, errorResult.ErrorMessage);
                    return errorResult;
                }

                // ✅ CRITICAL FIX: Execute and return result directly
                // The action itself calls Logger.OutputRemark with detailed messages
                var result = action.Execute(automator, command);

                // ✅ Don't override the detailed message from the action
                // Just return the result as-is
                return result;
            }
            catch (NotSupportedException ex)
            {
                var errorMessage = $"Action not supported: {command.Action}";
                Logger.OutputRemark(command.Id ?? "unknown", false, errorMessage);
                return new CommandResult
                {
                    CommandId = command.Id ?? "unknown",
                    Action = command.Action,
                    Success = false,
                    ErrorMessage = ex.Message
                };
            }
            catch (Exception ex)
            {
                var errorMessage = $"Unexpected error: {ex.Message}";
                Logger.OutputRemark(command.Id ?? "unknown", false, errorMessage);
                return new CommandResult
                {
                    CommandId = command.Id ?? "unknown",
                    Action = command.Action,
                    Success = false,
                    ErrorMessage = errorMessage
                };
            }
        }
        private TestData LoadTestData(string jsonPath)
        {
            _logger?.LogToFile($"Loading test suite from: {jsonPath}");

            var json = File.ReadAllText(jsonPath);

            // DEBUG: Log raw JSON
            _logger?.LogToFile($"Raw JSON length: {json.Length}");
            _logger?.LogToFile($"First 1000 chars of JSON: {json.Substring(0, Math.Min(1000, json.Length))}");

            var options = new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
                AllowTrailingCommas = true,
                ReadCommentHandling = JsonCommentHandling.Skip,
                PropertyNamingPolicy = null,
                DictionaryKeyPolicy = null
            };

            try
            {
                var commands = JsonSerializer.Deserialize<List<Command>>(json, options);
                if (commands != null && commands.Any())
                {
                    _logger?.LogToFile($"Loaded {commands.Count} commands directly");

                    // DEBUG: Check first command IMMEDIATELY after deserialization
                    var firstCmd = commands[0];
                    _logger?.LogToFile($"DEBUG AFTER DESER - First command ID: {firstCmd.Id}");
  
                   

                    _logger?.LogToFile($"DEBUG AFTER DESER - ColumnValidations is null: {firstCmd.ColumnValidations == null}");

                    return new TestData { Commands = commands };
                }
            }
            catch (Exception ex)
            {
                _logger?.LogToFile($"Failed to parse as command list: {ex.Message}");
                _logger?.LogToFile($"Stack trace: {ex.StackTrace}");
                throw new InvalidOperationException($"Failed to parse test data: {ex.Message}");
            }

            throw new InvalidOperationException("No valid test data found in file");
        }

        private string GenerateTestSummary(TestResult testResult)
        {
            if (testResult.Success)
            {
                return $"All {testResult.TotalCommands} commands completed successfully";
            }
            else
            {
                return $"{testResult.FailedCommands} out of {testResult.TotalCommands} commands failed";
            }
        }

    }


    internal class TestSuiteWrapper
    {
        public TestSuite? TestSuite { get; set; }
    }

    internal class TestData
    {
        public TestSuite? TestSuite { get; set; }
        public List<Command>? Commands { get; set; }
    }
}
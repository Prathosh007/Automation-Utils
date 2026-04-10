using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Text.RegularExpressions;

namespace GuiAgentUtils.Actions
{
    public class ReadTextAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);

                string text;
                try
                {
                    text = ExecuteWithSuppressedOutput(() => automator.ReadText(command.AutomationId, command.Name));
                }
                catch (Exception uiaEx)
                {
                    Logger?.LogToFile($"UIA3 ReadText failed: {uiaEx.Message}, attempting Win32 fallback...");
                    text = "";
                }

                // Win32 fallback if UIA3 returned empty or failed
                if (string.IsNullOrEmpty(text))
                {
                    var windowTitle = GetWin32WindowTitle(command);
                    if (!string.IsNullOrEmpty(windowTitle))
                    {
                        Logger?.LogToFile("[WIN32] Attempting Win32 ReadText fallback...");
                        text = automator.Win32ReadText(windowTitle, command.Name, null, command.MatchPartial);
                        if (!string.IsNullOrEmpty(text))
                            Logger?.LogToFile($"[WIN32] Read text via Win32: '{text}'");
                    }
                }

                result.Data = text;
                Logger?.LogToFile($"Read text: '{text}'");

                // Check if validation is needed
                if (!string.IsNullOrEmpty(command.ExpectedValue))
                {
                    var validationType = command.ValidationType ?? "exact"; // Default to exact match
                    var isValid = ValidateText(text, command.ExpectedValue, validationType, command.CaseSensitive);

                    if (!isValid)
                    {
                        var remarks = GenerateFailureRemarks(text, command.ExpectedValue, validationType);
                        Logger?.LogToFile($"Text validation failed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                        result.Success = false;
                        result.Message = remarks;
                        result.ErrorMessage = remarks;
                        return result;
                    }
                    else
                    {
                        var remarks = GenerateSuccessRemarks(text, command.ExpectedValue, validationType);
                        Logger?.LogToFile($"Text validation passed: {remarks}");
                        Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    }
                }
                else
                {
                    // No validation, just read
                    var remarks = $"Text read: '{text}'";
                    Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                }

                result.Success = true;
                result.Message = "ReadText command completed successfully";
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

        private bool ValidateText(string actualText, string expectedValue, string validationType, bool caseSensitive)
        {
            try
            {
                var comparison = caseSensitive ? StringComparison.Ordinal : StringComparison.OrdinalIgnoreCase;

                // Normalize whitespace: replace newlines/tabs with spaces, collapse multiple spaces
                var normalizedActual = NormalizeWhitespace(actualText);
                var normalizedExpected = NormalizeWhitespace(expectedValue);

                return validationType.ToLower() switch
                {
                    "exact" => normalizedActual.Equals(normalizedExpected, comparison),
                    "contains" => normalizedActual.Contains(normalizedExpected, comparison),
                    "startswith" => normalizedActual.StartsWith(normalizedExpected, comparison),
                    "endswith" => normalizedActual.EndsWith(normalizedExpected, comparison),
                    "regex" => Regex.IsMatch(actualText, expectedValue),
                    "notempty" => !string.IsNullOrWhiteSpace(actualText),
                    "isempty" => string.IsNullOrWhiteSpace(actualText),
                    "isnumeric" => double.TryParse(actualText, out _),
                    "isdate" => DateTime.TryParse(actualText, out _),
                    "isurl" => Uri.TryCreate(actualText, UriKind.Absolute, out _),
                    "isemail" => ValidateEmail(actualText),
                    "oneof" => ValidateOneOf(normalizedActual, normalizedExpected, comparison),
                    "length" => int.TryParse(expectedValue, out var len) && normalizedActual.Length == len,
                    "minlength" => int.TryParse(expectedValue, out var minLen) && normalizedActual.Length >= minLen,
                    "maxlength" => int.TryParse(expectedValue, out var maxLen) && normalizedActual.Length <= maxLen,
                    _ => throw new NotSupportedException($"Validation type '{validationType}' is not supported")
                };
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Validation error for type '{validationType}': {ex.Message}");
                return false;
            }
        }

        private bool ValidateEmail(string text)
        {
            try
            {
                var emailRegex = new Regex(@"^[^@\s]+@[^@\s]+\.[^@\s]+$", RegexOptions.IgnoreCase);
                return emailRegex.IsMatch(text);
            }
            catch
            {
                return false;
            }
        }

        /// <summary>
        /// Normalize whitespace: replace newlines/tabs with spaces, collapse multiple spaces into one.
        /// This ensures Win32 multi-line text (from MessageBox) matches single-line expected values.
        /// </summary>
        private static string NormalizeWhitespace(string text)
        {
            if (string.IsNullOrEmpty(text)) return text;
            return Regex.Replace(text.Replace("\r\n", " ").Replace("\n", " ").Replace("\r", " ").Replace("\t", " "), @"\s{2,}", " ").Trim();
        }

        private bool ValidateOneOf(string text, string expectedValues, StringComparison comparison)
        {
            var values = expectedValues.Split(',').Select(v => v.Trim());
            return values.Any(value => text.Equals(value, comparison));
        }

        private string GenerateFailureRemarks(string actualText, string expectedValue, string validationType)
        {
            return validationType.ToLower() switch
            {
                "exact" => $"Expected '{expectedValue}' but got '{actualText}'",
                "contains" => $"Expected text to contain '{expectedValue}' but got '{actualText}'",
                "startswith" => $"Expected text to start with '{expectedValue}' but got '{actualText}'",
                "endswith" => $"Expected text to end with '{expectedValue}' but got '{actualText}'",
                "regex" => $"Text '{actualText}' does not match pattern '{expectedValue}'",
                "notempty" => $"Expected non-empty text but got '{actualText}'",
                "isempty" => $"Expected empty text but got '{actualText}'",
                "oneof" => $"Expected one of [{expectedValue}] but got '{actualText}'",
                "isnumeric" => $"Expected numeric value but got '{actualText}'",
                "isdate" => $"Expected date format but got '{actualText}'",
                "isurl" => $"Expected valid URL but got '{actualText}'",
                "isemail" => $"Expected valid email but got '{actualText}'",
                "length" => $"Expected length {expectedValue} but got '{actualText}' (length {actualText.Length})",
                "minlength" => $"Expected minimum length {expectedValue} but got '{actualText}' (length {actualText.Length})",
                "maxlength" => $"Expected maximum length {expectedValue} but got '{actualText}' (length {actualText.Length})",
                _ => $"Validation '{validationType}' failed: Expected '{expectedValue}' but got '{actualText}'"
            };
        }

        private string GenerateSuccessRemarks(string actualText, string expectedValue, string validationType)
        {
            return validationType.ToLower() switch
            {
                "exact" => $"Text matches exactly: '{actualText}'",
                "contains" => $"Text contains '{expectedValue}': '{actualText}'",
                "startswith" => $"Text starts with '{expectedValue}': '{actualText}'",
                "endswith" => $"Text ends with '{expectedValue}': '{actualText}'",
                "regex" => $"Text matches pattern: '{actualText}'",
                "notempty" => $"Text is not empty: '{actualText}'",
                "isempty" => "Text is empty as expected",
                "oneof" => $"Text matches one of expected values: '{actualText}'",
                "isnumeric" => $"Text is numeric: '{actualText}'",
                "isdate" => $"Text is valid date: '{actualText}'",
                "isurl" => $"Text is valid URL: '{actualText}'",
                "isemail" => $"Text is valid email: '{actualText}'",
                "length" => $"Text has correct length: '{actualText}'",
                "minlength" => $"Text meets minimum length: '{actualText}'",
                "maxlength" => $"Text is within maximum length: '{actualText}'",
                _ => $"Text validation passed: '{actualText}'"
            };
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}
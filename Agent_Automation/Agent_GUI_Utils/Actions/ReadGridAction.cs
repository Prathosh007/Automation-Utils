using FlaUI.Core.Tools;
using GuiAgentUtils.Helpers;
using GuiAgentUtils.Models;
using GuiAgentUtils.Utils;
using System.Text.RegularExpressions;

namespace GuiAgentUtils.Actions
{
    public class ReadGridAction : BaseAction
    {
        public override CommandResult Execute(GuiAutomatorBase automator, Command command)
        {
            var result = CreateResult(command);
            var sw = System.Diagnostics.Stopwatch.StartNew();

            try
            {
                LogExecution(command);
                ConvertValidationStructures(command);

                var gridResult = ExecuteWithSuppressedOutput(() =>
                    automator.ReadDataGridGeneric(command.AutomationId, command.Name));

                if (!gridResult.Success)
                {
                    var remarks = $"Grid reading failed: {gridResult.ErrorMessage}";
                    Logger?.LogToFile(remarks);
                    TakeFailureScreenshot(automator, command, result);
                    Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                    result.Success = false;
                    result.Message = remarks;
                    result.ErrorMessage = remarks;
                    return result;
                }

                Logger?.LogToFile($"Grid read successfully: {gridResult.TotalRows} rows, {gridResult.TotalColumns} columns");
                LogGridDataToFile(gridResult);

                var validationSuccess = ExecuteValidation(gridResult, command, automator, result);

                if (!validationSuccess)
                {
                    return result;
                }

                result.Success = true;
                result.Message = "ReadGrid command completed successfully";
            }
            catch (Exception ex)
            {
                var remarks = $"ReadGrid error: {ex.Message}";
                Logger?.LogToFile($"ReadGrid exception: {ex.Message}");
                Logger?.LogToFile($"Stack trace: {ex.StackTrace}");

                TakeFailureScreenshot(automator, command, result);
                Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                result.Success = false;
                result.Message = remarks;
                result.ErrorMessage = remarks;
            }
            finally
            {
                sw.Stop();
                result.Duration = sw.Elapsed;
            }

            return result;
        }

        private void ConvertValidationStructures(Command command)
        {
            if (command.Validation != null)
                return;

            if (command.KeyValuePairs != null && command.KeyValuePairs.Any())
            {
                Logger?.LogToFile($"Key-Value validation mode detected with {command.KeyValuePairs.Count} pairs");
                return;
            }

            if (command.ColumnValidations != null && command.ColumnValidations.Any())
            {
                Logger?.LogToFile($"Column validation mode detected with {command.ColumnValidations.Count} columns");
                return;
            }

            if (command.CellValidations != null && command.CellValidations.Any())
            {
                Logger?.LogToFile($"Converting {command.CellValidations.Count} direct CellValidations to ValidationConfig");
                command.Validation = new ValidationConfig
                {
                    CellValidations = command.CellValidations,
                    ContinueOnFailure = command.ContinueOnFailure,
                    ShowFailureDetails = true,
                    AllowEmptyResults = true
                };
                return;
            }

            if (!string.IsNullOrEmpty(command.ValidationType) && !string.IsNullOrEmpty(command.ExpectedValue))
            {
                Logger?.LogToFile("Converting simple validation properties to ValidationConfig");
                command.Validation = new ValidationConfig
                {
                    CellValidations = new List<CellValidation>
                    {
                        new CellValidation
                        {
                            Row = command.Row ?? -1,
                            Column = command.Column ?? -1,
                            ExpectedValue = command.ExpectedValue,
                            ValidationType = command.ValidationType ?? "exact",
                            CaseSensitive = command.CaseSensitive,
                            Description = command.Description ?? "Auto-generated cell validation",
                            ContinueOnFailure = command.ContinueOnFailure
                        }
                    },
                    ContinueOnFailure = command.ContinueOnFailure,
                    ShowFailureDetails = true,
                    AllowEmptyResults = true
                };
            }
        }

        private bool ExecuteValidation(GridResult gridResult, Command command, GuiAutomatorBase automator, CommandResult result)
        {
            // KEY-VALUE VALIDATION
            if (command.KeyValuePairs != null && command.KeyValuePairs.Any())
            {
                Logger?.LogToFile($"EXECUTING Key-Value validation with {command.KeyValuePairs.Count} pairs");
                var kvValidation = ValidateKeyValuePairs(gridResult, command);

                if (!kvValidation.success)
                {
                    var remarks = kvValidation.message;
                    Logger?.LogToFile($"Key-Value validation failed: {remarks}");
                    TakeFailureScreenshot(automator, command, result);
                    Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                    result.Success = false;
                    result.Message = remarks;
                    result.ErrorMessage = remarks;
                    return false;
                }
                else
                {
                    var remarks = kvValidation.message;
                    Logger?.LogToFile($"Key-Value validation passed: {remarks}");
                    Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    result.Data = gridResult;
                    return true;
                }
            }

            // COLUMN VALIDATION
            if (command.ColumnValidations != null && command.ColumnValidations.Any())
            {
                Logger?.LogToFile($"EXECUTING Column validation with {command.ColumnValidations.Count} columns");
                var colValidation = ValidateColumns(gridResult, command);

                if (!colValidation.success)
                {
                    var remarks = colValidation.message;
                    Logger?.LogToFile($"Column validation failed: {remarks}");
                    TakeFailureScreenshot(automator, command, result);
                    Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                    result.Success = false;
                    result.Message = remarks;
                    result.ErrorMessage = remarks;
                    return false;
                }
                else
                {
                    var remarks = colValidation.message;
                    Logger?.LogToFile($"Column validation passed: {remarks}");
                    Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    result.Data = gridResult;
                    return true;
                }
            }

            // CELL VALIDATION
            if (command.Validation != null && command.Validation.CellValidations?.Any() == true)
            {
                Logger?.LogToFile("Using CellValidations mode");
                var validationResult = ValidationHelper.ValidateGrid(gridResult, command.Validation);

                Logger?.LogToFile($"VALIDATION RESULT: {validationResult.Message}");
                Logger?.LogToFile($"Validation Issues: Critical={validationResult.CriticalIssues}, Warnings={validationResult.WarningIssues}");

                foreach (var issue in validationResult.Issues)
                {
                    Logger?.LogToFile($"  {issue.Severity}: {issue.Message} [Row {issue.Row + 1}, Col {issue.Column + 1}] Value: '{issue.CellValue}'");
                }

                bool shouldFail = validationResult.CriticalIssues > 0 ||
                                 (validationResult.WarningIssues > 0 && HasExactValidations(command.Validation));

                if (shouldFail)
                {
                    var remarks = GenerateComplexFailureRemarks(validationResult, gridResult);
                    Logger?.LogToFile($"Command failed: {remarks}");
                    TakeFailureScreenshot(automator, command, result);
                    Logger.OutputRemark(command.Id ?? "unknown", false, remarks);

                    result.Success = false;
                    result.Message = remarks;
                    result.ErrorMessage = remarks;
                    return false;
                }
                else
                {
                    var remarks = GenerateSuccessRemarks(validationResult, gridResult);
                    result.Data = new { GridResult = gridResult, ValidationResult = validationResult };
                    Logger?.LogToFile($"Command passed: {remarks}");
                    Logger.OutputRemark(command.Id ?? "unknown", true, remarks);
                    return true;
                }
            }

            // NO VALIDATION
            Logger?.LogToFile("No validation configured - just reading grid");
            var successRemarks = $"Grid read: {gridResult.TotalRows} rows, {gridResult.TotalColumns} columns";
            result.Data = gridResult;
            Logger.OutputRemark(command.Id ?? "unknown", true, successRemarks);
            return true;
        }

        private void TakeFailureScreenshot(GuiAutomatorBase automator, Command command, CommandResult result)
        {
            if (command.TakeScreenshotOnFailure)
            {
                try
                {
                    var errorScreenshot = $"Screenshots/error_{command.Id}_{DateTime.Now:yyyyMMdd_HHmmss}.png";
                    Directory.CreateDirectory("Screenshots");
                    ExecuteWithSuppressedOutput(() => automator.CaptureScreenshot(errorScreenshot));
                    result.ScreenshotPath = errorScreenshot;
                    Logger?.LogToFile($"Validation failure screenshot saved: {errorScreenshot}");
                }
                catch (Exception screenshotEx)
                {
                    Logger?.LogToFile($"Could not capture screenshot: {screenshotEx.Message}");
                }
            }
        }

        private (bool success, string message) ValidateKeyValuePairs(GridResult gridResult, Command command)
        {
            try
            {
                var keyColumn = command.KeyColumn ?? 0;
                var valueColumn = command.ValueColumn ?? 1;
                var validationType = command.ValidationType ?? "exact";
                var caseSensitive = command.CaseSensitive;
                var keyValuePairs = command.KeyValuePairs;

                if (keyValuePairs == null || !keyValuePairs.Any())
                {
                    return (false, "KeyValuePairs is empty or null");
                }

                Logger?.LogToFile($"Key-Value validation: KeyColumn={keyColumn}, ValueColumn={valueColumn}, Pairs={keyValuePairs.Count}");

                if (keyColumn < 0 || keyColumn >= gridResult.TotalColumns)
                {
                    return (false, $"KeyColumn {keyColumn} is out of bounds (grid has {gridResult.TotalColumns} columns)");
                }

                if (valueColumn < 0 || valueColumn >= gridResult.TotalColumns)
                {
                    return (false, $"ValueColumn {valueColumn} is out of bounds (grid has {gridResult.TotalColumns} columns)");
                }

                var failedPairs = new List<string>();
                var successCount = 0;

                foreach (var pair in keyValuePairs)
                {
                    var expectedKey = pair.Key;
                    var expectedValue = pair.Value;

                    Logger?.LogToFile($"Looking for key '{expectedKey}' in column {keyColumn}");

                    int? matchingRow = null;
                    for (int row = 0; row < gridResult.TotalRows; row++)
                    {
                        var cellKey = gridResult.Rows[row][keyColumn];
                        var comparison = caseSensitive ? StringComparison.Ordinal : StringComparison.OrdinalIgnoreCase;

                        var normalizedCellKey = NormalizeWhitespace(cellKey);
                        var normalizedExpectedKey = NormalizeWhitespace(expectedKey);

                        if (normalizedCellKey.Equals(normalizedExpectedKey, comparison))
                        {
                            matchingRow = row;
                            Logger?.LogToFile($"Found key '{expectedKey}' at row {row} (cell value: '{cellKey}')");
                            break;
                        }
                    }

                    if (!matchingRow.HasValue)
                    {
                        failedPairs.Add($"Key '{expectedKey}' not found");
                        Logger?.LogToFile($"Key '{expectedKey}' not found in any row");
                        continue;
                    }

                    var actualValue = gridResult.Rows[matchingRow.Value][valueColumn];
                    var isValid = ValidateCellValue(actualValue, expectedValue, validationType, caseSensitive);

                    if (!isValid)
                    {
                        var rowData = GetCleanRowData(gridResult, matchingRow.Value);
                        var rowText = string.Join(" | ", rowData);

                        failedPairs.Add($"Key '{expectedKey}': Expected='{expectedValue}' Actual='{actualValue}' | Row {matchingRow.Value + 1}: [{rowText}]");
                        Logger?.LogToFile($"Validation failed for key '{expectedKey}': Expected '{expectedValue}' got '{actualValue}'");
                        Logger?.LogToFile($"  Full row {matchingRow.Value + 1}: {rowText}");
                    }
                    else
                    {
                        successCount++;
                        Logger?.LogToFile($"Validation passed for key '{expectedKey}': '{actualValue}'");
                    }
                }

                if (failedPairs.Any())
                {
                    var failureMessage = string.Join(" | ", failedPairs.Take(3));
                    var moreText = failedPairs.Count > 3 ? $" (+{failedPairs.Count - 3} more)" : "";
                    return (false, $"Key-Value validation failed: {failureMessage}{moreText}");
                }
                else
                {
                    return (true, $"All {successCount} key-value pairs validated successfully");
                }
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Exception in ValidateKeyValuePairs: {ex.Message}");
                Logger?.LogToFile($"Stack trace: {ex.StackTrace}");
                return (false, $"Key-Value validation error: {ex.Message}");
            }
        }

        private (bool success, string message) ValidateColumns(GridResult gridResult, Command command)
        {
            try
            {
                var columnValidations = command.ColumnValidations;
                var validationType = command.ValidationType ?? "exact";
                var caseSensitive = command.CaseSensitive;

                if (columnValidations == null || !columnValidations.Any())
                {
                    Logger?.LogToFile("ColumnValidations is null or empty");
                    return (false, "ColumnValidations is empty or null");
                }

                Logger?.LogToFile($"Column validation: {columnValidations.Count} columns to validate, ValidationType={validationType}");

                var intColumnValidations = new Dictionary<int, List<string>>();
                foreach (var kvp in columnValidations)
                {
                    if (int.TryParse(kvp.Key, out int colIndex))
                    {
                        intColumnValidations[colIndex] = kvp.Value;
                        Logger?.LogToFile($"Column {colIndex} has {kvp.Value.Count} expected values");
                    }
                    else
                    {
                        Logger?.LogToFile($"WARNING: Could not parse column key '{kvp.Key}' as integer");
                    }
                }

                if (!intColumnValidations.Any())
                {
                    return (false, "No valid column indices found in ColumnValidations");
                }

                var failedValidations = new List<string>();
                var totalValidations = 0;
                var successCount = 0;

                foreach (var colValidation in intColumnValidations.OrderBy(x => x.Key))
                {
                    var columnIndex = colValidation.Key;
                    var expectedValues = colValidation.Value;

                    Logger?.LogToFile($"Validating column {columnIndex} with {expectedValues.Count} expected values");

                    if (columnIndex < 0 || columnIndex >= gridResult.TotalColumns)
                    {
                        failedValidations.Add($"Column {columnIndex} is out of bounds (grid has {gridResult.TotalColumns} columns)");
                        continue;
                    }

                    for (int rowIndex = 0; rowIndex < expectedValues.Count; rowIndex++)
                    {
                        totalValidations++;
                        var expectedValue = expectedValues[rowIndex];

                        if (rowIndex >= gridResult.TotalRows)
                        {
                            failedValidations.Add($"Col {columnIndex} Row {rowIndex + 1}: Expected='{expectedValue}' but row doesn't exist");
                            Logger?.LogToFile($"Row {rowIndex} doesn't exist in grid (total rows: {gridResult.TotalRows})");
                            continue;
                        }

                        var actualValue = gridResult.Rows[rowIndex][columnIndex];
                        var isValid = ValidateCellValue(actualValue, expectedValue, validationType, caseSensitive);

                        if (!isValid)
                        {
                            var rowData = GetCleanRowData(gridResult, rowIndex);
                            var rowText = string.Join(" | ", rowData);

                            failedValidations.Add($"Col {columnIndex} Row {rowIndex + 1}: Expected='{expectedValue}' Actual='{actualValue}' | Row: [{rowText}]");
                            Logger?.LogToFile($"Validation failed at Column {columnIndex}, Row {rowIndex}: Expected '{expectedValue}' got '{actualValue}'");
                            Logger?.LogToFile($"  Full row {rowIndex + 1}: {rowText}");
                        }
                        else
                        {
                            successCount++;
                            Logger?.LogToFile($"Validation passed at Column {columnIndex}, Row {rowIndex}: '{actualValue}'");
                        }
                    }
                }

                if (failedValidations.Any())
                {
                    var failureMessage = string.Join(" | ", failedValidations.Take(3));
                    var moreText = failedValidations.Count > 3 ? $" (+{failedValidations.Count - 3} more)" : "";
                    return (false, $"Column validation failed: {failureMessage}{moreText}");
                }
                else
                {
                    return (true, $"All {totalValidations} column validations passed across {intColumnValidations.Count} columns");
                }
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Exception in ValidateColumns: {ex.Message}");
                Logger?.LogToFile($"Stack trace: {ex.StackTrace}");
                return (false, $"Column validation error: {ex.Message}");
            }
        }

        private List<string> GetCleanRowData(GridResult gridResult, int rowIndex)
        {
            if (rowIndex < 0 || rowIndex >= gridResult.Rows.Count)
            {
                return new List<string> { "[ROW_NOT_FOUND]" };
            }

            var cleanRow = new List<string>();
            var row = gridResult.Rows[rowIndex];

            foreach (var cell in row)
            {
                var cleanCell = cell ?? "";

                // Show [EMPTY] for truly empty cells for better visibility
                if (string.IsNullOrWhiteSpace(cleanCell))
                {
                    cleanCell = "";  // or use "[EMPTY]" if you want to show it explicitly
                }
                else if (cleanCell.Length > 50)
                {
                    cleanCell = cleanCell.Substring(0, 47) + "...";
                }

                cleanRow.Add(cleanCell);
            }

            return cleanRow;
        }

        private string CleanCellValue(string cellValue)
        {
            if (string.IsNullOrWhiteSpace(cellValue))
                return "[EMPTY]";

            if (cellValue.StartsWith("Item: {") || cellValue.Contains("domains ="))
            {
                if (cellValue.Contains("status ="))
                {
                    var statusStart = cellValue.IndexOf("status =") + 8;
                    var remaining = cellValue.Substring(statusStart);
                    var statusEnd = remaining.IndexOfAny(new[] { ',', '}' });
                    if (statusEnd > 0)
                    {
                        return remaining.Substring(0, statusEnd).Trim();
                    }
                }

                if (cellValue.Contains("remarks ="))
                {
                    var remarksStart = cellValue.IndexOf("remarks =") + 9;
                    var remaining = cellValue.Substring(remarksStart);
                    var remarksEnd = remaining.IndexOfAny(new[] { ',', '}' });
                    if (remarksEnd > 0)
                    {
                        return remaining.Substring(0, remarksEnd).Trim();
                    }
                }

                return "[OBJECT]";
            }

            var cleaned = cellValue.Trim()
                                  .Replace("\r\n", " ")
                                  .Replace("\n", " ")
                                  .Replace("\r", " ")
                                  .Replace("\t", " ");

            while (cleaned.Contains("  "))
            {
                cleaned = cleaned.Replace("  ", " ");
            }

            return cleaned;
        }

        private string NormalizeWhitespace(string text)
        {
            if (string.IsNullOrEmpty(text))
                return text ?? "";

            return Regex.Replace(text.Trim(), @"\s+", " ");
        }

        private bool ValidateCellValue(string actualValue, string expectedValue, string validationType, bool caseSensitive)
        {
            try
            {
                var comparison = caseSensitive ? StringComparison.Ordinal : StringComparison.OrdinalIgnoreCase;

                return validationType.ToLower() switch
                {
                    "exact" => actualValue.Equals(expectedValue, comparison),
                    "contains" => actualValue.Contains(expectedValue, comparison),
                    "startswith" => actualValue.StartsWith(expectedValue, comparison),
                    "endswith" => actualValue.EndsWith(expectedValue, comparison),
                    "regex" => Regex.IsMatch(actualValue, expectedValue),
                    "notempty" => !string.IsNullOrWhiteSpace(actualValue),
                    "isempty" => string.IsNullOrWhiteSpace(actualValue),
                    "isnumeric" => double.TryParse(actualValue, out _),
                    "isdate" => DateTime.TryParse(actualValue, out _),
                    "isurl" => Uri.TryCreate(actualValue, UriKind.Absolute, out _),
                    "isemail" => ValidateEmail(actualValue),
                    "oneof" => ValidateOneOf(actualValue, expectedValue, comparison),
                    "length" => int.TryParse(expectedValue, out var len) && actualValue.Length == len,
                    "minlength" => int.TryParse(expectedValue, out var minLen) && actualValue.Length >= minLen,
                    "maxlength" => int.TryParse(expectedValue, out var maxLen) && actualValue.Length <= maxLen,
                    _ => throw new NotSupportedException($"Validation type '{validationType}' is not supported")
                };
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Cell validation error for type '{validationType}': {ex.Message}");
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

        private bool ValidateOneOf(string text, string expectedValues, StringComparison comparison)
        {
            var values = expectedValues.Split(',').Select(v => v.Trim());
            return values.Any(value => text.Equals(value, comparison));
        }

        private bool HasExactValidations(ValidationConfig validation)
        {
            return validation.CellValidations?.Any(cv =>
                cv.ValidationType.Equals("exact", StringComparison.OrdinalIgnoreCase)) ?? false;
        }

        private string GenerateSuccessRemarks(ValidationResult validationResult, GridResult gridResult)
        {
            if (validationResult.WarningIssues > 0)
            {
                return $"Grid validated with {validationResult.WarningIssues} warnings. {gridResult.TotalRows} rows processed.";
            }
            else
            {
                return $"All validations passed. {gridResult.TotalRows} rows verified successfully.";
            }
        }

        private string GenerateComplexFailureRemarks(ValidationResult validationResult, GridResult gridResult)
        {
            var failureDetails = new List<string>();

            var issuesByRow = validationResult.Issues
                .Where(i => i.Row >= 0)
                .GroupBy(i => i.Row)
                .Take(3)
                .ToList();

            foreach (var rowGroup in issuesByRow)
            {
                var rowIndex = rowGroup.Key;
                var issues = rowGroup.ToList();
                var firstIssue = issues.First();

                var expectedValue = GetExpectedValueFromIssue(firstIssue);
                var actualValue = firstIssue.CellValue;
                var columnIndex = firstIssue.Column;

                var rowData = GetCleanRowData(gridResult, rowIndex);
                var rowText = string.Join(" | ", rowData);

                failureDetails.Add($"Row {rowIndex + 1} Col {columnIndex}: Expected='{expectedValue}' Actual='{actualValue}' [{rowText}]");
            }

            if (failureDetails.Any())
            {
                var totalIssues = validationResult.CriticalIssues + validationResult.WarningIssues;
                var moreText = totalIssues > failureDetails.Count ? $" (+{totalIssues - failureDetails.Count} more)" : "";
                return string.Join(" | ", failureDetails) + moreText;
            }

            return $"Grid validation failed: {validationResult.CriticalIssues} critical, {validationResult.WarningIssues} warning issues";
        }

        private string GetExpectedValueFromIssue(ValidationIssue issue)
        {
            var message = issue.Message;
            if (message.Contains("Expected: '"))
            {
                var start = message.IndexOf("Expected: '") + "Expected: '".Length;
                var end = message.IndexOf("'", start);
                if (end > start)
                {
                    return message.Substring(start, end - start);
                }
            }
            return "?";
        }

        private void LogGridDataToFile(GridResult gridResult)
        {
            try
            {
                Logger?.LogToFile("=== GRID DATA START ===");

                if (gridResult.Headers.Any())
                {
                    Logger?.LogToFile($"Headers: {string.Join(" | ", gridResult.Headers)}");
                    Logger?.LogToFile(new string('-', 60));
                }

                for (int i = 0; i < gridResult.Rows.Count; i++)
                {
                    var cleanRow = GetCleanRowData(gridResult, i);
                    var rowText = string.Join(" | ", cleanRow);
                    Logger?.LogToFile($"Row {i + 1}: {rowText}");
                }

                Logger?.LogToFile($"=== GRID DATA END ({gridResult.TotalRows} rows) ===");
            }
            catch (Exception ex)
            {
                Logger?.LogToFile($"Error logging grid data: {ex.Message}");
            }
        }

        public override bool ValidateCommand(Command command)
        {
            return base.ValidateCommand(command) &&
                   (!string.IsNullOrEmpty(command.AutomationId) || !string.IsNullOrEmpty(command.Name));
        }
    }
}

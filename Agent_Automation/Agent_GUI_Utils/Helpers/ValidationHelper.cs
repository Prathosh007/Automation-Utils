using GuiAgentUtils.Models;
using System.Text.RegularExpressions;
using System.Globalization;
using System.Text.Json;

namespace GuiAgentUtils.Helpers
{
    /// <summary>
    /// Generic grid validation helper with cell-specific validation
    /// </summary>
    public static class ValidationHelper
    {
        /// <summary>
        /// Main method to validate grid results against configuration
        /// </summary>
        public static ValidationResult ValidateGrid(GridResult gridResult, ValidationConfig config)
        {
            var result = new ValidationResult();

            try
            {
                // Basic grid validation
                ValidateBasicGrid(gridResult, config, result);

                // Perform cell-specific validations
                if (config.CellValidations?.Any() == true)
                {
                    ValidateGridCells(gridResult, config, result);
                }

                // Generate final result
                GenerateFinalValidationResult(gridResult, config, result);
            }
            catch (Exception ex)
            {
                result.IsValid = false;
                result.Message = $"Validation error: {ex.Message}";
                result.AddIssue(IssueSeverity.Critical, $"Validation process failed: {ex.Message}");
            }

            return result;
        }

        /// <summary>
        /// Validate basic grid properties
        /// </summary>
        private static void ValidateBasicGrid(GridResult gridResult, ValidationConfig config, ValidationResult result)
        {
            // Check if grid reading was successful
            if (!gridResult.Success)
            {
                result.IsValid = false;
                result.AddIssue(IssueSeverity.Critical, $"Grid reading failed: {gridResult.ErrorMessage}");
                return;
            }

            // Check for empty results
            if (gridResult.IsEmpty && !config.AllowEmptyResults)
            {
                result.AddIssue(IssueSeverity.Critical, "Grid is empty but empty results are not allowed");
            }

            // Validate row counts
            if (config.ExpectedRowCount.HasValue && gridResult.TotalRows != config.ExpectedRowCount.Value)
            {
                result.AddIssue(IssueSeverity.Critical,
                    $"Expected {config.ExpectedRowCount} rows, got {gridResult.TotalRows}");
            }

            if (config.MinRowCount.HasValue && gridResult.TotalRows < config.MinRowCount.Value)
            {
                result.AddIssue(IssueSeverity.Critical,
                    $"Expected at least {config.MinRowCount} rows, got {gridResult.TotalRows}");
            }

            if (config.MaxRowCount.HasValue && gridResult.TotalRows > config.MaxRowCount.Value)
            {
                result.AddIssue(IssueSeverity.Warning,
                    $"Expected at most {config.MaxRowCount} rows, got {gridResult.TotalRows}");
            }

            // Validate column counts
            if (config.ExpectedColumnCount.HasValue && gridResult.TotalColumns != config.ExpectedColumnCount.Value)
            {
                result.AddIssue(IssueSeverity.Critical,
                    $"Expected {config.ExpectedColumnCount} columns, got {gridResult.TotalColumns}");
            }
        }

        /// <summary>
        /// Perform cell-specific validations
        /// </summary>
        private static void ValidateGridCells(GridResult gridResult, ValidationConfig config, ValidationResult result)
        {
            foreach (var cellValidation in config.CellValidations)
            {
                try
                {
                    ValidateSingleCell(gridResult, cellValidation, result);

                    // Stop validation if critical failure and not continuing on failure
                    if (!config.ContinueOnFailure && !cellValidation.ContinueOnFailure &&
                        result.Issues.Any(i => i.Severity == IssueSeverity.Critical))
                    {
                        break;
                    }
                }
                catch (Exception ex)
                {
                    result.AddIssue(IssueSeverity.Critical,
                        $"Error validating cell rule '{cellValidation.Description}': {ex.Message}");
                }
            }
        }

        /// <summary>
        /// Validate a single cell validation rule
        /// </summary>
        private static void ValidateSingleCell(GridResult gridResult, CellValidation validation, ValidationResult result)
        {
            var targetRows = GetTargetRows(validation.Row, gridResult.TotalRows);
            var targetColumns = GetTargetColumns(validation, gridResult);

            if (!targetColumns.Any())
            {
                result.AddIssue(IssueSeverity.Warning,
                    $"No matching columns found for validation: {validation.Description}");
                return;
            }

            foreach (var row in targetRows)
            {
                foreach (var col in targetColumns)
                {
                    if (IsValidCellPosition(row, col, gridResult))
                    {
                        var cellValue = gridResult.Rows[row][col];
                        var isValid = ValidateCellValue(cellValue, validation);

                        if (!isValid)
                        {
                            var severity = validation.ContinueOnFailure ? IssueSeverity.Warning : IssueSeverity.Critical;
                            var message = BuildValidationMessage(validation, cellValue);
                            result.AddIssue(severity, message, row, col, cellValue);
                        }
                    }
                }
            }
        }

        /// <summary>
        /// Get target rows for validation
        /// </summary>
        private static IEnumerable<int> GetTargetRows(int row, int totalRows)
        {
            if (row == -1)
                return Enumerable.Range(0, totalRows);

            if (row >= 0 && row < totalRows)
                return new[] { row };

            return Enumerable.Empty<int>();
        }

        /// <summary>
        /// Get target columns for validation
        /// </summary>
        private static IEnumerable<int> GetTargetColumns(CellValidation validation, GridResult gridResult)
        {
            // Use specific column index
            if (validation.Column != -1)
            {
                if (validation.Column >= 0 && validation.Column < gridResult.TotalColumns)
                    return new[] { validation.Column };
                else
                    return Enumerable.Empty<int>();
            }

            // Use header name to find column
            if (!string.IsNullOrEmpty(validation.HeaderName))
            {
                var headerIndex = gridResult.Headers.FindIndex(h =>
                    h.Equals(validation.HeaderName, StringComparison.OrdinalIgnoreCase));

                if (headerIndex >= 0)
                    return new[] { headerIndex };
                else
                    return Enumerable.Empty<int>();
            }

            // All columns
            return Enumerable.Range(0, gridResult.TotalColumns);
        }

        /// <summary>
        /// Check if cell position is valid
        /// </summary>
        private static bool IsValidCellPosition(int row, int col, GridResult gridResult)
        {
            return row >= 0 && row < gridResult.TotalRows &&
                   col >= 0 && col < gridResult.Rows[row].Count;
        }

        /// <summary>
        /// Validate individual cell value against rule
        /// </summary>
        private static bool ValidateCellValue(string cellValue, CellValidation validation)
        {
            try
            {
                var comparison = validation.CaseSensitive ?
                    StringComparison.Ordinal : StringComparison.OrdinalIgnoreCase;

                return validation.ValidationType.ToLower() switch
                {
                    ValidationTypes.Exact =>
                        cellValue.Equals(validation.ExpectedValue?.ToString(), comparison),

                    ValidationTypes.Contains =>
                        cellValue.Contains(validation.ExpectedValue?.ToString() ?? "", comparison),

                    ValidationTypes.StartsWith =>
                        cellValue.StartsWith(validation.ExpectedValue?.ToString() ?? "", comparison),

                    ValidationTypes.EndsWith =>
                        cellValue.EndsWith(validation.ExpectedValue?.ToString() ?? "", comparison),

                    ValidationTypes.Regex =>
                        ValidateRegex(cellValue, validation.ExpectedValue?.ToString()),

                    ValidationTypes.OneOf =>
                        ValidateOneOf(cellValue, validation.ExpectedValue, comparison),

                    ValidationTypes.NotEmpty =>
                        !string.IsNullOrWhiteSpace(cellValue),

                    ValidationTypes.IsEmpty =>
                        string.IsNullOrWhiteSpace(cellValue),

                    ValidationTypes.NumericLessThan =>
                        ValidateNumericComparison(cellValue, validation.ExpectedValue, (a, b) => a < b),

                    ValidationTypes.NumericGreaterThan =>
                        ValidateNumericComparison(cellValue, validation.ExpectedValue, (a, b) => a > b),

                    ValidationTypes.NumericEquals =>
                        ValidateNumericComparison(cellValue, validation.ExpectedValue, (a, b) => Math.Abs(a - b) < 0.001),

                    ValidationTypes.NumericBetween =>
                        ValidateNumericBetween(cellValue, validation.ExpectedValue),

                    ValidationTypes.Length =>
                        ValidateLengthEquals(cellValue, validation.ExpectedValue),

                    ValidationTypes.MinLength =>
                        ValidateMinLength(cellValue, validation.ExpectedValue),

                    ValidationTypes.MaxLength =>
                        ValidateMaxLength(cellValue, validation.ExpectedValue),

                    ValidationTypes.IsNumeric =>
                        double.TryParse(cellValue, out _),

                    ValidationTypes.IsDate =>
                        DateTime.TryParse(cellValue, out _),

                    ValidationTypes.IsUrl =>
                        Uri.TryCreate(cellValue, UriKind.Absolute, out _),

                    ValidationTypes.IsEmail =>
                        ValidateEmail(cellValue),

                    _ => throw new NotSupportedException($"Validation type '{validation.ValidationType}' is not supported")
                };
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Validation error for type '{validation.ValidationType}': {ex.Message}");
                return false;
            }
        }

        #region Specific Validation Methods

        private static bool ValidateRegex(string cellValue, string? pattern)
        {
            if (string.IsNullOrEmpty(pattern)) return false;
            return Regex.IsMatch(cellValue, pattern);
        }

        private static bool ValidateOneOf(string cellValue, object? expectedValue, StringComparison comparison)
        {
            if (expectedValue == null) return false;

            // Handle JSON array
            if (expectedValue is JsonElement jsonElement && jsonElement.ValueKind == JsonValueKind.Array)
            {
                return jsonElement.EnumerateArray()
                    .Any(item => cellValue.Equals(item.GetString(), comparison));
            }

            // Handle string array
            if (expectedValue is string[] stringArray)
            {
                return stringArray.Any(item => cellValue.Equals(item, comparison));
            }

            // Handle comma-separated string
            if (expectedValue is string stringValue)
            {
                var values = stringValue.Split(',').Select(v => v.Trim());
                return values.Any(item => cellValue.Equals(item, comparison));
            }

            return false;
        }

        private static bool ValidateNumericComparison(string cellValue, object? expectedValue, Func<double, double, bool> comparer)
        {
            if (!double.TryParse(cellValue, out var cellNumeric)) return false;
            if (!double.TryParse(expectedValue?.ToString(), out var expectedNumeric)) return false;
            return comparer(cellNumeric, expectedNumeric);
        }

        private static bool ValidateNumericBetween(string cellValue, object? expectedValue)
        {
            if (!double.TryParse(cellValue, out var cellNumeric)) return false;

            // Expected format: "min,max"
            var rangeStr = expectedValue?.ToString();
            if (string.IsNullOrEmpty(rangeStr)) return false;

            var parts = rangeStr.Split(',');
            if (parts.Length != 2) return false;

            if (!double.TryParse(parts[0].Trim(), out var min)) return false;
            if (!double.TryParse(parts[1].Trim(), out var max)) return false;

            return cellNumeric >= min && cellNumeric <= max;
        }

        private static bool ValidateLengthEquals(string cellValue, object? expectedValue)
        {
            if (!int.TryParse(expectedValue?.ToString(), out var expectedLength)) return false;
            return cellValue.Length == expectedLength;
        }

        private static bool ValidateMinLength(string cellValue, object? expectedValue)
        {
            if (!int.TryParse(expectedValue?.ToString(), out var minLength)) return false;
            return cellValue.Length >= minLength;
        }

        private static bool ValidateMaxLength(string cellValue, object? expectedValue)
        {
            if (!int.TryParse(expectedValue?.ToString(), out var maxLength)) return false;
            return cellValue.Length <= maxLength;
        }

        private static bool ValidateEmail(string cellValue)
        {
            try
            {
                var emailRegex = new Regex(@"^[^@\s]+@[^@\s]+\.[^@\s]+$", RegexOptions.IgnoreCase);
                return emailRegex.IsMatch(cellValue);
            }
            catch
            {
                return false;
            }
        }

        #endregion

        /// <summary>
        /// Build validation error message
        /// </summary>
        private static string BuildValidationMessage(CellValidation validation, string actualValue)
        {
            var description = string.IsNullOrEmpty(validation.Description) ?
                "Cell validation" : validation.Description;

            return validation.ValidationType.ToLower() switch
            {
                ValidationTypes.Exact => $"{description} - Expected: '{validation.ExpectedValue}', Got: '{actualValue}'",
                ValidationTypes.Contains => $"{description} - Expected to contain: '{validation.ExpectedValue}', Got: '{actualValue}'",
                ValidationTypes.OneOf => $"{description} - Expected one of: {validation.ExpectedValue}, Got: '{actualValue}'",
                ValidationTypes.NotEmpty => $"{description} - Expected non-empty value, Got: '{actualValue}'",
                ValidationTypes.IsEmpty => $"{description} - Expected empty value, Got: '{actualValue}'",
                ValidationTypes.NumericLessThan => $"{description} - Expected < {validation.ExpectedValue}, Got: '{actualValue}'",
                ValidationTypes.NumericGreaterThan => $"{description} - Expected > {validation.ExpectedValue}, Got: '{actualValue}'",
                ValidationTypes.Regex => $"{description} - Pattern '{validation.ExpectedValue}' not matched, Got: '{actualValue}'",
                _ => $"{description} - Validation failed for type '{validation.ValidationType}', Got: '{actualValue}'"
            };
        }

        /// <summary>
        /// Generate final validation result
        /// </summary>
        private static void GenerateFinalValidationResult(GridResult gridResult, ValidationConfig config, ValidationResult result)
        {
            result.IsValid = result.Issues.All(i => i.Severity != IssueSeverity.Critical);

            if (result.IsValid)
            {
                result.Message = $"✅ All validations passed. {gridResult.TotalRows} rows, {gridResult.TotalColumns} columns processed.";
                if (result.WarningIssues > 0)
                    result.Message += $" ({result.WarningIssues} warnings)";
            }
            else
            {
                var criticalCount = result.Issues.Count(i => i.Severity == IssueSeverity.Critical);
                result.Message = $"❌ {criticalCount} critical validation(s) failed";

                if (result.WarningIssues > 0)
                    result.Message += $", {result.WarningIssues} warnings";
            }

            // Generate failure details
            if (config.ShowFailureDetails && result.Issues.Any())
            {
                result.FailureDetails = string.Join("\n", result.Issues.Select(i => i.ToString()));

                // Add failed entries for backward compatibility
                result.FailedEntries = result.Issues
                    .Where(i => i.Row >= 0)
                    .Select(i => $"Row {i.Row + 1}: {string.Join(" | ", gridResult.GetRow(i.Row))}")
                    .Distinct()
                    .ToList();
            }
        }

        /// <summary>
        /// Print grid data in clean readable format to console
        /// </summary>
        public static void PrintGridToConsole(GridResult gridResult, bool showRowNumbers = true, int maxColumnWidth = 20)
        {
            if (!gridResult.Success)
            {
                Console.WriteLine($"[GRID ERROR] {gridResult.ErrorMessage}");
                return;
            }

            if (gridResult.IsEmpty)
            {
                Console.WriteLine("[GRID] No data to display - grid is empty");
                return;
            }

            try
            {
                Console.WriteLine("\n" + new string('=', 80));
                Console.WriteLine($"GRID DATA: {gridResult.TotalRows} rows × {gridResult.TotalColumns} columns");
                Console.WriteLine(new string('=', 80));

                // Calculate column widths
                var columnWidths = CalculateColumnWidths(gridResult, maxColumnWidth, showRowNumbers);

                // Print headers
                PrintHeaders(gridResult, columnWidths, showRowNumbers);

                // Print separator line
                PrintSeparator(columnWidths, showRowNumbers);

                // Print data rows
                PrintDataRows(gridResult, columnWidths, showRowNumbers);

                Console.WriteLine(new string('=', 80));
                Console.WriteLine($"Total: {gridResult.TotalRows} rows, {gridResult.TotalColumns} columns");

                if (gridResult.HasFailures)
                {
                    Console.WriteLine($"⚠️  Failed rows: {string.Join(", ", gridResult.FailedRows.Select(r => r + 1))}");
                }

                Console.WriteLine(new string('=', 80) + "\n");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Failed to print grid: {ex.Message}");
            }
        }

        /// <summary>
        /// Calculate optimal column widths for display
        /// </summary>
        private static List<int> CalculateColumnWidths(GridResult gridResult, int maxWidth, bool showRowNumbers)
        {
            var widths = new List<int>();

            // Add row number column width if needed
            if (showRowNumbers)
            {
                var rowNumWidth = Math.Max(3, gridResult.TotalRows.ToString().Length + 1);
                widths.Add(Math.Min(rowNumWidth, 6)); // Max 6 chars for row numbers
            }

            // Calculate width for each data column
            for (int col = 0; col < gridResult.TotalColumns; col++)
            {
                var maxColWidth = 3; // Minimum width

                // Check header width
                if (col < gridResult.Headers.Count)
                {
                    maxColWidth = Math.Max(maxColWidth, gridResult.Headers[col].Length);
                }

                // Check all cell values in this column
                for (int row = 0; row < gridResult.TotalRows; row++)
                {
                    if (col < gridResult.Rows[row].Count)
                    {
                        var cellValue = gridResult.Rows[row][col] ?? "";
                        maxColWidth = Math.Max(maxColWidth, cellValue.Length);
                    }
                }

                // Apply maximum width limit
                widths.Add(Math.Min(maxColWidth, maxWidth));
            }

            return widths;
        }

        /// <summary>
        /// Print table headers
        /// </summary>
        private static void PrintHeaders(GridResult gridResult, List<int> columnWidths, bool showRowNumbers)
        {
            var headerLine = "";
            var widthIndex = 0;

            // Row number header
            if (showRowNumbers)
            {
                headerLine += "Row".PadRight(columnWidths[widthIndex]) + " │ ";
                widthIndex++;
            }

            // Data column headers
            for (int col = 0; col < gridResult.TotalColumns; col++)
            {
                var headerText = col < gridResult.Headers.Count ? gridResult.Headers[col] : $"Col{col + 1}";
                var truncatedHeader = TruncateText(headerText, columnWidths[widthIndex]);
                headerLine += truncatedHeader.PadRight(columnWidths[widthIndex]);

                if (col < gridResult.TotalColumns - 1)
                    headerLine += " │ ";

                widthIndex++;
            }

            Console.WriteLine(headerLine);
        }

        /// <summary>
        /// Print separator line
        /// </summary>
        private static void PrintSeparator(List<int> columnWidths, bool showRowNumbers)
        {
            var separatorLine = "";

            for (int i = 0; i < columnWidths.Count; i++)
            {
                separatorLine += new string('─', columnWidths[i]);
                if (i < columnWidths.Count - 1)
                    separatorLine += "─┼─";
            }

            Console.WriteLine(separatorLine);
        }

        /// <summary>
        /// Print data rows
        /// </summary>
        private static void PrintDataRows(GridResult gridResult, List<int> columnWidths, bool showRowNumbers)
        {
            for (int row = 0; row < gridResult.TotalRows; row++)
            {
                var rowLine = "";
                var widthIndex = 0;

                // Row number
                if (showRowNumbers)
                {
                    var rowNum = (row + 1).ToString();
                    rowLine += rowNum.PadRight(columnWidths[widthIndex]) + " │ ";
                    widthIndex++;
                }

                // Data columns
                for (int col = 0; col < gridResult.TotalColumns; col++)
                {
                    var cellValue = "";
                    if (col < gridResult.Rows[row].Count)
                    {
                        cellValue = gridResult.Rows[row][col] ?? "";
                    }

                    var truncatedValue = TruncateText(cellValue, columnWidths[widthIndex]);

                    // Highlight failed cells
                    if (gridResult.FailedCells.ContainsKey(row) && gridResult.FailedCells[row].Contains(col))
                    {
                        truncatedValue = $"❌{truncatedValue}";
                    }

                    rowLine += truncatedValue.PadRight(columnWidths[widthIndex]);

                    if (col < gridResult.TotalColumns - 1)
                        rowLine += " │ ";

                    widthIndex++;
                }

                // Mark failed rows
                if (gridResult.FailedRows.Contains(row))
                {
                    rowLine += " ⚠️";
                }

                Console.WriteLine(rowLine);
            }
        }

        /// <summary>
        /// Truncate text to fit column width
        /// </summary>
        private static string TruncateText(string text, int maxWidth)
        {
            if (string.IsNullOrEmpty(text))
                return "";

            if (text.Length <= maxWidth)
                return text;

            return maxWidth > 3 ? text.Substring(0, maxWidth - 3) + "..." : text.Substring(0, maxWidth);
        }

        /// <summary>
        /// Print grid in simple format (no table formatting)
        /// </summary>
        public static void PrintGridSimple(GridResult gridResult)
        {
            if (!gridResult.Success)
            {
                Console.WriteLine($"[GRID ERROR] {gridResult.ErrorMessage}");
                return;
            }

            Console.WriteLine($"\n[GRID DATA] {gridResult.TotalRows} rows × {gridResult.TotalColumns} columns");

            // Print headers
            if (gridResult.Headers.Any())
            {
                Console.WriteLine($"Headers: {string.Join(" | ", gridResult.Headers)}");
                Console.WriteLine(new string('-', 50));
            }

            // Print rows
            for (int i = 0; i < gridResult.Rows.Count; i++)
            {
                var rowPrefix = gridResult.FailedRows.Contains(i) ? "❌" : "  ";
                Console.WriteLine($"{rowPrefix}Row {i + 1}: {string.Join(" | ", gridResult.Rows[i])}");
            }

            Console.WriteLine();
        }

        /// <summary>
        /// Get summary statistics for the grid
        /// </summary>
        public static Dictionary<string, object> GetGridStatistics(GridResult gridResult)
        {
            var stats = new Dictionary<string, object>
            {
                ["TotalRows"] = gridResult.TotalRows,
                ["TotalColumns"] = gridResult.TotalColumns,
                ["TotalCells"] = gridResult.TotalRows * gridResult.TotalColumns,
                ["HasFailures"] = gridResult.HasFailures,
                ["IsEmpty"] = gridResult.IsEmpty,
                ["CapturedAt"] = gridResult.CapturedAt,
                ["Success"] = gridResult.Success
            };

            return stats;
        }

        /// <summary>
        /// Quick helper method for simple validations
        /// </summary>
        public static bool ValidateSingleCell(GridResult gridResult, int row, int column, string expectedValue, string validationType = ValidationTypes.Exact)
        {
            if (!IsValidCellPosition(row, column, gridResult))
                return false;

            var cellValue = gridResult.Rows[row][column];
            var validation = new CellValidation
            {
                Row = row,
                Column = column,
                ExpectedValue = expectedValue,
                ValidationType = validationType
            };

            return ValidateCellValue(cellValue, validation);
        }
    }
}
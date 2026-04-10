using System.Text.Json.Serialization;

namespace GuiAgentUtils.Models
{
    /// <summary>
    /// Configuration for grid validation rules - Cell-specific validation approach
    /// </summary>
    public class ValidationConfig
    {
        /// <summary>
        /// List of specific cell validations to perform
        /// </summary>
        public List<CellValidation> CellValidations { get; set; } = new();

        /// <summary>
        /// Whether to allow empty grid results
        /// </summary>
        public bool AllowEmptyResults { get; set; } = true;

        /// <summary>
        /// Whether to continue validation after first failure
        /// </summary>
        public bool ContinueOnFailure { get; set; } = true;

        /// <summary>
        /// Whether to show detailed failure information
        /// </summary>
        public bool ShowFailureDetails { get; set; } = true;

        /// <summary>
        /// Expected minimum number of rows
        /// </summary>
        public int? MinRowCount { get; set; }

        /// <summary>
        /// Expected maximum number of rows
        /// </summary>
        public int? MaxRowCount { get; set; }

        /// <summary>
        /// Expected exact number of rows
        /// </summary>
        public int? ExpectedRowCount { get; set; }

        /// <summary>
        /// Expected exact number of columns
        /// </summary>
        public int? ExpectedColumnCount { get; set; }
    }

    /// <summary>
    /// Individual cell validation rule
    /// </summary>
    public class CellValidation
    {
        /// <summary>
        /// Target row index (-1 for all rows)
        /// </summary>
        public int Row { get; set; } = -1;

        /// <summary>
        /// Target column index (-1 for all columns)
        /// </summary>
        public int Column { get; set; } = -1;

        /// <summary>
        /// Target column by header name (alternative to Column index)
        /// </summary>
        public string? HeaderName { get; set; }

        /// <summary>
        /// Expected value - can be string, array of strings, or number
        /// </summary>
        public object? ExpectedValue { get; set; }

        /// <summary>
        /// Type of validation to perform
        /// </summary>
        public string ValidationType { get; set; } = "exact";

        /// <summary>
        /// Description of this validation rule
        /// </summary>
        public string Description { get; set; } = "";

        /// <summary>
        /// Whether to continue if this validation fails
        /// </summary>
        public bool ContinueOnFailure { get; set; } = true;

        /// <summary>
        /// Case sensitive comparison (for string validations)
        /// </summary>
        public bool CaseSensitive { get; set; } = false;
    }

    /// <summary>
    /// Validation result with detailed information
    /// </summary>
    public class ValidationResult
    {
        public bool IsValid { get; set; } = true;
        public string Message { get; set; } = string.Empty;
        public string? FailureDetails { get; set; }
        public List<string> FailedEntries { get; set; } = new();
        public List<ValidationIssue> Issues { get; set; } = new();

        // Statistics
        public int TotalIssues => Issues.Count;
        public int CriticalIssues => Issues.Count(i => i.Severity == IssueSeverity.Critical);
        public int WarningIssues => Issues.Count(i => i.Severity == IssueSeverity.Warning);
        public int InfoIssues => Issues.Count(i => i.Severity == IssueSeverity.Info);

        public void AddIssue(IssueSeverity severity, string message, int row = -1, int column = -1, string? cellValue = null)
        {
            Issues.Add(new ValidationIssue
            {
                Severity = severity,
                Message = message,
                Row = row,
                Column = column,
                CellValue = cellValue
            });
        }
    }

    /// <summary>
    /// Individual validation issue
    /// </summary>
    public class ValidationIssue
    {
        public IssueSeverity Severity { get; set; }
        public string Message { get; set; } = string.Empty;
        public int Row { get; set; } = -1;
        public int Column { get; set; } = -1;
        public string? CellValue { get; set; }
        public DateTime Timestamp { get; set; } = DateTime.Now;

        public override string ToString()
        {
            var location = Row >= 0 && Column >= 0 ? $"[Row {Row + 1}, Col {Column + 1}]" :
                          Row >= 0 ? $"[Row {Row + 1}]" :
                          Column >= 0 ? $"[Col {Column + 1}]" : "";

            var cellInfo = !string.IsNullOrEmpty(CellValue) ? $" Value: '{CellValue}'" : "";

            return $"{Severity}: {Message} {location}{cellInfo}";
        }
    }

    /// <summary>
    /// Issue severity levels
    /// </summary>
    public enum IssueSeverity
    {
        Info,
        Warning,
        Critical
    }

    /// <summary>
    /// Supported validation types
    /// </summary>
    public static class ValidationTypes
    {
        public const string Exact = "exact";
        public const string Contains = "contains";
        public const string Regex = "regex";
        public const string OneOf = "oneOf";
        public const string NotEmpty = "notEmpty";
        public const string IsEmpty = "isEmpty";
        public const string NumericLessThan = "numeric_less_than";
        public const string NumericGreaterThan = "numeric_greater_than";
        public const string NumericEquals = "numeric_equals";
        public const string NumericBetween = "numeric_between";
        public const string StartsWith = "startsWith";
        public const string EndsWith = "endsWith";
        public const string Length = "length";
        public const string MinLength = "min_length";
        public const string MaxLength = "max_length";
        public const string IsNumeric = "is_numeric";
        public const string IsDate = "is_date";
        public const string IsUrl = "is_url";
        public const string IsEmail = "is_email";
    }
}
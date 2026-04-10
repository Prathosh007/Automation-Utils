using System.Text.Json.Serialization;

namespace GuiAgentUtils.Models  // Fixed namespace
{
    /// <summary>
    /// Generic grid result that works with any number of rows and columns
    /// </summary>
    public class GridResult
    {
        public bool Success { get; set; }
        public string? ErrorMessage { get; set; }

        // Dynamic headers - auto-detected from grid
        public List<string> Headers { get; set; } = new();

        // Dynamic rows - each row contains cells for all columns
        public List<List<string>> Rows { get; set; } = new();

        // Grid metadata
        public int TotalRows { get; set; }
        public int TotalColumns { get; set; }

        // Validation results
        public int FailureCount { get; set; }
        public List<int> FailedRows { get; set; } = new();
        public Dictionary<int, List<int>> FailedCells { get; set; } = new(); // Row -> Column indexes

        // Additional metadata
        public DateTime CapturedAt { get; set; } = DateTime.UtcNow;
        public string? GridName { get; set; }
        public string? GridAutomationId { get; set; }

        [JsonIgnore]
        public bool HasFailures => FailureCount > 0;

        [JsonIgnore]
        public bool IsEmpty => TotalRows == 0;

        /// <summary>
        /// Get cell value by row and column index
        /// </summary>
        public string GetCell(int row, int column)
        {
            if (row >= 0 && row < Rows.Count && column >= 0 && column < Rows[row].Count)
                return Rows[row][column];
            return string.Empty;
        }

        /// <summary>
        /// Get entire row as string array
        /// </summary>
        public string[] GetRow(int rowIndex)
        {
            if (rowIndex >= 0 && rowIndex < Rows.Count)
                return Rows[rowIndex].ToArray();
            return Array.Empty<string>();
        }

        /// <summary>
        /// Get column values for all rows
        /// </summary>
        public List<string> GetColumn(int columnIndex)
        {
            var columnData = new List<string>();
            foreach (var row in Rows)
            {
                if (columnIndex < row.Count)
                    columnData.Add(row[columnIndex]);
                else
                    columnData.Add(string.Empty);
            }
            return columnData;
        }

        /// <summary>
        /// Convert to formatted string for display
        /// </summary>
        public string ToFormattedString()
        {
            if (!Success)
                return $"Grid Error: {ErrorMessage}";
            if (IsEmpty)
                return "Grid is empty";
            var output = new System.Text.StringBuilder();

            // Headers
            if (Headers.Any())
            {
                output.AppendLine(string.Join(" | ", Headers));
                output.AppendLine(new string('-', Headers.Sum(h => h.Length) + (Headers.Count - 1) * 3));
            }

            // Rows
            for (int i = 0; i < Rows.Count; i++)
            {
                var row = Rows[i];
                var rowText = string.Join(" | ", row);

                // Mark failed rows
                if (FailedRows.Contains(i))
                    rowText += " ❌";

                output.AppendLine(rowText);
            }

            return output.ToString();
        }
    }
}

using FlaUI.Core.Definitions;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace GuiAgentUtils.Models
{
    public class Command
    {
        public string? Id { get; set; }
        public string Action { get; set; } = "";
        public string? AutomationId { get; set; }
        public string? Name { get; set; }
        public string? Text { get; set; }
        public string? ExpectedValue { get; set; }
        public string? ExpectedResult { get; set; }
        public string? Description { get; set; }
        public string? FilePath { get; set; }
        public string? AppName { get; set; }
        public string? ServiceName { get; set; }
        public bool? Checked { get; set; }
        public bool? ForceKill { get; set; }          // For appclose/appcloseall
        public string? WindowClassName { get; set; }  // For appclose (optional)

        private ControlType? _controlType;

        [JsonPropertyName("controlType")]
        public string? ControlTypeString
        {
            get => _controlType?.ToString();
            set
            {
                if (string.IsNullOrWhiteSpace(value))
                {
                    _controlType = null;
                    return;
                }

                if (Enum.TryParse<ControlType>(value, ignoreCase: true, out var result))
                {
                    _controlType = result;
                }
                else
                {
                    Console.WriteLine($"[WARN] Unknown ControlType: '{value}', will be ignored");
                    _controlType = null;
                }
            }
        }

        [JsonIgnore]
        public ControlType? ControlType
        {
            get => _controlType;
            set => _controlType = value;
        }

        public int? Timeout { get; set; }
        public int? WaitAfter { get; set; }
        public bool MatchPartial { get; set; } = false;
        public bool ContinueOnFailure { get; set; } = false;
        public bool TakeScreenshotOnFailure { get; set; } = true;

        // Complex validation (old format - for backward compatibility)
        public ValidationConfig? Validation { get; set; }

        // Direct cell validations array (no wrapper)
        public List<CellValidation>? CellValidations { get; set; }

        // Simple validation properties (for single cell)
        public string? ValidationType { get; set; }
        public bool CaseSensitive { get; set; } = false;
        public int? Row { get; set; }
        public int? Column { get; set; }

        // Column-wise validation - use custom converter
        [JsonConverter(typeof(ColumnValidationsConverter))]
        public Dictionary<string, List<string>>? ColumnValidations { get; set; }

        // NEW: Key-Value pair validation
        [JsonConverter(typeof(KeyValuePairsConverter))]
        public Dictionary<string, string>? KeyValuePairs { get; set; }

        // NEW: Columns for key-value validation
        public int? KeyColumn { get; set; }
        public int? ValueColumn { get; set; }

        // Modal dialog specific properties
        public string? ButtonName { get; set; }
        public string? ButtonAutomationId { get; set; }
        public string? TextBoxAutomationId { get; set; }
        public string? DialogTitle { get; set; }

        // Window targeting properties
        public string? WindowName { get; set; }
        public string? WindowAutomationId { get; set; }
        public string? WindowTitle { get; set; }
        public bool WindowPartialMatch { get; set; } = false;

        [JsonPropertyName("appPath")]
        public string? AppPath { get; set; }

        [JsonPropertyName("processName")]
        public string? ProcessName { get; set; }

        [JsonPropertyName("launchTimeout")]
        public int? LaunchTimeout { get; set; }
    }

    // Custom converter for ColumnValidations
    public class ColumnValidationsConverter : JsonConverter<Dictionary<string, List<string>>?>
    {
        public override Dictionary<string, List<string>>? Read(
            ref Utf8JsonReader reader,
            Type typeToConvert,
            JsonSerializerOptions options)
        {
            if (reader.TokenType == JsonTokenType.Null)
                return null;

            if (reader.TokenType != JsonTokenType.StartObject)
                throw new JsonException("Expected StartObject token");

            var result = new Dictionary<string, List<string>>();

            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndObject)
                    return result;

                // Read property name (column index as string)
                if (reader.TokenType != JsonTokenType.PropertyName)
                    throw new JsonException("Expected PropertyName token");

                string columnKey = reader.GetString() ?? "";

                // Read array of values
                reader.Read();
                if (reader.TokenType != JsonTokenType.StartArray)
                    throw new JsonException("Expected StartArray token");

                var values = new List<string>();
                while (reader.Read())
                {
                    if (reader.TokenType == JsonTokenType.EndArray)
                        break;

                    if (reader.TokenType == JsonTokenType.String)
                    {
                        values.Add(reader.GetString() ?? "");
                    }
                }

                result[columnKey] = values;
            }

            return result;
        }

        public override void Write(
            Utf8JsonWriter writer,
            Dictionary<string, List<string>>? value,
            JsonSerializerOptions options)
        {
            if (value == null)
            {
                writer.WriteNullValue();
                return;
            }

            writer.WriteStartObject();
            foreach (var kvp in value)
            {
                writer.WritePropertyName(kvp.Key);
                writer.WriteStartArray();
                foreach (var item in kvp.Value)
                {
                    writer.WriteStringValue(item);
                }
                writer.WriteEndArray();
            }
            writer.WriteEndObject();
        }
    }

    // NEW: Custom converter for KeyValuePairs
    public class KeyValuePairsConverter : JsonConverter<Dictionary<string, string>?>
    {
        public override Dictionary<string, string>? Read(
            ref Utf8JsonReader reader,
            Type typeToConvert,
            JsonSerializerOptions options)
        {
            if (reader.TokenType == JsonTokenType.Null)
                return null;

            if (reader.TokenType != JsonTokenType.StartObject)
                throw new JsonException("Expected StartObject token");

            var result = new Dictionary<string, string>();

            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndObject)
                    return result;

                // Read property name (the key)
                if (reader.TokenType != JsonTokenType.PropertyName)
                    throw new JsonException("Expected PropertyName token");

                string key = reader.GetString() ?? "";

                // Read the value
                reader.Read();
                if (reader.TokenType == JsonTokenType.String)
                {
                    string value = reader.GetString() ?? "";
                    result[key] = value;
                }
                else if (reader.TokenType == JsonTokenType.Number)
                {
                    result[key] = reader.GetInt32().ToString();
                }
                else if (reader.TokenType == JsonTokenType.True || reader.TokenType == JsonTokenType.False)
                {
                    result[key] = reader.GetBoolean().ToString();
                }
                else
                {
                    result[key] = "";
                }
            }

            return result;
        }

        public override void Write(
            Utf8JsonWriter writer,
            Dictionary<string, string>? value,
            JsonSerializerOptions options)
        {
            if (value == null)
            {
                writer.WriteNullValue();
                return;
            }

            writer.WriteStartObject();
            foreach (var kvp in value)
            {
                writer.WritePropertyName(kvp.Key);
                writer.WriteStringValue(kvp.Value);
            }
            writer.WriteEndObject();
        }
    }
}
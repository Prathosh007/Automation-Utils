using System.Text.Json;
using System.Text.Json.Serialization;
using FlaUI.Core.Definitions;

namespace GuiAgentUtils.Utils
{
    public class ControlTypeJsonConverter : JsonConverter<ControlType?>
    {
        public override ControlType? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        {
            var str = reader.GetString();
            if (Enum.TryParse<ControlType>(str, true, out var result))
            {
                return result;
            }
            return null;
        }

        public override void Write(Utf8JsonWriter writer, ControlType? value, JsonSerializerOptions options)
        {
            writer.WriteStringValue(value?.ToString());
        }
    }
}

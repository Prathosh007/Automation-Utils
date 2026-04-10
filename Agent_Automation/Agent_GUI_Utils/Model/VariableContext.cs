using System.Text.Json;
using System.Text.RegularExpressions;
using GuiAgentUtils.Core;

namespace GuiAgentUtils.Models
{
    /// <summary>
    /// Manages variable storage and substitution for test execution
    /// </summary>
    public class VariableContext
    {
        private readonly Dictionary<string, object> _variables = new();
        private static readonly Regex VariablePattern = new(@"\{\{([^}]+)\}\}", RegexOptions.Compiled);

        /// <summary>
        /// Store a variable value
        /// </summary>
        public void SetVariable(string name, object value)
        {
            _variables[name] = value;
        }

        /// <summary>
        /// Get a variable value
        /// </summary>
        public T? GetVariable<T>(string name)
        {
            if (_variables.TryGetValue(name, out var value))
            {
                if (value is T directValue)
                    return directValue;

                // Try to convert
                try
                {
                    return (T)Convert.ChangeType(value, typeof(T));
                }
                catch
                {
                    return default(T);
                }
            }
            return default(T);
        }

        /// <summary>
        /// Check if a variable exists
        /// </summary>
        public bool HasVariable(string name)
        {
            return _variables.ContainsKey(name);
        }

        /// <summary>
        /// Substitute variables in a string
        /// </summary>
        public string SubstituteVariables(string input)
        {
            if (string.IsNullOrEmpty(input))
                return input;

            return VariablePattern.Replace(input, match =>
            {
                var variableExpression = match.Groups[1].Value;
                return ResolveVariableExpression(variableExpression);
            });
        }

        /// <summary>
        /// Resolve a variable expression like "stepId.property"
        /// </summary>
        private string ResolveVariableExpression(string expression)
        {
            try
            {
                var parts = expression.Split('.');
                if (parts.Length == 1)
                {
                    // Simple variable: {{variableName}}
                    if (_variables.TryGetValue(parts[0], out var value))
                    {
                        return value?.ToString() ?? "";
                    }
                }
                else if (parts.Length == 2)
                {
                    // Property access: {{stepId.property}}
                    var stepId = parts[0];
                    var property = parts[1];

                    if (_variables.TryGetValue(stepId, out var stepResult))
                    {
                        return ExtractProperty(stepResult, property);
                    }
                }

                // Variable not found, return original expression
                return $"{{{{{expression}}}}}";
            }
            catch (Exception ex)
            {
                // If resolution fails, return original expression
                Console.WriteLine($"[WARN] Variable resolution failed for '{expression}': {ex.Message}");
                return $"{{{{{expression}}}}}";
            }
        }

        /// <summary>
        /// Extract property from an object
        /// </summary>
        private string ExtractProperty(object obj, string property)
        {
            if (obj == null)
                return "";

            try
            {
                // Handle any CommandResult object (from any namespace)
                var type = obj.GetType();
                if (type.Name == "CommandResult")
                {
                    return property.ToLower() switch
                    {
                        "data" => GetPropertyValue(obj, "Data")?.ToString() ?? "",
                        "message" => GetPropertyValue(obj, "Message")?.ToString() ?? "",
                        "success" => GetPropertyValue(obj, "Success")?.ToString() ?? "",
                        "duration" => GetPropertyValue(obj, "Duration")?.ToString() ?? "",
                        "commandid" => GetPropertyValue(obj, "CommandId")?.ToString() ?? "",
                        "action" => GetPropertyValue(obj, "Action")?.ToString() ?? "",
                        _ => ""
                    };
                }

                // Handle generic object using reflection
                var propInfo = type.GetProperty(property,
                    System.Reflection.BindingFlags.Public |
                    System.Reflection.BindingFlags.Instance |
                    System.Reflection.BindingFlags.IgnoreCase);

                if (propInfo != null)
                {
                    var value = propInfo.GetValue(obj);
                    return value?.ToString() ?? "";
                }

                // Handle dictionary-like objects
                if (obj is IDictionary<string, object> dict)
                {
                    if (dict.TryGetValue(property, out var dictValue))
                        return dictValue?.ToString() ?? "";
                }

                return "";
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Property extraction failed for '{property}': {ex.Message}");
                return "";
            }
        }

        /// <summary>
        /// Helper to get property value using reflection
        /// </summary>
        private object? GetPropertyValue(object obj, string propertyName)
        {
            try
            {
                var type = obj.GetType();
                var propInfo = type.GetProperty(propertyName,
                    System.Reflection.BindingFlags.Public |
                    System.Reflection.BindingFlags.Instance);
                return propInfo?.GetValue(obj);
            }
            catch
            {
                return null;
            }
        }

        /// <summary>
        /// Get all variables for debugging
        /// </summary>
        public Dictionary<string, object> GetAllVariables()
        {
            return new Dictionary<string, object>(_variables);
        }

        /// <summary>
        /// Clear all variables
        /// </summary>
        public void Clear()
        {
            _variables.Clear();
        }

        /// <summary>
        /// Store result from a command execution - works with any CommandResult type
        /// </summary>
        public void StoreCommandResult(string stepId, object result)
        {
            SetVariable(stepId, result);

            // Also store common properties as direct variables for easier access
            try
            {
                if (result != null)
                {
                    var dataValue = GetPropertyValue(result, "Data");
                    var successValue = GetPropertyValue(result, "Success");
                    var messageValue = GetPropertyValue(result, "Message");

                    if (dataValue != null) SetVariable($"{stepId}.Data", dataValue);
                    if (successValue != null) SetVariable($"{stepId}.Success", successValue);
                    if (messageValue != null) SetVariable($"{stepId}.Message", messageValue);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] Failed to store command result properties: {ex.Message}");
            }
        }
    }
}
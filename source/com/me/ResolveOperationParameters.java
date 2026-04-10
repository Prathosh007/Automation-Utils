package com.me;

import com.me.util.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.me.util.CommonUtill.resolveHome;

public class ResolveOperationParameters {
    private static final Logger LOGGER = LogManager.getLogger(ResolveOperationParameters.class, LogManager.LOG_TYPE.FW);

    // Static inner class for variable management
    public static class VariableManager {
        private static final Map<String, String> variables = new HashMap<>();

        public static void setVariable(String name, String value) {
            variables.put(name, value);
            LOGGER.info("Variable set: " + name + " = " + value);
        }

        public static String getVariable(String name) {
            return variables.get(name);
        }

        public static void clearVariables() {
            variables.clear();
        }
    }

    public static String resolveVariableReferences(String input) {
        if (input == null) return null;
        LOGGER.log(Level.INFO, "Resolving variable references in: " + input);

        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = VariableManager.getVariable(varName);
            if (value != null) {
                LOGGER.info("Resolved variable: " + varName + " = " + value);
            } else {
                LOGGER.warning("Variable not found: " + varName);
            }
            String replacement = value != null ? Matcher.quoteReplacement(value) : matcher.group(0);
            matcher.appendReplacement(result, replacement);
            LOGGER.info("Resolved input: " + result);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolves all variable and path references in operation parameters
     *
     * @param op The operation containing parameters to resolve
     * @return The operation with resolved parameters
     */
    public static Operation resolveOperationParameters(Operation op) {
        LOGGER.info("Resolving parameters for operation: " + (op != null ? op.getOperationType() : "null"));
        if (op == null) {
            LOGGER.warning("Cannot resolve parameters for null operation");
            return null;
        }

        Map<String, String> parameters = op.getParameters();

        if (parameters == null || parameters.isEmpty()) {
            LOGGER.info("No parameters to resolve for operation");
            return op;
        }

        Map<String, String> resolvedParams = new HashMap<>();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            String originalValue = entry.getValue();

            if (originalValue != null) {
                // First resolve any variable references like ${variable}
                String resolvedValue = resolveVariableReferences(originalValue);

                // Then handle path resolution (server_home, etc.)
                String finalValue = resolveHome(resolvedValue);
                resolvedParams.put(paramName, finalValue);
            } else {
                LOGGER.fine("Parameter '" + paramName + "' has null value");
                resolvedParams.put(paramName, null);
            }
        }

        // Replace the parameters with resolved ones
        op.setParameters(resolvedParams);
        return op;
    }
}
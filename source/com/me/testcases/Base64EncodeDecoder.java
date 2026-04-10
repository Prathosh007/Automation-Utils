package com.me.testcases;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Base64;

import com.me.Operation;
import com.me.util.LogManager;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

public class Base64EncodeDecoder {
    private static final Logger LOGGER = LogManager.getLogger(Base64EncodeDecoder.class, LogManager.LOG_TYPE.FW);

    /**
     * Execute a Base64 operation (encode/decode)
     * @param op Operation object with parameters:
     *           - action: "encode" or "decode"
     *           - input: String to encode/decode (optional if file is provided)
     *           - input_file: Path to input file (optional)
     *           - output_file: Path to output file (optional)
     * @return true if operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        String action = op.getParameter("action");
        String input = op.getParameter("input");

        StringBuilder remarks = new StringBuilder();
        remarks.append("Base64 Operation: ").append(action).append("\n");

        try {
            boolean result;
            switch (action == null ? "" : action.toLowerCase()) {
                case "encode":
                    result = handleEncode(input, remarks,op);
                    break;
                case "decode":
                    result = handleDecode(input, remarks,op);
                    break;
                default:
                    remarks.append("Unsupported action: ").append(action);
                    LOGGER.warning("Unsupported action: " + action);
                    op.setRemarks(remarks.toString());
                    return false;
            }
            op.setRemarks(remarks.toString());
            op.setOutputValue(remarks.toString());
            LOGGER.info(remarks.toString());
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in Base64 operation", e);
            remarks.append("Error: ").append(e.getMessage());
            op.setRemarks(remarks.toString());
            return false;
        }
    }

private static boolean handleEncode(String input, StringBuilder remarks, Operation operation) {
    if (input != null) {
        // Encode string
        String encoded = Base64.getEncoder().encodeToString(input.getBytes());
        remarks.append("Encoded string: ").append(encoded);
        saveNote(operation, encoded);
        return true;
    } else {
        remarks.append("No input provided for encoding.");
        LOGGER.severe("No input provided for encoding.");
        return false;
    }
}

private static boolean handleDecode(String input, StringBuilder remarks, Operation operation) {
    if (input == null) {
        remarks.append("No input provided for decoding.");
        return false;
    }
    try {
        byte[] decodedBytes = Base64.getDecoder().decode(input);
        String decoded = new String(decodedBytes);
        saveNote(operation, decoded);
        remarks.append("Decoded string: ").append(decoded);
        return true;
    } catch (IllegalArgumentException e) {
        remarks.append("Invalid Base64 input: ").append(e.getMessage());
        LOGGER.severe("Invalid Base64 input: " + e.getMessage());
        return false;
    }
}
}
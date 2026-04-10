package com.me.api.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.me.util.LogManager;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom serializer for GoatApiResponse to handle various data types,
 * especially to properly handle Gson JsonObject and JsonElement types
 */
public class GoatApiResponseSerializer extends JsonSerializer<GoatApiResponse<?>> {
    
    private static final Logger LOGGER = LogManager.getLogger(GoatApiResponseSerializer.class.getName(), LogManager.LOG_TYPE.FW);

    @Override
    public void serialize(GoatApiResponse<?> response, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        try {
            gen.writeStartObject();
            gen.writeBooleanField("success", response.isSuccess());
            gen.writeStringField("message", response.getMessage());
            gen.writeNumberField("timestamp", response.getTimestamp());
            
            // Handle the data field based on its type
            gen.writeFieldName("data");
            serializeData(response.getData(), gen);
            
            gen.writeEndObject();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error serializing API response", e);
            // Create a simple fallback response if serialization fails
            gen.writeStartObject();
            gen.writeBooleanField("success", false);
            gen.writeStringField("message", "Error serializing response: " + e.getMessage());
            gen.writeNullField("data");
            gen.writeNumberField("timestamp", System.currentTimeMillis());
            gen.writeEndObject();
        }
    }
    
    /**
     * Serialize the data field based on its type
     */
    private void serializeData(Object data, JsonGenerator gen) throws IOException {
        if (data == null) {
            gen.writeNull();
        } else if (data instanceof JsonObject) {
            // Handle Gson JsonObject
            serializeJsonObject((JsonObject) data, gen);
        } else if (data instanceof JsonElement) {
            // Handle Gson JsonElement
            serializeJsonElement((JsonElement) data, gen);
        } else {
            // Let Jackson handle other types
            gen.writeObject(data);
        }
    }
    
    /**
     * Serialize a Gson JsonObject
     */
    private void serializeJsonObject(JsonObject jsonObject, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        
        for (String key : jsonObject.keySet()) {
            gen.writeFieldName(key);
            serializeJsonElement(jsonObject.get(key), gen);
        }
        
        gen.writeEndObject();
    }
    
    /**
     * Serialize a Gson JsonElement
     */
    private void serializeJsonElement(JsonElement element, JsonGenerator gen) throws IOException {
        if (element == null || element.isJsonNull()) {
            gen.writeNull();
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                gen.writeBoolean(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                gen.writeNumber(primitive.getAsString());
            } else {
                gen.writeString(primitive.getAsString());
            }
        } else if (element.isJsonArray()) {
            gen.writeStartArray();
            for (JsonElement item : element.getAsJsonArray()) {
                serializeJsonElement(item, gen);
            }
            gen.writeEndArray();
        } else if (element.isJsonObject()) {
            serializeJsonObject(element.getAsJsonObject(), gen);
        }
    }
}
package com.elitecsp.contact.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for JSON serialisation and deserialisation using Jackson.
 * A single shared {@link ObjectMapper} instance is used for efficiency.
 */
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Deserialises a JSON string into an instance of the given class.
     *
     * @param json  the JSON string to parse
     * @param clazz the target class
     * @param <T>   the type of the target object
     * @return the deserialised object
     * @throws IllegalArgumentException if parsing fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serialises an object to a JSON string.
     *
     * @param object the object to serialise
     * @return the JSON string representation
     * @throws IllegalStateException if serialisation fails
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise object to JSON: " + e.getMessage(), e);
        }
    }
}

package ca.elitecsp.common.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared JSON serialisation / deserialisation utility backed by a singleton
 * {@link ObjectMapper} instance for performance.
 *
 * <p>Both methods translate checked Jackson exceptions into unchecked
 * {@link CustomException} so callers do not need to handle checked exceptions.
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // Utility class – do not instantiate
    }

    /**
     * Deserialises a JSON string into an instance of the given class.
     *
     * @param json  the JSON string to parse; must not be {@code null}
     * @param clazz the target class
     * @param <T>   the type of the target object
     * @return the deserialised object
     * @throws CustomException with {@link ErrorCode#JSON_PARSE_ERROR} (HTTP 400) if parsing fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.JSON_PARSE_ERROR, 400,
                    "Failed to parse request JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serialises an object to a JSON string.
     *
     * @param object the object to serialise; must not be {@code null}
     * @return the JSON string representation
     * @throws CustomException with {@link ErrorCode#INTERNAL_ERROR} (HTTP 500) if serialisation fails
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                    "Failed to serialise response to JSON: " + e.getMessage(), e);
        }
    }
}

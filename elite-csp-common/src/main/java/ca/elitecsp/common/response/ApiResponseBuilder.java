package ca.elitecsp.common.response;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.model.BaseResponse;
import ca.elitecsp.common.util.Constants;
import ca.elitecsp.common.util.JsonUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

/**
 * Builds structured {@link APIGatewayProxyResponseEvent} responses for all Lambda endpoints.
 *
 * <p>All responses use {@link BaseResponse} as the JSON body and include the standard
 * CORS and content-type headers.
 *
 * <p>Response body format:
 * <pre>{@code
 * {
 *   "success": true|false,
 *   "message": "...",
 *   "error":   null | "ERROR_CODE"
 * }
 * }</pre>
 */
public final class ApiResponseBuilder {

    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            Constants.HEADER_CONTENT_TYPE, Constants.CONTENT_TYPE_JSON,
            Constants.HEADER_CORS_ORIGIN, Constants.CORS_ALLOW_ALL
    );

    private ApiResponseBuilder() {
        // Utility class – do not instantiate
    }

    // -------------------------------------------------------------------------
    // Public factory methods
    // -------------------------------------------------------------------------

    /**
     * Builds a successful (HTTP 200) response.
     *
     * @param message the success message to include in the body
     * @return a 200 {@link APIGatewayProxyResponseEvent}
     */
    public static APIGatewayProxyResponseEvent success(String message) {
        BaseResponse body = BaseResponse.builder()
                .success(true)
                .message(message)
                .build();
        return build(200, body);
    }

    /**
     * Builds a bad-request (HTTP 400) error response.
     *
     * @param message   the human-readable error message
     * @param errorCode the machine-readable error code
     * @return a 400 {@link APIGatewayProxyResponseEvent}
     */
    public static APIGatewayProxyResponseEvent badRequest(String message, String errorCode) {
        BaseResponse body = BaseResponse.builder()
                .success(false)
                .message(message)
                .error(errorCode)
                .build();
        return build(400, body);
    }

    /**
     * Builds an internal-server-error (HTTP 500) response.
     *
     * @param message   the human-readable error message
     * @param errorCode the machine-readable error code
     * @return a 500 {@link APIGatewayProxyResponseEvent}
     */
    public static APIGatewayProxyResponseEvent internalError(String message, String errorCode) {
        BaseResponse body = BaseResponse.builder()
                .success(false)
                .message(message)
                .error(errorCode)
                .build();
        return build(500, body);
    }

    /**
     * Builds a response from a {@link CustomException}, using its HTTP status code and error code.
     *
     * @param ex the exception to convert into a response
     * @return an {@link APIGatewayProxyResponseEvent} matching the exception's status
     */
    public static APIGatewayProxyResponseEvent fromException(CustomException ex) {
        BaseResponse body = BaseResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .error(ex.getErrorCode().name())
                .build();
        return build(ex.getHttpStatus(), body);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static APIGatewayProxyResponseEvent build(int statusCode, BaseResponse body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(DEFAULT_HEADERS)
                .withBody(JsonUtils.toJson(body));
    }
}

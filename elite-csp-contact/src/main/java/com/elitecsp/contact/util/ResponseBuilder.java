package com.elitecsp.contact.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

/**
 * Utility class to build structured {@link APIGatewayProxyResponseEvent} responses.
 *
 * <p>All responses follow the format:
 * <pre>
 * {
 *   "success": true|false,
 *   "message": "..."
 * }
 * </pre>
 */
public final class ResponseBuilder {

    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*"
    );

    private ResponseBuilder() {
        // Utility class – do not instantiate
    }

    /**
     * Builds a successful (HTTP 200) response.
     *
     * @param message the success message to include in the body
     * @return a 200 APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent success(String message) {
        return buildResponse(200, true, message);
    }

    /**
     * Builds a bad-request (HTTP 400) error response.
     *
     * @param message the error message to include in the body
     * @return a 400 APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent badRequest(String message) {
        return buildResponse(400, false, message);
    }

    /**
     * Builds an internal-server-error (HTTP 500) error response.
     *
     * @param message the error message to include in the body
     * @return a 500 APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent internalError(String message) {
        return buildResponse(500, false, message);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static APIGatewayProxyResponseEvent buildResponse(int statusCode, boolean success, String message) {
        String body = JsonUtil.toJson(new ResponseBody(success, message));
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(DEFAULT_HEADERS)
                .withBody(body);
    }

    /**
     * Inner class representing the JSON response body structure.
     */
    private static class ResponseBody {
        private final boolean success;
        private final String message;

        ResponseBody(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}

package ca.elitecsp.common.exception;

/**
 * Application-level exception that carries a structured {@link ErrorCode} and an HTTP status code.
 *
 * <p>Handlers should catch {@code CustomException} and map {@link #getHttpStatus()} directly to
 * the HTTP response status code, avoiding the need to examine exception types at the call site.
 *
 * <p>Usage example:
 * <pre>{@code
 * throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400, "Name must not be blank");
 * }</pre>
 */
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    /**
     * Constructs a new {@code CustomException}.
     *
     * @param errorCode  the machine-readable error code
     * @param httpStatus the HTTP status code that should be returned to the caller
     * @param message    a human-readable description of the error
     */
    public CustomException(ErrorCode errorCode, int httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * Constructs a new {@code CustomException} wrapping a root cause.
     *
     * @param errorCode  the machine-readable error code
     * @param httpStatus the HTTP status code that should be returned to the caller
     * @param message    a human-readable description of the error
     * @param cause      the underlying exception
     */
    public CustomException(ErrorCode errorCode, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the machine-readable error code for this exception.
     *
     * @return the {@link ErrorCode}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the HTTP status code that should be used in the response.
     *
     * @return HTTP status code (e.g. 400, 500)
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}

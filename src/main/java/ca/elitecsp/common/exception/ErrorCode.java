package ca.elitecsp.common.exception;

/**
 * Enumeration of application-level error codes used across all Elite CSP modules.
 *
 * <p>Each constant maps a specific failure scenario to a machine-readable identifier,
 * allowing handlers to produce consistent, predictable HTTP error responses.
 */
public enum ErrorCode {

    /** A required request field is missing or blank. */
    MISSING_REQUIRED_FIELD,

    /** A field value does not pass format or business-rule validation. */
    VALIDATION_ERROR,

    /** The provided email address is not in a valid format. */
    INVALID_EMAIL,

    /** The uploaded file type is not supported (e.g. not a PDF). */
    INVALID_FILE_TYPE,

    /** The uploaded file exceeds the maximum permitted size. */
    FILE_TOO_LARGE,

    /** The request body could not be parsed as valid JSON. */
    JSON_PARSE_ERROR,

    /** Sending an email via Amazon SES failed. */
    EMAIL_SEND_FAILURE,

    /** An unexpected internal error occurred. */
    INTERNAL_ERROR
}

package ca.elitecsp.common.util;

/**
 * Central repository of application-wide constants shared across Elite CSP modules.
 *
 * <p>Using named constants instead of magic values improves readability, reduces
 * duplication, and makes it easier to adjust limits in a single place.
 */
public final class Constants {

    // -------------------------------------------------------------------------
    // CV / file upload limits
    // -------------------------------------------------------------------------

    /** Maximum allowed CV file size in bytes (5 MB). */
    public static final int MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

    /**
     * PDF magic bytes ({@code %PDF}).
     * Used to verify that an uploaded file is genuinely a PDF document.
     */
    public static final byte[] PDF_MAGIC_BYTES = {0x25, 0x50, 0x44, 0x46};

    // -------------------------------------------------------------------------
    // MIME / Content-Type values
    // -------------------------------------------------------------------------

    /** MIME type for JSON payloads. */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /** MIME type for PDF documents. */
    public static final String CONTENT_TYPE_PDF = "application/pdf";

    // -------------------------------------------------------------------------
    // HTTP header names and values
    // -------------------------------------------------------------------------

    /** HTTP {@code Content-Type} header name. */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    /** HTTP {@code Access-Control-Allow-Origin} header name. */
    public static final String HEADER_CORS_ORIGIN = "Access-Control-Allow-Origin";

    /** Wildcard CORS origin value – allows requests from any origin. */
    public static final String CORS_ALLOW_ALL = "*";

    // -------------------------------------------------------------------------
    // S3 key patterns
    // -------------------------------------------------------------------------

    /** S3 key prefix for uploaded CV files. */
    public static final String S3_UPLOADS_PREFIX = "uploads";

    // -------------------------------------------------------------------------
    // Gmail / email constants
    // -------------------------------------------------------------------------

    /** Display name used in the Gmail API application name. */
    public static final String GMAIL_APPLICATION_NAME = "Elite CSP Contact";

    /** Gmail OAuth2 scope required to send messages. */
    public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    /** Value passed as the Gmail user ID to represent the authenticated account. */
    public static final String GMAIL_USER_ME = "me";

    // -------------------------------------------------------------------------
    // Email format pattern
    // -------------------------------------------------------------------------

    /**
     * Regular expression for basic email address validation.
     * Requires at least one non-whitespace character before and after {@code @},
     * and a dot in the domain part.
     */
    public static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private Constants() {
        // Utility class – do not instantiate
    }
}

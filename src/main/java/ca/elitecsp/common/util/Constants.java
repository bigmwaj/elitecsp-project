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

    /**
     * DOCX magic bytes (ZIP PK signature: {@code PK\x03\x04}).
     * DOCX files are ZIP-based OOXML archives and share this signature with other ZIP
     * formats (e.g. XLSX, JAR).  This check is combined with an extension check in
     * {@link ca.elitecsp.common.util.ValidationUtils#requireAllowedFileType} to
     * reduce false positives.
     */
    public static final byte[] DOCX_MAGIC_BYTES = {0x50, 0x4B, 0x03, 0x04};

    // -------------------------------------------------------------------------
    // MIME / Content-Type values
    // -------------------------------------------------------------------------

    /** MIME type for JSON payloads. */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /** MIME type for PDF documents. */
    public static final String CONTENT_TYPE_PDF = "application/pdf";

    /** MIME type for DOCX (Office Open XML word processing) documents. */
    public static final String CONTENT_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
    // Email / SES constants
    // -------------------------------------------------------------------------

    /** MIME type for plain-text email content. */
    public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=UTF-8";

    /** MIME type for HTML email content. */
    public static final String CONTENT_TYPE_TEXT_HTML = "text/html; charset=UTF-8";

    /** Subject prefix for contact-form emails sent via Amazon SES. */
    public static final String CONTACT_EMAIL_SUBJECT_PREFIX = "Elite CSP – Contact Form: ";

    /** Subject prefix for job-application emails sent via Amazon SES. */
    public static final String JOB_APPLICATION_EMAIL_SUBJECT_PREFIX = "Elite CSP – Job Application: ";

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

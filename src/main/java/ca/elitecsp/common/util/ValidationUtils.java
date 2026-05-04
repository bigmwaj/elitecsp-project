package ca.elitecsp.common.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;

import java.util.Base64;

/**
 * Shared validation helpers used across Elite CSP Lambda modules.
 *
 * <p>All {@code require*} methods throw {@link CustomException} with HTTP 400 on failure,
 * allowing handlers to catch a single exception type and map it to the correct HTTP response.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class – do not instantiate
    }

    // -------------------------------------------------------------------------
    // Generic field validation
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the string is {@code null} or contains only whitespace.
     *
     * @param value the string to test
     * @return {@code true} if blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Asserts that the given string is not blank.
     * Throws {@link CustomException} with {@link ErrorCode#MISSING_REQUIRED_FIELD} (HTTP 400)
     * if the value is blank.
     *
     * @param value     the value to check
     * @param fieldName the human-readable field name used in the error message
     * @throws CustomException if {@code value} is blank
     */
    public static void requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    fieldName + " must not be blank");
        }
    }

    // -------------------------------------------------------------------------
    // Email validation
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the email address matches the basic format pattern.
     *
     * @param email the email address to test
     * @return {@code true} if the format appears valid
     */
    public static boolean isValidEmail(String email) {
        return email != null && email.matches(Constants.EMAIL_REGEX);
    }

    /**
     * Asserts that the given email is not blank and has a valid format.
     * Throws {@link CustomException} on the first violated rule.
     *
     * @param email     the email address to validate
     * @param fieldName the human-readable field name used in the error message
     * @throws CustomException with {@link ErrorCode#MISSING_REQUIRED_FIELD} (HTTP 400) if blank
     * @throws CustomException with {@link ErrorCode#INVALID_EMAIL} (HTTP 400) if format is invalid
     */
    public static void requireValidEmail(String email, String fieldName) {
        requireNonBlank(email, fieldName);
        if (!isValidEmail(email)) {
            throw new CustomException(ErrorCode.INVALID_EMAIL, 400,
                    fieldName + " address is invalid: " + email);
        }
    }

    // -------------------------------------------------------------------------
    // File / Base64 helpers
    // -------------------------------------------------------------------------

    /**
     * Decodes a Base64-encoded file string, stripping any data-URI prefix
     * (e.g. {@code data:application/pdf;base64,}) if present.
     *
     * @param base64File the Base64-encoded file string
     * @return the raw file bytes
     * @throws CustomException with {@link ErrorCode#VALIDATION_ERROR} (HTTP 400)
     *                         if the string is not valid Base64
     */
    public static byte[] decodeBase64File(String base64File) {
        try {
            String base64Data = base64File.contains(",")
                    ? base64File.split(",", 2)[1]
                    : base64File;
            return Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, 400,
                    "File is not valid Base64: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the decoded file bytes do not exceed {@link Constants#MAX_CV_SIZE_BYTES}.
     *
     * @param fileBytes the decoded file bytes
     * @throws CustomException with {@link ErrorCode#FILE_TOO_LARGE} (HTTP 400) if the file is too large
     */
    public static void requireCvSizeWithinLimit(byte[] fileBytes) {
        if (fileBytes.length > Constants.MAX_CV_SIZE_BYTES) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE, 400,
                    "CV file exceeds the maximum allowed size of 5 MB (actual: "
                    + fileBytes.length + " bytes)");
        }
    }

    /**
     * Validates that the decoded file bytes begin with the PDF magic bytes ({@code %PDF}).
     *
     * @param fileBytes the decoded file bytes
     * @throws CustomException with {@link ErrorCode#INVALID_FILE_TYPE} (HTTP 400)
     *                         if the file is not a valid PDF
     */
    public static void requirePdfMagicBytes(byte[] fileBytes) {
        byte[] magic = Constants.PDF_MAGIC_BYTES;
        if (fileBytes.length < magic.length) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE, 400,
                    "CV file is too small to be a valid PDF");
        }
        for (int i = 0; i < magic.length; i++) {
            if (fileBytes[i] != magic[i]) {
                throw new CustomException(ErrorCode.INVALID_FILE_TYPE, 400,
                        "CV file must be a valid PDF document");
            }
        }
    }

    /**
     * Validates that the file is an allowed type (PDF or DOCX) based on both the
     * filename extension and the file's magic bytes.
     *
     * <ul>
     *   <li>PDF: filename ends with {@code .pdf} and content starts with {@code %PDF}.</li>
     *   <li>DOCX: filename ends with {@code .docx} and content starts with the ZIP
     *       signature {@code PK\x03\x04}.</li>
     * </ul>
     *
     * @param fileBytes the decoded file bytes
     * @param fileName  the original filename (e.g. {@code "resume.pdf"} or {@code "cv.docx"})
     * @throws CustomException with {@link ErrorCode#INVALID_FILE_TYPE} (HTTP 400)
     *                         if the file type is not allowed or the magic bytes do not match
     */
    public static void requireAllowedFileType(byte[] fileBytes, String fileName) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            requirePdfMagicBytes(fileBytes);
        } else if (lowerName.endsWith(".docx")) {
            requireDocxMagicBytes(fileBytes);
        } else {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE, 400,
                    "Unsupported file type. Allowed types: PDF, DOCX");
        }
    }

    /**
     * Validates that the decoded file bytes begin with the DOCX / ZIP magic bytes
     * ({@code PK\x03\x04}).
     *
     * @param fileBytes the decoded file bytes
     * @throws CustomException with {@link ErrorCode#INVALID_FILE_TYPE} (HTTP 400)
     *                         if the file does not start with the expected ZIP signature
     */
    private static void requireDocxMagicBytes(byte[] fileBytes) {
        byte[] magic = Constants.DOCX_MAGIC_BYTES;
        if (fileBytes.length < magic.length) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE, 400,
                    "File is too small to be a valid DOCX document");
        }
        for (int i = 0; i < magic.length; i++) {
            if (fileBytes[i] != magic[i]) {
                throw new CustomException(ErrorCode.INVALID_FILE_TYPE, 400,
                        "DOCX file must be a valid Office Open XML document");
            }
        }
    }
}

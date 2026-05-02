package com.elitecsp.jobapplication.util;

import com.elitecsp.jobapplication.model.JobApplicationRequest;

import java.util.Base64;

/**
 * Utility class providing validation helpers for the job application request.
 */
public final class ValidationUtil {

    /** Maximum allowed CV file size in bytes (5 MB). */
    public static final int MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

    /** Expected MIME type / magic bytes prefix for PDF files. */
    private static final byte[] PDF_MAGIC_BYTES = {0x25, 0x50, 0x44, 0x46}; // %PDF

    private ValidationUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Validates the fields of a {@link JobApplicationRequest}.
     * Throws {@link IllegalArgumentException} with a descriptive message if validation fails.
     *
     * @param request the job application request to validate
     */
    public static void validateJobApplicationRequest(JobApplicationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (isBlank(request.getFullName())) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        if (isBlank(request.getEmail())) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (!isValidEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email address is invalid: " + request.getEmail());
        }
        if (isBlank(request.getCity())) {
            throw new IllegalArgumentException("City must not be blank");
        }
        if (isBlank(request.getJobTitle())) {
            throw new IllegalArgumentException("Job title must not be blank");
        }
        if (isBlank(request.getCvFile())) {
            throw new IllegalArgumentException("CV file must not be blank");
        }

        byte[] cvBytes = decodeCv(request.getCvFile());
        validateCvSize(cvBytes);
        validateCvIsPdf(cvBytes);
    }

    /**
     * Decodes a Base64-encoded CV file, stripping any data-URI prefix if present.
     *
     * @param cvFile the base64-encoded CV string
     * @return the raw file bytes
     * @throws IllegalArgumentException if the string is not valid Base64
     */
    public static byte[] decodeCv(String cvFile) {
        try {
            // Strip optional data-URI prefix (e.g. "data:application/pdf;base64,")
            String base64Data = cvFile.contains(",") ? cvFile.split(",", 2)[1] : cvFile;
            return Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CV file is not valid Base64: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void validateCvSize(byte[] cvBytes) {
        if (cvBytes.length > MAX_CV_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "CV file exceeds the maximum allowed size of 5 MB (actual: "
                    + cvBytes.length + " bytes)");
        }
    }

    private static void validateCvIsPdf(byte[] cvBytes) {
        if (cvBytes.length < PDF_MAGIC_BYTES.length) {
            throw new IllegalArgumentException("CV file is too small to be a valid PDF");
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (cvBytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new IllegalArgumentException("CV file must be a valid PDF document");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}

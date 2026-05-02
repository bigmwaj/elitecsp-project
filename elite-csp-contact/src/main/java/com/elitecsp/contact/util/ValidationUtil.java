package com.elitecsp.contact.util;

import com.elitecsp.contact.model.ContactRequest;

/**
 * Utility class providing validation helpers for the contact request.
 */
public final class ValidationUtil {

    private ValidationUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Validates the fields of a {@link ContactRequest}.
     * Throws {@link IllegalArgumentException} with a descriptive message if validation fails.
     *
     * @param request the contact request to validate
     */
    public static void validateContactRequest(ContactRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (isBlank(request.getName())) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (isBlank(request.getEmail())) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (!isValidEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email address is invalid: " + request.getEmail());
        }
        if (isBlank(request.getMessage())) {
            throw new IllegalArgumentException("Message must not be blank");
        }
    }

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
     * Performs a basic email format validation.
     *
     * @param email the email address to validate
     * @return {@code true} if the email format appears valid
     */
    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}

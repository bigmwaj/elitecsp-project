package ca.elitecsp.contact.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.ValidationUtils;
import ca.elitecsp.contact.model.ContactRequest;

/**
 * Validation helper for the contact form request.
 *
 * <p>Delegates generic field and email checks to {@link ValidationUtils} from the
 * shared common module, keeping only the contact-domain orchestration here.
 */
public final class ValidationUtil {

    private ValidationUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Validates all fields of a {@link ContactRequest}.
     * Throws {@link CustomException} with HTTP 400 on the first violated rule.
     *
     * <p>When {@link ContactRequest#getAttachmentFile()} is provided, the attachment
     * is also validated: it must be valid Base64, must not exceed 5 MB, and must be
     * a genuine PDF document. In that case {@link ContactRequest#getAttachmentFileName()}
     * must also be non-blank.
     *
     * @param request the contact request to validate; must not be {@code null}
     * @throws CustomException if any required field is missing or invalid
     */
    public static void validateContactRequest(ContactRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    "Request body must not be null");
        }
        ValidationUtils.requireNonBlank(request.getName(), "Name");
        ValidationUtils.requireValidEmail(request.getEmail(), "Email");
        ValidationUtils.requireNonBlank(request.getMessage(), "Message");

        if (!ValidationUtils.isBlank(request.getAttachmentFile())) {
            byte[] fileBytes = ValidationUtils.decodeBase64File(request.getAttachmentFile());
            ValidationUtils.requireCvSizeWithinLimit(fileBytes);
            ValidationUtils.requirePdfMagicBytes(fileBytes);
            ValidationUtils.requireNonBlank(request.getAttachmentFileName(), "Attachment file name");
        }
    }
}

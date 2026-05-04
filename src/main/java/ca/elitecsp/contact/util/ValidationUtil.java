package ca.elitecsp.contact.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.ValidationUtils;
import ca.elitecsp.contact.model.ContactRequest;
import ca.elitecsp.contact.model.ContactType;

/**
 * Validation helper for the unified contact/job-application request.
 *
 * <p>Delegates generic field and email checks to {@link ValidationUtils} from the
 * shared common module, keeping only the contact-domain orchestration here.
 *
 * <p>Validation rules vary by {@link ContactType}:
 * <ul>
 *   <li>{@link ContactType#CONTACT} – {@code fullName}, {@code email}, and
 *       {@code message} are required; attachment is optional but validated if present.</li>
 *   <li>{@link ContactType#JOB_APPLICATION} – same required fields as CONTACT, plus
 *       {@code attachment} and {@code attachmentFileName} are required; file must be
 *       PDF or DOCX and must not exceed 5 MB.</li>
 * </ul>
 */
public final class ValidationUtil {

    private ValidationUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Validates all fields of a {@link ContactRequest} according to its
     * {@link ContactRequest#getType() type}.
     *
     * <p>Throws {@link CustomException} with HTTP 400 on the first violated rule.
     *
     * @param request the contact request to validate; must not be {@code null}
     * @throws CustomException if any required field is missing or invalid
     */
    public static void validateContactRequest(ContactRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    "Request body must not be null");
        }

        ValidationUtils.requireNonBlank(request.getFullName(), "Full name");
        ValidationUtils.requireValidEmail(request.getEmail(), "Email");
        ValidationUtils.requireNonBlank(request.getMessage(), "Message");

        ContactType type = request.getEffectiveType();

        if (type == ContactType.JOB_APPLICATION) {
            // Attachment is mandatory for job applications
            ValidationUtils.requireNonBlank(request.getAttachment(), "Attachment");
            ValidationUtils.requireNonBlank(request.getAttachmentFileName(), "Attachment file name");
            byte[] fileBytes = ValidationUtils.decodeBase64File(request.getAttachment());
            ValidationUtils.requireCvSizeWithinLimit(fileBytes);
            ValidationUtils.requireAllowedFileType(fileBytes, request.getAttachmentFileName());
        } else {
            // CONTACT: attachment is optional; validate if present
            if (!ValidationUtils.isBlank(request.getAttachment())) {
                byte[] fileBytes = ValidationUtils.decodeBase64File(request.getAttachment());
                ValidationUtils.requireCvSizeWithinLimit(fileBytes);
                ValidationUtils.requireAllowedFileType(fileBytes, request.getAttachmentFileName());
                ValidationUtils.requireNonBlank(request.getAttachmentFileName(), "Attachment file name");
            }
        }
    }
}

package ca.elitecsp.contact.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the incoming contact / job-application request payload.
 * This model maps to the JSON body sent from API Gateway.
 *
 * <p>The {@link #type} field selects the processing path:
 * <ul>
 *   <li>{@link ContactType#CONTACT} (default) – sends a notification email via SES.</li>
 *   <li>{@link ContactType#JOB_APPLICATION} – uploads the CV to S3, then sends an email
 *       with the file URL; {@link #attachment} and {@link #attachmentFileName} are
 *       required for this type.</li>
 * </ul>
 *
 * <p>JSON backward-compatibility notes:
 * <ul>
 *   <li>{@code "name"} is accepted as an alias for {@code "fullName"}.</li>
 *   <li>{@code "attachmentFile"} is accepted as an alias for {@code "attachment"}.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactRequest {

    /**
     * Contact type that determines the processing path.
     * Defaults to {@link ContactType#CONTACT} when omitted.
     */
    private ContactType type;

    /**
     * Full name of the person submitting the form.
     * Accepts {@code "name"} as an alias for backward compatibility.
     */
    @JsonAlias("name")
    private String fullName;

    /** Email address of the sender. */
    private String email;

    /**
     * City of the sender (optional).
     * Displayed in the notification email when provided.
     */
    private String city;

    /**
     * Subject of the message (optional, for {@link ContactType#CONTACT} only).
     * When omitted, the email subject is auto-generated from the sender name.
     */
    private String subject;

    /** Message body / cover letter of the submission. */
    private String message;

    /**
     * Optional Base64-encoded file to include with the submission.
     * A data-URI prefix such as {@code data:application/pdf;base64,} is
     * automatically stripped before processing.
     * Accepts {@code "attachmentFile"} as an alias for backward compatibility.
     * <p>Required when {@link #type} is {@link ContactType#JOB_APPLICATION}.
     */
    @JsonAlias("attachmentFile")
    private String attachment;

    /**
     * Original filename for the attachment (e.g. {@code "resume.pdf"} or
     * {@code "cv.docx"}).  Required when {@link #attachment} is provided.
     */
    private String attachmentFileName;

    /**
     * Returns the effective contact type, defaulting to {@link ContactType#CONTACT}
     * when the {@link #type} field was omitted from the JSON payload.
     *
     * @return the non-null contact type to apply
     */
    public ContactType getEffectiveType() {
        return type != null ? type : ContactType.CONTACT;
    }
}


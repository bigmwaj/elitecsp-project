package ca.elitecsp.contact.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the incoming contact form request payload.
 * This model maps to the JSON body sent from the API Gateway.
 *
 * <p>An optional file attachment (e.g. a CV or resume in PDF format) may be
 * included as a Base64-encoded string in {@link #attachmentFile}, with its
 * original filename provided in {@link #attachmentFileName}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactRequest {

    /** Full name of the person submitting the contact form. */
    private String name;

    /** Email address of the sender. */
    private String email;

    /** Message body of the contact form. */
    private String message;

    /**
     * Optional Base64-encoded file to attach to the contact email
     * (e.g. a CV in PDF format). A data-URI prefix such as
     * {@code data:application/pdf;base64,} is automatically stripped.
     */
    private String attachmentFile;

    /**
     * Original filename for the attachment (e.g. {@code "resume.pdf"}).
     * Required when {@link #attachmentFile} is provided.
     */
    private String attachmentFileName;
}

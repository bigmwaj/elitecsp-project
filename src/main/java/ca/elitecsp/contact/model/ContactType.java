package ca.elitecsp.contact.model;

/**
 * Discriminates the type of inbound contact request.
 *
 * <ul>
 *   <li>{@link #CONTACT} – a standard website contact form submission;
 *       sends a notification email via Amazon SES.</li>
 *   <li>{@link #JOB_APPLICATION} – a job application submission;
 *       uploads the CV attachment to Amazon S3 then sends a notification
 *       email that includes the S3 file URL.</li>
 * </ul>
 *
 * <p>If the {@code type} field is omitted from the JSON payload the handler
 * defaults to {@link #CONTACT}.
 */
public enum ContactType {

    /** Standard contact-form enquiry – email only, no S3 upload. */
    CONTACT,

    /** Job application – CV uploaded to S3 before the email is sent. */
    JOB_APPLICATION
}

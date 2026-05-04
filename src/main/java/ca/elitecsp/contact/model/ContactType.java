package ca.elitecsp.contact.model;

/**
 * Discriminates the type of inbound contact request.
 *
 * <ul>
 *   <li>{@link #CONTACT} – a standard website contact form submission;
 *       sends a notification email via Amazon SES (with optional attachment).</li>
 *   <li>{@link #JOB_APPLICATION} – a job application submission;
 *       sends a notification email via Amazon SES with the CV attached directly.</li>
 * </ul>
 *
 * <p>If the {@code type} field is omitted from the JSON payload the handler
 * defaults to {@link #CONTACT}.
 */
public enum ContactType {

    /** Standard contact-form enquiry – email only, optional attachment. */
    CONTACT,

    /** Job application – CV attached directly to the notification email. */
    JOB_APPLICATION
}

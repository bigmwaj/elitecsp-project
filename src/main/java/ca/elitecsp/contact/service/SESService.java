package ca.elitecsp.contact.service;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.Constants;
import ca.elitecsp.common.util.EmailTemplateLoader;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Service responsible for sending transactional emails via Amazon Simple Email Service (SES).
 *
 * <p>Two email types are supported:
 * <ul>
 *   <li><b>Contact</b> – sent via {@link #sendContactEmail}; supports an optional inline
 *       attachment (PDF or DOCX). When an attachment is present the email is built as a
 *       raw MIME multipart message; otherwise a simple {@code sendEmail} call is used.</li>
 *   <li><b>Job Application</b> – sent via {@link #sendJobApplicationEmail}; the CV is
 *       already stored in S3 and only a link is included in the email body.</li>
 * </ul>
 *
 * <p>Email bodies (plain-text and HTML) are loaded from classpath templates:
 * <ul>
 *   <li>{@code templates/contact-email.txt} / {@code .html}</li>
 *   <li>{@code templates/job-application-email.txt} / {@code .html}</li>
 * </ul>
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code FROM_EMAIL}        – Verified SES sender address</li>
 *   <li>{@code DESTINATION_EMAIL} – Recipient email address for all messages</li>
 *   <li>{@code AWS_REGION}        – AWS region where SES is configured (e.g. {@code us-east-1})</li>
 * </ul>
 *
 * <p>AWS credentials are resolved automatically by the SDK's default credential chain
 * (Lambda execution role, environment variables, or instance profile) — no hardcoded secrets.
 */
@Slf4j
public class SESService {

    private static final String ENV_FROM_EMAIL = "FROM_EMAIL";
    private static final String ENV_DESTINATION_EMAIL = "DESTINATION_EMAIL";
    private static final String ENV_AWS_REGION = "AWS_REGION";

    /** Verified SES sender (From) address. */
    private final String fromEmail;

    /** Recipient address for all notification emails. */
    private final String destinationEmail;

    /** SES client initialised with the configured AWS region. */
    private final SesClient sesClient;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Reads configuration from environment variables and initialises the SES client.
     *
     * @throws CustomException if any required environment variable is missing
     */
    public SESService() {
        this.fromEmail = requireEnv(ENV_FROM_EMAIL);
        this.destinationEmail = requireEnv(ENV_DESTINATION_EMAIL);
        String awsRegion = requireEnv(ENV_AWS_REGION);
        this.sesClient = SesClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Package-private constructor for dependency injection in tests.
     *
     * @param fromEmail        verified SES sender address
     * @param destinationEmail recipient email address
     * @param sesClient        pre-configured SES client
     */
    SESService(String fromEmail, String destinationEmail, SesClient sesClient) {
        this.fromEmail = fromEmail;
        this.destinationEmail = destinationEmail;
        this.sesClient = sesClient;
    }

    /**
     * Sends a contact-form notification email via Amazon SES, optionally with a file attachment.
     *
     * <p>When {@code attachmentBytes} is non-null the message is assembled as a raw MIME
     * multipart email so the attachment can be included. Otherwise a simple
     * {@code sendEmail} call (text + HTML) is used.
     *
     * @param fullName        the full name of the person who submitted the form
     * @param senderEmail     the email address of the sender (used as Reply-To)
     * @param city            the sender's city (optional; may be {@code null} or blank)
     * @param subject         the message subject (optional; auto-generated when blank)
     * @param messageBody     the message content from the contact form
     * @param attachmentBytes optional decoded file bytes; {@code null} means no attachment
     * @param attachmentName  the filename for the attachment; ignored when {@code attachmentBytes} is {@code null}
     * @throws CustomException with {@link ErrorCode#EMAIL_SEND_FAILURE} (HTTP 500) if sending fails
     */
    public void sendContactEmail(String fullName, String senderEmail, String city,
                                  String subject, String messageBody,
                                  byte[] attachmentBytes, String attachmentName) {
        log.info("Sending contact email via SES on behalf of: {}", senderEmail);
        try {
            if (attachmentBytes != null) {
                sendRawContactEmail(fullName, senderEmail, city, subject, messageBody,
                        attachmentBytes, attachmentName);
            } else {
                sendSimpleContactEmail(fullName, senderEmail, city, subject, messageBody);
            }
            log.info("Contact email sent successfully to {} on behalf of {}", destinationEmail, senderEmail);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send contact email for sender: {}", senderEmail, e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to send email via SES: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a job-application notification email via Amazon SES.
     *
     * <p>The CV has already been uploaded to S3; the {@code fileUrl} parameter
     * contains the publicly-accessible link included in the email body.
     *
     * @param fullName    the full name of the applicant
     * @param senderEmail the applicant's email address (used as Reply-To)
     * @param city        the applicant's city (optional; may be {@code null} or blank)
     * @param messageBody the cover letter / message body
     * @param fileUrl     the S3 HTTPS URL of the uploaded CV
     * @throws CustomException with {@link ErrorCode#EMAIL_SEND_FAILURE} (HTTP 500) if sending fails
     */
    public void sendJobApplicationEmail(String fullName, String senderEmail, String city,
                                         String messageBody, String fileUrl) {
        log.info("Sending job-application email via SES on behalf of: {}", senderEmail);
        try {
            String emailSubject = Constants.JOB_APPLICATION_EMAIL_SUBJECT_PREFIX + fullName;
            Map<String, String> placeholders = buildJobApplicationPlaceholders(
                    fullName, senderEmail, city, messageBody, fileUrl);

            sendSimpleEmail(emailSubject, senderEmail,
                    EmailTemplateLoader.load("job-application-email.txt", placeholders),
                    EmailTemplateLoader.load("job-application-email.html", placeholders));

            log.info("Job-application email sent successfully to {} on behalf of {}",
                    destinationEmail, senderEmail);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send job-application email for sender: {}", senderEmail, e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to send email via SES: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers – contact email (no attachment)
    // -------------------------------------------------------------------------

    private void sendSimpleContactEmail(String fullName, String senderEmail, String city,
                                         String subject, String messageBody) {
        String emailSubject = resolveContactSubject(subject, fullName);
        Map<String, String> placeholders = buildContactPlaceholders(fullName, senderEmail, city, messageBody, subject);

        sendSimpleEmail(emailSubject, senderEmail,
                EmailTemplateLoader.load("contact-email.txt", placeholders),
                EmailTemplateLoader.load("contact-email.html", placeholders));
    }

    private void sendSimpleEmail(String subject, String replyTo, String textBody, String htmlBody) {
        Content subjectContent = Content.builder().data(subject).charset("UTF-8").build();
        Content textContent = Content.builder().data(textBody).charset("UTF-8").build();
        Content htmlContent = Content.builder().data(htmlBody).charset("UTF-8").build();

        Body body = Body.builder().text(textContent).html(htmlContent).build();
        software.amazon.awssdk.services.ses.model.Message message =
                software.amazon.awssdk.services.ses.model.Message.builder()
                        .subject(subjectContent).body(body).build();

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder().toAddresses(destinationEmail).build())
                .replyToAddresses(replyTo)
                .message(message)
                .build();

        sesClient.sendEmail(emailRequest);
    }

    // -------------------------------------------------------------------------
    // Private helpers – contact email with attachment (raw MIME)
    // -------------------------------------------------------------------------

    private void sendRawContactEmail(String fullName, String senderEmail, String city,
                                      String subject, String messageBody,
                                      byte[] attachmentBytes, String attachmentName) {
        try {
            String emailSubject = resolveContactSubject(subject, fullName);
            Map<String, String> placeholders = buildContactPlaceholders(
                    fullName, senderEmail, city, messageBody, subject);
            byte[] rawMime = buildRawMimeMessage(emailSubject, senderEmail,
                    EmailTemplateLoader.load("contact-email.txt", placeholders),
                    EmailTemplateLoader.load("contact-email.html", placeholders),
                    attachmentBytes, attachmentName);

            sesClient.sendRawEmail(SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteArray(rawMime))
                            .build())
                    .build());
        } catch (MessagingException | IOException e) {
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to build MIME message: " + e.getMessage(), e);
        }
    }

    private byte[] buildRawMimeMessage(String subject, String replyTo,
                                        String textBody, String htmlBody,
                                        byte[] attachmentBytes, String attachmentName)
            throws MessagingException, IOException {

        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(fromEmail));
        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(destinationEmail));
        mimeMessage.setReplyTo(new InternetAddress[]{new InternetAddress(replyTo)});
        mimeMessage.setSubject(subject, "UTF-8");

        // Outer multipart/mixed container
        MimeMultipart mixed = new MimeMultipart("mixed");

        // Inner multipart/alternative for text + HTML bodies
        MimeBodyPart bodyPart = new MimeBodyPart();
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(textBody, Constants.CONTENT_TYPE_TEXT_PLAIN);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, Constants.CONTENT_TYPE_TEXT_HTML);

        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        bodyPart.setContent(alternative);
        mixed.addBodyPart(bodyPart);

        // Attachment part
        String lowerName = attachmentName != null ? attachmentName.toLowerCase() : "";
        String attachmentMime = lowerName.endsWith(".docx") ? Constants.CONTENT_TYPE_DOCX : Constants.CONTENT_TYPE_PDF;
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(
                new DataHandler(new ByteArrayDataSource(attachmentBytes, attachmentMime)));
        attachmentPart.setFileName(attachmentName);
        mixed.addBodyPart(attachmentPart);

        mimeMessage.setContent(mixed);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mimeMessage.writeTo(out);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Private helpers – placeholder builders
    // -------------------------------------------------------------------------

    /**
     * Builds the template placeholder map for contact-form emails.
     * All user-supplied values are HTML-escaped before use.
     */
    private Map<String, String> buildContactPlaceholders(String fullName, String senderEmail,
                                                          String city, String messageBody,
                                                          String subject) {
        Map<String, String> map = new HashMap<>();
        map.put("{{NAME}}", htmlEscape(fullName));
        map.put("{{EMAIL}}", htmlEscape(senderEmail));
        map.put("{{CITY}}", htmlEscape(city != null ? city : ""));
        map.put("{{SUBJECT}}", htmlEscape(subject != null ? subject : ""));
        map.put("{{MESSAGE}}", htmlEscape(messageBody).replace("\n", "<br/>"));
        return map;
    }

    /**
     * Builds the template placeholder map for job-application emails.
     * All user-supplied values are HTML-escaped before use.
     */
    private Map<String, String> buildJobApplicationPlaceholders(String fullName, String senderEmail,
                                                                  String city, String messageBody,
                                                                  String fileUrl) {
        Map<String, String> map = new HashMap<>();
        map.put("{{NAME}}", htmlEscape(fullName));
        map.put("{{EMAIL}}", htmlEscape(senderEmail));
        map.put("{{CITY}}", htmlEscape(city != null ? city : ""));
        map.put("{{MESSAGE}}", htmlEscape(messageBody).replace("\n", "<br/>"));
        map.put("{{FILE_URL}}", htmlEscape(fileUrl));
        return map;
    }

    // -------------------------------------------------------------------------
    // Private helpers – shared
    // -------------------------------------------------------------------------

    /**
     * Resolves the email subject: uses the caller-supplied {@code subject} when non-blank,
     * otherwise falls back to the default prefix plus the sender's full name.
     *
     * @param subject  optional subject field from the request
     * @param fullName the sender's full name
     * @return the resolved email subject string
     */
    private static String resolveContactSubject(String subject, String fullName) {
        return (subject != null && !subject.isBlank())
                ? subject
                : Constants.CONTACT_EMAIL_SUBJECT_PREFIX + fullName;
    }

    /**
     * Escapes HTML special characters to prevent injection in the email body.
     *
     * @param input raw input string
     * @return HTML-escaped string, or an empty string if {@code input} is {@code null}
     */
    private static String htmlEscape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * Reads a required environment variable or throws {@link CustomException}.
     *
     * @param name the environment variable name
     * @return the value of the environment variable
     * @throws CustomException if the variable is not set or blank
     */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                    "Missing required environment variable: " + name);
        }
        return value;
    }
}

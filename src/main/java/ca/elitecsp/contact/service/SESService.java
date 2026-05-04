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
import java.util.Map;
import java.util.Properties;

/**
 * Service responsible for sending transactional emails via Amazon Simple Email Service (SES).
 *
 * <p>When no file attachment is included, a simple SES {@code sendEmail} call is made.
 * When a PDF attachment is present the email is assembled as a MIME multipart message
 * and dispatched via {@code sendRawEmail}.
 *
 * <p>Email bodies (plain-text and HTML) are loaded from classpath templates:
 * <ul>
 *   <li>{@code templates/contact-email.txt} – plain-text fallback</li>
 *   <li>{@code templates/contact-email.html} – styled HTML body</li>
 * </ul>
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code FROM_EMAIL}        – Verified SES sender address</li>
 *   <li>{@code DESTINATION_EMAIL} – Recipient email address for contact messages</li>
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

    /** Recipient address for all contact-form messages. */
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
     * Sends a contact-form email via Amazon SES, optionally with a PDF attachment.
     *
     * <p>When {@code attachmentBytes} is non-null the message is sent as a raw MIME
     * multipart email so the attachment can be included.  Otherwise a simple SES
     * {@code sendEmail} call (text + HTML) is used.
     *
     * @param senderName      the name of the person who submitted the form
     * @param senderEmail     the email address of the sender (used as Reply-To)
     * @param messageBody     the message content from the contact form
     * @param attachmentBytes optional decoded PDF bytes; {@code null} means no attachment
     * @param attachmentName  the filename for the attachment (e.g. {@code "resume.pdf"});
     *                        ignored when {@code attachmentBytes} is {@code null}
     * @throws CustomException with {@link ErrorCode#EMAIL_SEND_FAILURE} (HTTP 500) if sending fails
     */
    public void sendContactEmail(String senderName, String senderEmail, String messageBody,
                                  byte[] attachmentBytes, String attachmentName) {
        log.info("Sending contact email via SES on behalf of: {}", senderEmail);
        try {
            if (attachmentBytes != null) {
                sendRawEmail(senderName, senderEmail, messageBody, attachmentBytes, attachmentName);
            } else {
                sendSimpleEmail(senderName, senderEmail, messageBody);
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

    // -------------------------------------------------------------------------
    // Private helpers – simple email (no attachment)
    // -------------------------------------------------------------------------

    private void sendSimpleEmail(String senderName, String senderEmail, String messageBody) {
        String subject = Constants.CONTACT_EMAIL_SUBJECT_PREFIX + senderName;
        Map<String, String> placeholders = buildPlaceholders(senderName, senderEmail, messageBody);

        Content subjectContent = Content.builder().data(subject).charset("UTF-8").build();
        Content textContent = Content.builder()
                .data(EmailTemplateLoader.load("contact-email.txt", placeholders))
                .charset("UTF-8").build();
        Content htmlContent = Content.builder()
                .data(EmailTemplateLoader.load("contact-email.html", placeholders))
                .charset("UTF-8").build();

        Body body = Body.builder().text(textContent).html(htmlContent).build();
        software.amazon.awssdk.services.ses.model.Message message =
                software.amazon.awssdk.services.ses.model.Message.builder()
                        .subject(subjectContent).body(body).build();

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder().toAddresses(destinationEmail).build())
                .replyToAddresses(senderEmail)
                .message(message)
                .build();

        sesClient.sendEmail(emailRequest);
    }

    // -------------------------------------------------------------------------
    // Private helpers – raw MIME email (with attachment)
    // -------------------------------------------------------------------------

    private void sendRawEmail(String senderName, String senderEmail, String messageBody,
                               byte[] attachmentBytes, String attachmentName) {
        try {
            byte[] rawMime = buildRawMimeMessage(senderName, senderEmail, messageBody,
                    attachmentBytes, attachmentName);

            SendRawEmailRequest rawRequest = SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteArray(rawMime))
                            .build())
                    .build();

            sesClient.sendRawEmail(rawRequest);
        } catch (MessagingException | IOException e) {
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to build MIME message: " + e.getMessage(), e);
        }
    }

    private byte[] buildRawMimeMessage(String senderName, String senderEmail, String messageBody,
                                        byte[] attachmentBytes, String attachmentName)
            throws MessagingException, IOException {

        String subject = Constants.CONTACT_EMAIL_SUBJECT_PREFIX + senderName;
        Map<String, String> placeholders = buildPlaceholders(senderName, senderEmail, messageBody);

        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(fromEmail));
        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(destinationEmail));
        mimeMessage.setReplyTo(new InternetAddress[]{new InternetAddress(senderEmail)});
        mimeMessage.setSubject(subject, "UTF-8");

        // Outer multipart/mixed container
        MimeMultipart mixed = new MimeMultipart("mixed");

        // Inner multipart/alternative for text + HTML bodies
        MimeBodyPart bodyPart = new MimeBodyPart();
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(
                EmailTemplateLoader.load("contact-email.txt", placeholders), Constants.CONTENT_TYPE_TEXT_PLAIN);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(
                EmailTemplateLoader.load("contact-email.html", placeholders), Constants.CONTENT_TYPE_TEXT_HTML);

        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        bodyPart.setContent(alternative);
        mixed.addBodyPart(bodyPart);

        // Attachment part
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(
                new DataHandler(new ByteArrayDataSource(attachmentBytes, Constants.CONTENT_TYPE_PDF)));
        attachmentPart.setFileName(attachmentName);
        mixed.addBodyPart(attachmentPart);

        mimeMessage.setContent(mixed);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mimeMessage.writeTo(out);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Private helpers – shared
    // -------------------------------------------------------------------------

    /**
     * Builds the template placeholder map, HTML-escaping the user-supplied values.
     */
    private Map<String, String> buildPlaceholders(String senderName, String senderEmail, String messageBody) {
        return Map.of(
                "{{NAME}}", htmlEscape(senderName),
                "{{EMAIL}}", htmlEscape(senderEmail),
                "{{MESSAGE}}", htmlEscape(messageBody).replace("\n", "<br/>")
        );
    }

    /**
     * Escapes HTML special characters to prevent injection in the email body.
     *
     * @param input raw input string
     * @return HTML-escaped string
     */
    private String htmlEscape(String input) {
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
    private String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                    "Missing required environment variable: " + name);
        }
        return value;
    }
}

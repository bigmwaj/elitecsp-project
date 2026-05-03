package ca.elitecsp.contact.service;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.Constants;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Service responsible for sending transactional emails via Amazon Simple Email Service (SES).
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
     * Sends a contact-form email via Amazon SES.
     *
     * <p>The email is delivered with both a plain-text fallback and a styled HTML body
     * using the company brand colour {@code #AC5055}.
     *
     * @param senderName  the name of the person who submitted the form
     * @param senderEmail the email address of the sender (used as Reply-To)
     * @param messageBody the message content from the contact form
     * @throws CustomException with {@link ErrorCode#EMAIL_SEND_FAILURE} (HTTP 500) if sending fails
     */
    public void sendContactEmail(String senderName, String senderEmail, String messageBody) {
        log.info("Sending contact email via SES on behalf of: {}", senderEmail);
        try {
            SendEmailRequest emailRequest = buildSendEmailRequest(senderName, senderEmail, messageBody);
            sesClient.sendEmail(emailRequest);
            log.info("Contact email sent successfully to {} on behalf of {}", destinationEmail, senderEmail);
        } catch (Exception e) {
            log.error("Failed to send contact email for sender: {}", senderEmail, e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to send email via SES: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the {@link SendEmailRequest} with subject, plain-text, and HTML bodies.
     */
    private SendEmailRequest buildSendEmailRequest(String senderName, String senderEmail, String messageBody) {
        String subject = Constants.CONTACT_EMAIL_SUBJECT_PREFIX + senderName;

        Content subjectContent = Content.builder()
                .data(subject)
                .charset("UTF-8")
                .build();

        Content textContent = Content.builder()
                .data(buildPlainTextBody(senderName, senderEmail, messageBody))
                .charset("UTF-8")
                .build();

        Content htmlContent = Content.builder()
                .data(buildHtmlBody(senderName, senderEmail, messageBody))
                .charset("UTF-8")
                .build();

        Body body = Body.builder()
                .text(textContent)
                .html(htmlContent)
                .build();

        Message message = Message.builder()
                .subject(subjectContent)
                .body(body)
                .build();

        return SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder()
                        .toAddresses(destinationEmail)
                        .build())
                .replyToAddresses(senderEmail)
                .message(message)
                .build();
    }

    /**
     * Formats the plain-text fallback email body.
     */
    private String buildPlainTextBody(String senderName, String senderEmail, String messageBody) {
        return String.format(
                "You have received a new message from the Elite CSP contact form.%n%n"
                + "Name:    %s%n"
                + "Email:   %s%n%n"
                + "Message:%n%s",
                senderName, senderEmail, messageBody
        );
    }

    /**
     * Builds a styled HTML email body using inline CSS and the company brand colour #AC5055.
     */
    private String buildHtmlBody(String senderName, String senderEmail, String messageBody) {
        String escapedName    = htmlEscape(senderName);
        String escapedEmail   = htmlEscape(senderEmail);
        String escapedMessage = htmlEscape(messageBody).replace("\n", "<br/>");

        return "<!DOCTYPE html>"
            + "<html lang=\"en\">"
            + "<head><meta charset=\"UTF-8\"><title>Contact Form</title></head>"
            + "<body style=\"margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;\">"
            + "  <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
            + "    <tr><td align=\"center\" style=\"padding:40px 20px;\">"
            + "      <table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
            + "             style=\"background-color:#ffffff;border-radius:8px;"
            + "                    box-shadow:0 2px 8px rgba(0,0,0,0.1);overflow:hidden;\">"
            + "        <tr>"
            + "          <td style=\"background-color:#AC5055;padding:24px 32px;\">"
            + "            <h1 style=\"margin:0;color:#ffffff;font-size:22px;\">Elite CSP – Contact Form Submission</h1>"
            + "          </td>"
            + "        </tr>"
            + "        <tr>"
            + "          <td style=\"padding:32px;\">"
            + "            <p style=\"margin:0 0 24px;color:#555555;font-size:15px;\">"
            + "              You have received a new message from the website contact form."
            + "            </p>"
            + "            <table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" border=\"0\""
            + "                   style=\"border-collapse:collapse;\">"
            + "              <tr style=\"background-color:#f9f9f9;\">"
            + "                <td style=\"width:120px;font-weight:bold;color:#AC5055;font-size:14px;"
            + "                           border-bottom:1px solid #eeeeee;\">Name</td>"
            + "                <td style=\"color:#333333;font-size:14px;"
            + "                           border-bottom:1px solid #eeeeee;\">" + escapedName + "</td>"
            + "              </tr>"
            + "              <tr>"
            + "                <td style=\"width:120px;font-weight:bold;color:#AC5055;font-size:14px;"
            + "                           border-bottom:1px solid #eeeeee;\">Email</td>"
            + "                <td style=\"color:#333333;font-size:14px;"
            + "                           border-bottom:1px solid #eeeeee;\">"
            + "                  <a href=\"mailto:" + escapedEmail + "\" style=\"color:#AC5055;\">"
            + escapedEmail + "</a></td>"
            + "              </tr>"
            + "              <tr style=\"background-color:#f9f9f9;\">"
            + "                <td style=\"width:120px;font-weight:bold;color:#AC5055;font-size:14px;"
            + "                           vertical-align:top;\">Message</td>"
            + "                <td style=\"color:#333333;font-size:14px;line-height:1.6;\">"
            + escapedMessage + "</td>"
            + "              </tr>"
            + "            </table>"
            + "          </td>"
            + "        </tr>"
            + "        <tr>"
            + "          <td style=\"background-color:#f4f4f4;padding:16px 32px;text-align:center;"
            + "                     color:#aaaaaa;font-size:12px;\">"
            + "            This message was sent via the Elite CSP website contact form."
            + "          </td>"
            + "        </tr>"
            + "      </table>"
            + "    </td></tr>"
            + "  </table>"
            + "</body></html>";
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

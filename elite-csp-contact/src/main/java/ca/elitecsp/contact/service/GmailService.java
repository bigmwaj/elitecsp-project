package ca.elitecsp.contact.service;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.Constants;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import lombok.extern.slf4j.Slf4j;


import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

/**
 * Service responsible for sending emails via the Google Gmail API using OAuth 2.0.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code CLIENT_ID}         – Google OAuth2 client ID</li>
 *   <li>{@code CLIENT_SECRET}     – Google OAuth2 client secret</li>
 *   <li>{@code REFRESH_TOKEN}     – OAuth2 refresh token for the sending account</li>
 *   <li>{@code DESTINATION_EMAIL} – The recipient email address</li>
 * </ul>
 */
@Slf4j
public class GmailService {

    private static final String ENV_CLIENT_ID = "CLIENT_ID";
    private static final String ENV_CLIENT_SECRET = "CLIENT_SECRET";
    private static final String ENV_REFRESH_TOKEN = "REFRESH_TOKEN";
    private static final String ENV_DESTINATION_EMAIL = "DESTINATION_EMAIL";

    /** OAuth2 client ID read from the environment. */
    private final String clientId;

    /** OAuth2 client secret read from the environment. */
    private final String clientSecret;

    /** OAuth2 refresh token for the Gmail sender account. */
    private final String refreshToken;

    /** Destination email address for contact messages. */
    private final String destinationEmail;

    /**
     * Constructs the service and reads credentials from environment variables.
     *
     * @throws CustomException if any required environment variable is missing
     */
    public GmailService() {
        this.clientId = requireEnv(ENV_CLIENT_ID);
        this.clientSecret = requireEnv(ENV_CLIENT_SECRET);
        this.refreshToken = requireEnv(ENV_REFRESH_TOKEN);
        this.destinationEmail = requireEnv(ENV_DESTINATION_EMAIL);
    }

    /**
     * Sends a contact email using the Gmail API.
     *
     * @param senderName  the name of the person who submitted the form
     * @param senderEmail the email address of the sender
     * @param messageBody the message content
     * @throws CustomException with {@link ErrorCode#EMAIL_SEND_FAILURE} (HTTP 500) if sending fails
     */
    public void sendContactEmail(String senderName, String senderEmail, String messageBody) {
        try {
            Gmail gmailClient = buildGmailClient();
            Message gmailMessage = buildGmailMessage(senderName, senderEmail, messageBody);
            gmailClient.users().messages().send(Constants.GMAIL_USER_ME, gmailMessage).execute();
            log.info("Contact email sent to {} on behalf of {}", destinationEmail, senderEmail);
        } catch (Exception e) {
            log.error("Failed to send contact email for sender: {}", senderEmail, e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILURE, 500,
                    "Failed to send email: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds and returns an authenticated {@link Gmail} client.
     *
     * @return configured Gmail client
     * @throws Exception if transport or credential initialisation fails
     */
    private Gmail buildGmailClient() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = buildCredential(httpTransport);
        return new Gmail.Builder(httpTransport, GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(Constants.GMAIL_APPLICATION_NAME)
                .build();
    }

    /**
     * Creates an OAuth2 {@link Credential} using the stored refresh token.
     *
     * @param httpTransport the HTTP transport to use
     * @return a refreshed OAuth2 credential
     * @throws Exception if credential refresh fails
     */
    @SuppressWarnings("deprecation")
    private Credential buildCredential(NetHttpTransport httpTransport) throws Exception {
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(clientId, clientSecret)
                .build();
        credential.setRefreshToken(refreshToken);
        credential.refreshToken();
        return credential;
    }

    /**
     * Builds a RFC-2822 email and wraps it in a Gmail {@link Message}.
     *
     * @param senderName  the display name of the sender
     * @param senderEmail the reply-to email address
     * @param messageBody the plain-text message content
     * @return a base64url-encoded Gmail message ready to send
     * @throws Exception if message creation fails
     */
    private Message buildGmailMessage(String senderName, String senderEmail, String messageBody) throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(senderEmail, senderName));
        mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(destinationEmail));
        mimeMessage.setSubject("Elite CSP – Contact Form: " + senderName);
        mimeMessage.setText(buildEmailBody(senderName, senderEmail, messageBody));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Formats the email body with sender details and message content.
     *
     * @param senderName  the sender's name
     * @param senderEmail the sender's email address
     * @param messageBody the message text
     * @return a formatted plain-text email body
     */
    private String buildEmailBody(String senderName, String senderEmail, String messageBody) {
        return String.format(
                "You have received a new message from the Elite CSP contact form.%n%n"
                + "Name:    %s%n"
                + "Email:   %s%n%n"
                + "Message:%n%s",
                senderName, senderEmail, messageBody
        );
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

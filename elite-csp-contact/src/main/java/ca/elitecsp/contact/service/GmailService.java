package ca.elitecsp.contact.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Properties;

/**
 * Service responsible for sending emails via the Google Gmail API using OAuth 2.0.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code CLIENT_ID}       – Google OAuth2 client ID</li>
 *   <li>{@code CLIENT_SECRET}   – Google OAuth2 client secret</li>
 *   <li>{@code REFRESH_TOKEN}   – OAuth2 refresh token for the sending account</li>
 *   <li>{@code DESTINATION_EMAIL} – The recipient email address</li>
 * </ul>
 */
public class GmailService {

    private static final String APPLICATION_NAME = "Elite CSP Contact";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String USER_ME = "me";

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
     * @throws IllegalStateException if any required environment variable is missing
     */
    public GmailService() {
        this.clientId = requireEnv("CLIENT_ID");
        this.clientSecret = requireEnv("CLIENT_SECRET");
        this.refreshToken = requireEnv("REFRESH_TOKEN");
        this.destinationEmail = requireEnv("DESTINATION_EMAIL");
    }

    /**
     * Sends a contact email using the Gmail API.
     *
     * @param senderName  the name of the person who submitted the form
     * @param senderEmail the email address of the sender
     * @param messageBody the message content
     * @throws Exception if the email could not be sent
     */
    public void sendContactEmail(String senderName, String senderEmail, String messageBody) throws Exception {
        Gmail gmailClient = buildGmailClient();
        Message gmailMessage = buildGmailMessage(senderName, senderEmail, messageBody);
        gmailClient.users().messages().send(USER_ME, gmailMessage).execute();
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
                .setApplicationName(APPLICATION_NAME)
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
     * Reads a required environment variable or throws {@link IllegalStateException}.
     *
     * @param name the environment variable name
     * @return the value of the environment variable
     * @throws IllegalStateException if the variable is not set or blank
     */
    private String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}

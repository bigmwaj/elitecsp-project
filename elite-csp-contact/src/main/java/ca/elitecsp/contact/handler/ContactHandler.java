package ca.elitecsp.contact.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import ca.elitecsp.contact.model.ContactRequest;
import ca.elitecsp.contact.service.GmailService;
import ca.elitecsp.contact.util.JsonUtil;
import ca.elitecsp.contact.util.ResponseBuilder;
import ca.elitecsp.contact.util.ValidationUtil;

/**
 * AWS Lambda handler for the contact form endpoint.
 *
 * <p>Triggered by Amazon API Gateway (proxy integration).
 * The handler:
 * <ol>
 *   <li>Parses the JSON body into a {@link ContactRequest}.</li>
 *   <li>Validates the request fields.</li>
 *   <li>Delegates email sending to {@link GmailService}.</li>
 *   <li>Returns a structured JSON response.</li>
 * </ol>
 *
 * <p>Handler reference for Lambda:
 * {@code handler.ca.elitecsp.contact.ContactHandler::handleRequest}
 */
public class ContactHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final GmailService gmailService;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Initialises {@link GmailService} which reads credentials from environment variables.
     */
    public ContactHandler() {
        this.gmailService = new GmailService();
    }

    /**
     * Constructor for dependency injection (useful in tests).
     *
     * @param gmailService the Gmail service to use
     */
    public ContactHandler(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    /**
     * Handles an API Gateway proxy request.
     *
     * @param request the API Gateway request event containing the JSON body
     * @param context the Lambda execution context
     * @return an {@link APIGatewayProxyResponseEvent} with a JSON body
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("ContactHandler invoked");

        try {
            ContactRequest contactRequest = parseRequest(request);
            ValidationUtil.validateContactRequest(contactRequest);
            gmailService.sendContactEmail(
                    contactRequest.getName(),
                    contactRequest.getEmail(),
                    contactRequest.getMessage()
            );
            context.getLogger().log("Email sent successfully for: " + contactRequest.getEmail());
            return ResponseBuilder.success("Your message has been sent successfully.");

        } catch (IllegalArgumentException e) {
            context.getLogger().log("Validation error: " + e.getMessage());
            return ResponseBuilder.badRequest(e.getMessage());

        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            return ResponseBuilder.internalError("An unexpected error occurred. Please try again later.");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the JSON request body into a {@link ContactRequest}.
     *
     * @param request the incoming API Gateway event
     * @return the parsed {@link ContactRequest}
     * @throws IllegalArgumentException if the body is missing or cannot be parsed
     */
    private ContactRequest parseRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
        return JsonUtil.fromJson(body, ContactRequest.class);
    }
}

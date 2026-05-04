package ca.elitecsp.contact.handler;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.response.ApiResponseBuilder;
import ca.elitecsp.common.util.JsonUtils;
import ca.elitecsp.common.util.ValidationUtils;
import ca.elitecsp.contact.model.ContactRequest;
import ca.elitecsp.contact.service.SESService;
import ca.elitecsp.contact.util.ValidationUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * AWS Lambda handler for the contact form endpoint.
 *
 * <p>Triggered by Amazon API Gateway (proxy integration).
 * The handler:
 * <ol>
 *   <li>Parses the JSON body into a {@link ContactRequest}.</li>
 *   <li>Validates the request fields (including any optional attachment).</li>
 *   <li>Delegates email sending to {@link SESService}.</li>
 *   <li>Returns a structured JSON response.</li>
 * </ol>
 *
 * <p>Handler reference for Lambda:
 * {@code ca.elitecsp.contact.handler.ContactHandler::handleRequest}
 */
@Slf4j
public class ContactHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SESService sesService;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Initialises {@link SESService} which reads configuration from environment variables.
     */
    public ContactHandler() {
        this.sesService = new SESService();
    }

    /**
     * Constructor for dependency injection (useful in tests).
     *
     * @param sesService the SES service to use
     */
    public ContactHandler(SESService sesService) {
        this.sesService = sesService;
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
        log.info("ContactHandler invoked");

        try {
            ContactRequest contactRequest = parseRequest(request);
            log.info("Contact request received from: {}", contactRequest.getEmail());
            ValidationUtil.validateContactRequest(contactRequest);

            byte[] attachmentBytes = null;
            if (!ValidationUtils.isBlank(contactRequest.getAttachmentFile())) {
                attachmentBytes = ValidationUtils.decodeBase64File(contactRequest.getAttachmentFile());
                log.info("Attachment '{}' included in contact request", contactRequest.getAttachmentFileName());
            }

            sesService.sendContactEmail(
                    contactRequest.getName(),
                    contactRequest.getEmail(),
                    contactRequest.getMessage(),
                    attachmentBytes,
                    contactRequest.getAttachmentFileName()
            );
            log.info("Email sent successfully for: {}", contactRequest.getEmail());
            return ApiResponseBuilder.success("Your message has been sent successfully.");

        } catch (CustomException e) {
            log.warn("Request error [{}]: {}", e.getErrorCode(), e.getMessage());
            return ApiResponseBuilder.fromException(e);

        } catch (Exception e) {
            log.error("Unexpected error processing contact request", e);
            return ApiResponseBuilder.internalError(
                    "An unexpected error occurred. Please try again later.",
                    ErrorCode.INTERNAL_ERROR.name());
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
     * @throws CustomException if the body is missing or cannot be parsed
     */
    private ContactRequest parseRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    "Request body must not be empty");
        }
        return JsonUtils.fromJson(body, ContactRequest.class);
    }
}

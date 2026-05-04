package ca.elitecsp.contact.handler;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.response.ApiResponseBuilder;
import ca.elitecsp.common.util.JsonUtils;
import ca.elitecsp.common.util.ValidationUtils;
import ca.elitecsp.contact.model.ContactRequest;
import ca.elitecsp.contact.model.ContactType;
import ca.elitecsp.contact.service.EmailService;
import ca.elitecsp.contact.util.ValidationUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * AWS Lambda handler for the contact / job-application endpoint.
 *
 * <p>Triggered by Amazon API Gateway (proxy integration).
 * The handler:
 * <ol>
 *   <li>Parses the JSON body into a {@link ContactRequest}.</li>
 *   <li>Validates the request fields according to the contact {@link ContactType}.</li>
 *   <li>Routes processing based on type:
 *       <ul>
 *         <li>{@link ContactType#CONTACT} – sends a notification email via SES
 *             (with optional inline attachment).</li>
 *         <li>{@link ContactType#JOB_APPLICATION} – sends a notification email via SES
 *             with the CV file attached directly.</li>
 *       </ul>
 *   </li>
 *   <li>Returns a structured JSON response.</li>
 * </ol>
 *
 * <p>Handler reference for Lambda:
 * {@code ca.elitecsp.contact.handler.LambdaHandler::handleRequest}
 */
@Slf4j
public class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final EmailService emailService;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Initialises {@link EmailService} from environment variables.
     */
    public LambdaHandler() {
        this.emailService = new EmailService();
    }

    /**
     * Constructor for dependency injection (useful in tests).
     *
     * @param emailService the email service to use
     */
    public LambdaHandler(EmailService emailService) {
        this.emailService = emailService;
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
        log.info("LambdaHandler invoked");

        try {
            ContactRequest contactRequest = parseRequest(request);
            ContactType type = contactRequest.getEffectiveType();
            log.info("Contact request received: type={}, email={}", type, contactRequest.getEmail());

            ValidationUtil.validateContactRequest(contactRequest);

            if (type == ContactType.JOB_APPLICATION) {
                return handleJobApplication(contactRequest);
            } else {
                return handleContact(contactRequest);
            }

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
    // Private routing helpers
    // -------------------------------------------------------------------------

    /**
     * Processes a standard contact-form submission.
     * Sends a notification email via SES, optionally with an inline attachment.
     *
     * @param req the validated contact request
     * @return a 200 success response
     */
    private APIGatewayProxyResponseEvent handleContact(ContactRequest req) {
        byte[] attachmentBytes = null;
        if (!ValidationUtils.isBlank(req.getAttachment())) {
            attachmentBytes = ValidationUtils.decodeBase64File(req.getAttachment());
            log.info("Attachment '{}' included in contact request", req.getAttachmentFileName());
        }

        emailService.sendContactEmail(
                req.getFullName(),
                req.getEmail(),
                req.getCity(),
                req.getSubject(),
                req.getMessage(),
                attachmentBytes,
                req.getAttachmentFileName()
        );
        log.info("Contact email sent successfully for: {}", req.getEmail());
        return ApiResponseBuilder.success("Your message has been sent successfully.");
    }

    /**
     * Processes a job-application submission.
     * Sends a notification email via SES with the CV attached directly.
     *
     * @param req the validated job-application request
     * @return a 200 success response
     */
    private APIGatewayProxyResponseEvent handleJobApplication(ContactRequest req) {
        byte[] fileBytes = ValidationUtils.decodeBase64File(req.getAttachment());
        log.info("Sending job application email with CV '{}' for applicant: {}",
                req.getAttachmentFileName(), req.getEmail());

        emailService.sendJobApplicationEmail(
                req.getFullName(),
                req.getEmail(),
                req.getCity(),
                req.getMessage(),
                fileBytes,
                req.getAttachmentFileName()
        );
        log.info("Job-application email sent successfully for: {}", req.getEmail());
        return ApiResponseBuilder.success("Your application has been submitted successfully.");
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

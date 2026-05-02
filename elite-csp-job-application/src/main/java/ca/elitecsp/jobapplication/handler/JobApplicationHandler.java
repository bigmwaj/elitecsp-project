package ca.elitecsp.jobapplication.handler;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.response.ApiResponseBuilder;
import ca.elitecsp.common.util.JsonUtils;
import ca.elitecsp.jobapplication.model.JobApplicationRequest;
import ca.elitecsp.jobapplication.service.S3Service;
import ca.elitecsp.jobapplication.util.ValidationUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * AWS Lambda handler for the job application submission endpoint.
 *
 * <p>Triggered by Amazon API Gateway (proxy integration).
 * The handler:
 * <ol>
 *   <li>Parses the JSON body into a {@link JobApplicationRequest}.</li>
 *   <li>Validates the request including CV file type and size.</li>
 *   <li>Decodes the Base64 CV and delegates the upload to {@link S3Service}.</li>
 *   <li>Returns a structured JSON response containing the uploaded file key.</li>
 * </ol>
 *
 * <p>Handler reference for Lambda:
 * {@code ca.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest}
 */
@Slf4j
public class JobApplicationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final S3Service s3Service;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Initialises {@link S3Service} which reads configuration from environment variables.
     */
    public JobApplicationHandler() {
        this.s3Service = new S3Service();
    }

    /**
     * Constructor for dependency injection (useful in tests).
     *
     * @param s3Service the S3 service to use
     */
    public JobApplicationHandler(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Handles an API Gateway proxy request for a job application submission.
     *
     * @param request the API Gateway request event containing the JSON body
     * @param context the Lambda execution context
     * @return an {@link APIGatewayProxyResponseEvent} with a JSON body
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        log.info("JobApplicationHandler invoked");

        try {
            JobApplicationRequest appRequest = parseRequest(request);
            byte[] cvBytes = ValidationUtil.validateAndDecodeCv(appRequest);

            String fileKey = s3Service.uploadApplication(appRequest, cvBytes);
            log.info("Application uploaded successfully. Key: {}", fileKey);
            return ApiResponseBuilder.success("Application submitted successfully. File key: " + fileKey);

        } catch (CustomException e) {
            log.warn("Request error [{}]: {}", e.getErrorCode(), e.getMessage());
            return ApiResponseBuilder.fromException(e);

        } catch (Exception e) {
            log.error("Unexpected error processing job application", e);
            return ApiResponseBuilder.internalError(
                    "An unexpected error occurred. Please try again later.",
                    ErrorCode.INTERNAL_ERROR.name());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the JSON request body into a {@link JobApplicationRequest}.
     *
     * @param request the incoming API Gateway event
     * @return the parsed {@link JobApplicationRequest}
     * @throws CustomException if the body is missing or cannot be parsed
     */
    private JobApplicationRequest parseRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    "Request body must not be empty");
        }
        return JsonUtils.fromJson(body, JobApplicationRequest.class);
    }
}

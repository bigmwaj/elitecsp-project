package com.elitecsp.jobapplication.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.elitecsp.jobapplication.model.JobApplicationRequest;
import com.elitecsp.jobapplication.service.S3Service;
import com.elitecsp.jobapplication.util.JsonUtil;
import com.elitecsp.jobapplication.util.ResponseBuilder;
import com.elitecsp.jobapplication.util.ValidationUtil;

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
 * {@code com.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest}
 */
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
        context.getLogger().log("JobApplicationHandler invoked");

        try {
            JobApplicationRequest appRequest = parseRequest(request);
            ValidationUtil.validateJobApplicationRequest(appRequest);

            byte[] cvBytes = ValidationUtil.decodeCv(appRequest.getCvFile());
            String fileKey = s3Service.uploadApplication(appRequest, cvBytes);

            context.getLogger().log("Application uploaded successfully. Key: " + fileKey);
            return ResponseBuilder.success("Application submitted successfully. File key: " + fileKey);

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
     * Parses the JSON request body into a {@link JobApplicationRequest}.
     *
     * @param request the incoming API Gateway event
     * @return the parsed {@link JobApplicationRequest}
     * @throws IllegalArgumentException if the body is missing or cannot be parsed
     */
    private JobApplicationRequest parseRequest(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
        return JsonUtil.fromJson(body, JobApplicationRequest.class);
    }
}

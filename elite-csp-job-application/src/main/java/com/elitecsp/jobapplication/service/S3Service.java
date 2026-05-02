package com.elitecsp.jobapplication.service;

import com.elitecsp.jobapplication.model.JobApplicationRequest;
import com.elitecsp.jobapplication.util.JsonUtil;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for uploading job application files and metadata to Amazon S3.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code S3_BUCKET_NAME} – name of the S3 bucket for uploads</li>
 *   <li>{@code AWS_REGION}     – AWS region (e.g. {@code eu-west-1}); optional if
 *       the Lambda execution role has a default region configured</li>
 * </ul>
 *
 * <p>File keys follow the pattern:
 * <pre>uploads/{year}/{month}/{uuid}.pdf</pre>
 */
public class S3Service {

    /** Environment variable key for the S3 bucket name. */
    private static final String ENV_BUCKET = "S3_BUCKET_NAME";

    /** Environment variable key for the AWS region. */
    private static final String ENV_REGION = "AWS_REGION";

    private final S3Client s3Client;
    private final String bucketName;

    /**
     * Default constructor used by the Lambda runtime.
     * Reads the bucket name and region from environment variables.
     *
     * @throws IllegalStateException if {@code S3_BUCKET_NAME} is not set
     */
    public S3Service() {
        this.bucketName = requireEnv(ENV_BUCKET);
        this.s3Client = buildS3Client();
    }

    /**
     * Constructor for dependency injection (useful in tests).
     *
     * @param s3Client   the S3 client to use
     * @param bucketName the name of the S3 bucket
     */
    public S3Service(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Uploads the applicant's CV and a JSON metadata file to S3.
     *
     * @param request the job application request containing the decoded CV bytes
     * @param cvBytes the decoded PDF bytes of the CV
     * @return the S3 key of the uploaded CV file
     */
    public String uploadApplication(JobApplicationRequest request, byte[] cvBytes) {
        String fileKey = buildFileKey();
        String metadataKey = fileKey.replace(".pdf", "-metadata.json");

        uploadCv(fileKey, cvBytes);
        uploadMetadata(metadataKey, request, fileKey);

        return fileKey;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a unique S3 key for the CV file.
     * Pattern: {@code uploads/{year}/{month}/{uuid}.pdf}
     *
     * @return the generated S3 key string
     */
    private String buildFileKey() {
        LocalDate today = LocalDate.now();
        return String.format("uploads/%d/%02d/%s.pdf",
                today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }

    /**
     * Uploads the CV PDF bytes to S3.
     *
     * @param key     the S3 object key
     * @param cvBytes the raw PDF file bytes
     */
    private void uploadCv(String key, byte[] cvBytes) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(cvBytes));
    }

    /**
     * Uploads a JSON metadata object alongside the CV.
     *
     * @param metadataKey the S3 key for the metadata JSON
     * @param request     the original job application request
     * @param cvKey       the S3 key of the uploaded CV file
     */
    private void uploadMetadata(String metadataKey, JobApplicationRequest request, String cvKey) {
        Map<String, String> metadata = buildMetadataMap(request, cvKey);
        byte[] metadataBytes = JsonUtil.toJson(metadata)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .contentType("application/json")
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(metadataBytes));
    }

    /**
     * Assembles the metadata map for the job application.
     *
     * @param request the job application request
     * @param cvKey   the S3 key where the CV was uploaded
     * @return a map of key-value pairs describing the application
     */
    private Map<String, String> buildMetadataMap(JobApplicationRequest request, String cvKey) {
        Map<String, String> map = new HashMap<>();
        map.put("fullName", request.getFullName());
        map.put("email", request.getEmail());
        map.put("city", request.getCity());
        map.put("jobTitle", request.getJobTitle());
        map.put("coverLetter", request.getCoverLetter() != null ? request.getCoverLetter() : "");
        map.put("cvKey", cvKey);
        map.put("submittedAt", Instant.now().toString());
        return map;
    }

    /**
     * Builds an {@link S3Client} using the region from the environment.
     *
     * @return a configured S3Client
     */
    private S3Client buildS3Client() {
        String regionName = System.getenv(ENV_REGION);
        if (regionName != null && !regionName.isBlank()) {
            return S3Client.builder()
                    .region(Region.of(regionName))
                    .build();
        }
        // Rely on the default region provider chain (IAM role, instance metadata, etc.)
        return S3Client.create();
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

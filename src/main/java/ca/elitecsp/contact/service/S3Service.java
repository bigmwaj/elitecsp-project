package ca.elitecsp.contact.service;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.Constants;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Service responsible for uploading job-application attachments to Amazon S3.
 *
 * <p>Each file is stored under the {@code uploads/} prefix with a UUID-based key,
 * preventing filename collisions.  The public HTTPS URL is derived from the bucket
 * name and region.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code S3_BUCKET_NAME} – the target S3 bucket (must already exist)</li>
 *   <li>{@code AWS_REGION}     – AWS region where the bucket resides (e.g. {@code us-east-1})</li>
 * </ul>
 *
 * <p>AWS credentials are resolved by the SDK's default credential chain
 * (Lambda execution role, environment variables, or instance profile) — no hardcoded secrets.
 */
@Slf4j
public class S3Service {

    private static final String ENV_S3_BUCKET_NAME = "S3_BUCKET_NAME";
    private static final String ENV_AWS_REGION = "AWS_REGION";

    /** Target S3 bucket for CV uploads. */
    private final String bucketName;

    /** AWS region string used for constructing the public file URL. */
    private final String awsRegion;

    /** S3 client initialised with the configured AWS region. */
    private final S3Client s3Client;

    /**
     * Default no-arg constructor used by the Lambda runtime.
     * Reads configuration from environment variables and initialises the S3 client.
     *
     * @throws CustomException if any required environment variable is missing
     */
    public S3Service() {
        this.bucketName = requireEnv(ENV_S3_BUCKET_NAME);
        this.awsRegion = requireEnv(ENV_AWS_REGION);
        this.s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Package-private constructor for dependency injection in tests.
     *
     * @param bucketName the target S3 bucket name
     * @param awsRegion  the AWS region (used for URL construction)
     * @param s3Client   pre-configured S3 client
     */
    S3Service(String bucketName, String awsRegion, S3Client s3Client) {
        this.bucketName = bucketName;
        this.awsRegion = awsRegion;
        this.s3Client = s3Client;
    }

    /**
     * Uploads a file to S3 and returns the generated S3 object key.
     *
     * <p>The key format is {@code uploads/{uuid}.{extension}}, where
     * {@code extension} is derived from the original filename.
     *
     * @param fileBytes        the file content to upload; must not be {@code null}
     * @param originalFileName the original filename used to determine the extension
     *                         (e.g. {@code "resume.pdf"}); must not be {@code null}
     * @return the S3 object key under which the file was stored
     * @throws CustomException with {@link ErrorCode#S3_UPLOAD_FAILURE} (HTTP 500) if the upload fails
     */
    public String upload(byte[] fileBytes, String originalFileName) {
        String extension = extractExtension(originalFileName);
        String s3Key = Constants.S3_UPLOADS_PREFIX + "/" + UUID.randomUUID() + "." + extension;
        String contentType = resolveContentType(extension);

        log.info("Uploading attachment to S3: bucket={}, key={}, size={}B", bucketName, s3Key, fileBytes.length);
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes));
            log.info("Attachment uploaded successfully: s3://{}/{}", bucketName, s3Key);
            return s3Key;
        } catch (Exception e) {
            log.error("Failed to upload attachment to S3: bucket={}, key={}", bucketName, s3Key, e);
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILURE, 500,
                    "Failed to upload attachment to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Constructs the public HTTPS URL for an S3 object.
     *
     * @param s3Key the S3 object key returned by {@link #upload}
     * @return the HTTPS URL of the object in the form
     *         {@code https://{bucket}.s3.{region}.amazonaws.com/{key}}
     */
    public String getFileUrl(String s3Key) {
        return "https://" + bucketName + ".s3." + awsRegion + ".amazonaws.com/" + s3Key;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the lowercase file extension from a filename.
     *
     * @param fileName the original filename (e.g. {@code "resume.PDF"})
     * @return the lowercase extension without the dot (e.g. {@code "pdf"}),
     *         or {@code "bin"} if no extension is found
     */
    private static String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Returns the appropriate MIME content type for a given file extension.
     *
     * @param extension lowercase file extension (e.g. {@code "pdf"}, {@code "docx"})
     * @return the MIME content-type string
     */
    private static String resolveContentType(String extension) {
        return switch (extension) {
            case "pdf"  -> Constants.CONTENT_TYPE_PDF;
            case "docx" -> Constants.CONTENT_TYPE_DOCX;
            default     -> "application/octet-stream";
        };
    }

    /**
     * Reads a required environment variable or throws {@link CustomException}.
     *
     * @param name the environment variable name
     * @return the value of the environment variable
     * @throws CustomException if the variable is not set or blank
     */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                    "Missing required environment variable: " + name);
        }
        return value;
    }
}

package ca.elitecsp.jobapplication.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;
import ca.elitecsp.common.util.ValidationUtils;
import ca.elitecsp.jobapplication.model.JobApplicationRequest;

/**
 * Validation helper for the job application request.
 *
 * <p>Delegates generic field, email, and file checks to {@link ValidationUtils} from the
 * shared common module, keeping only the job-application-domain orchestration here.
 */
public final class ValidationUtil {

    private ValidationUtil() {
        // Utility class – do not instantiate
    }

    /**
     * Validates all fields of a {@link JobApplicationRequest} and decodes the CV file.
     *
     * <p>Performs field presence checks, email format validation, Base64 decoding,
     * file size enforcement, and PDF magic-byte verification in a single pass.
     *
     * @param request the job application request to validate; must not be {@code null}
     * @return the decoded CV bytes ready for upload
     * @throws CustomException if any required field is missing, invalid, or the file fails validation
     */
    public static byte[] validateAndDecodeCv(JobApplicationRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, 400,
                    "Request body must not be null");
        }
        ValidationUtils.requireNonBlank(request.getFullName(), "Full name");
        ValidationUtils.requireValidEmail(request.getEmail(), "Email");
        ValidationUtils.requireNonBlank(request.getCity(), "City");
        ValidationUtils.requireNonBlank(request.getJobTitle(), "Job title");
        ValidationUtils.requireNonBlank(request.getCvFile(), "CV file");

        byte[] cvBytes = ValidationUtils.decodeBase64File(request.getCvFile());
        ValidationUtils.requireCvSizeWithinLimit(cvBytes);
        ValidationUtils.requirePdfMagicBytes(cvBytes);
        return cvBytes;
    }
}

package ca.elitecsp.jobapplication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a job application form submission.
 * The {@code cvFile} field contains the resume encoded as a Base64 string.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobApplicationRequest {

    /** Applicant's full name. */
    private String fullName;

    /** Applicant's email address. */
    private String email;

    /** Applicant's city of residence. */
    private String city;

    /** Job title the applicant is applying for. */
    private String jobTitle;

    /** Base64-encoded CV / resume file (PDF). */
    private String cvFile;

    /** Optional cover letter text. */
    private String coverLetter;
}

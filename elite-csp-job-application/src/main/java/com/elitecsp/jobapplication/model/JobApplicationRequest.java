package com.elitecsp.jobapplication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a job application form submission.
 * The {@code cvFile} field contains the resume encoded as a Base64 string.
 */
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

    public JobApplicationRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCvFile() {
        return cvFile;
    }

    public void setCvFile(String cvFile) {
        this.cvFile = cvFile;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }
}

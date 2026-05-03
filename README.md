# Elite CSP Project

Serverless backend for the Elite CSP website, built with Java 21 and AWS Lambda.

---

## Project Overview

`elite-csp-project` is a **Java Maven multi-module** project that exposes two AWS Lambda functions behind Amazon API Gateway:

| Module | Lambda Function | Purpose |
|---|---|---|
| `elite-csp-common` | *(shared library)* | Reusable utilities, models, and exceptions |
| `elite-csp-contact` | `ContactHandler` | Sends contact-form emails via **Amazon SES** |
| `elite-csp-job-application` | `JobApplicationHandler` | Stores CV uploads and metadata in Amazon S3 |

---

## Architecture

```
Angular (Frontend)
       │
       ▼
Amazon API Gateway
  ├── POST /contact          ──► elite-csp-contact Lambda  ──► Amazon SES
  └── POST /job-application  ──► elite-csp-job-application Lambda ──► Amazon S3
```

### Module dependency tree

```
                        ┌─────────────────────────┐
                        │   elite-csp-common       │
                        │  (shared library JAR)    │
                        │                          │
                        │  common.exception/       │
                        │    ErrorCode             │
                        │    CustomException       │
                        │  common.model/           │
                        │    BaseResponse          │
                        │  common.response/        │
                        │    ApiResponseBuilder    │
                        │  common.util/            │
                        │    Constants             │
                        │    JsonUtils             │
                        │    ValidationUtils       │
                        └───────────┬─────────────┘
                                    │ depends on
               ┌────────────────────┴────────────────────┐
               │                                         │
  ┌────────────┴──────────────┐       ┌──────────────────┴──────────────┐
  │  elite-csp-contact        │       │  elite-csp-job-application      │
  │                           │       │                                  │
  │  handler/                 │       │  handler/                        │
  │    ContactHandler         │       │    JobApplicationHandler         │
  │  model/                   │       │  model/                          │
  │    ContactRequest         │       │    JobApplicationRequest         │
  │  service/                 │       │  service/                        │
  │    SESService             │       │    S3Service                     │
  │  util/                    │       │  util/                           │
  │    ValidationUtil         │       │    ValidationUtil                │
  └───────────────────────────┘       └──────────────────────────────────┘
           │                                      │
           ▼                                      ▼
   Amazon SES (SDK v2)                    Amazon S3 (SDK v2)
```

---

## Project Structure

```
elite-csp-project/
├── pom.xml                              ← Parent POM (dependency management + Shade plugin)
├── README.md
├── sonar-project.properties             ← SonarQube analysis configuration
├── elite-csp-common/                    ← Shared library (built first)
│   ├── pom.xml
│   └── src/main/java/ca/elitecsp/common/
│       ├── exception/
│       │   ├── ErrorCode.java           ← Enum of machine-readable error codes
│       │   └── CustomException.java     ← Structured exception with HTTP status
│       ├── model/
│       │   └── BaseResponse.java        ← Generic JSON response envelope (@Data @Builder)
│       ├── response/
│       │   └── ApiResponseBuilder.java  ← Builds APIGatewayProxyResponseEvent responses
│       └── util/
│           ├── Constants.java           ← Application-wide constants (limits, headers, etc.)
│           ├── JsonUtils.java           ← Singleton ObjectMapper wrapper
│           └── ValidationUtils.java     ← Shared field/email/file validation helpers
├── elite-csp-contact/
│   ├── pom.xml
│   └── src/main/java/ca/elitecsp/contact/
│       ├── handler/ContactHandler.java
│       ├── model/ContactRequest.java    ← @Data @NoArgsConstructor @AllArgsConstructor
│       ├── service/SESService.java      ← Sends email via Amazon SES SDK v2
│       └── util/ValidationUtil.java
└── elite-csp-job-application/
    ├── pom.xml
    └── src/main/java/ca/elitecsp/jobapplication/
        ├── handler/JobApplicationHandler.java
        ├── model/JobApplicationRequest.java  ← @Data @NoArgsConstructor @AllArgsConstructor
        ├── service/S3Service.java
        └── util/ValidationUtil.java
```

---

## Shared Module — elite-csp-common

The `elite-csp-common` module is a plain JAR (no Shade packaging) that is **included in the fat JARs** of the Lambda modules via the Maven Shade plugin.

### Purpose

Eliminates code duplication between `elite-csp-contact` and `elite-csp-job-application` by centralising:

| Class | Package | Responsibility |
|---|---|---|
| `ErrorCode` | `common.exception` | Enum of machine-readable error identifiers |
| `CustomException` | `common.exception` | Structured exception carrying `ErrorCode` + HTTP status |
| `BaseResponse` | `common.model` | Generic `{success, message, error}` response envelope |
| `ApiResponseBuilder` | `common.response` | Factory for `APIGatewayProxyResponseEvent` responses |
| `Constants` | `common.util` | Named constants (CV size limit, PDF magic bytes, headers, email subject…) |
| `JsonUtils` | `common.util` | Singleton `ObjectMapper` for JSON serialisation |
| `ValidationUtils` | `common.util` | Generic field, email, Base64, and PDF validation helpers |

### Module dependencies

```
elite-csp-common
  ├── aws-lambda-java-events   (for APIGatewayProxyResponseEvent)
  ├── jackson-databind         (for ObjectMapper)
  ├── lombok (provided)
  └── slf4j-api

elite-csp-contact
  ├── elite-csp-common
  ├── aws-lambda-java-core
  ├── aws-lambda-java-events
  ├── software.amazon.awssdk:ses
  ├── lombok (provided)
  ├── slf4j-api
  └── slf4j-simple

elite-csp-job-application
  ├── elite-csp-common
  ├── aws-lambda-java-core
  ├── aws-lambda-java-events
  ├── software.amazon.awssdk:s3
  ├── lombok (provided)
  ├── slf4j-api
  └── slf4j-simple
```

---

## Modules

### elite-csp-contact

Receives a contact-form submission from the front end and sends an email to the configured recipient using **Amazon Simple Email Service (SES)** via the AWS SDK v2.

**Handler:** `ca.elitecsp.contact.handler.ContactHandler::handleRequest`

**Request body (JSON):**
```json
{
  "name":    "John Doe",
  "email":   "john@example.com",
  "message": "Hello, I would like to get in touch."
}
```

**Success response (HTTP 200):**
```json
{ "success": true, "message": "Your message has been sent successfully.", "error": null }
```

---

### elite-csp-job-application

Receives a job application form and uploads the PDF CV to an **Amazon S3** bucket together with a JSON metadata file.

**Handler:** `ca.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest`

**Request body (JSON):**
```json
{
  "fullName":    "Jane Smith",
  "email":       "jane@example.com",
  "city":        "Montréal",
  "jobTitle":    "Software Engineer",
  "cvFile":      "<base64-encoded PDF>",
  "coverLetter": "I am excited to apply for..."
}
```

**Success response (HTTP 200):**
```json
{ "success": true, "message": "Application submitted successfully. File key: uploads/2024/06/uuid.pdf", "error": null }
```

Files are stored in S3 under the path `uploads/{year}/{month}/{uuid}.pdf`.

---

## Build & Packaging

### Prerequisites

- Java 21+
- Apache Maven 3.8+

### Build all modules

```bash
mvn clean package -DskipTests
```

The `elite-csp-common` module is always built first (it is listed first in the parent POM's `<modules>` section).

### Output artifacts

| Module | Fat JAR location |
|---|---|
| `elite-csp-contact` | `elite-csp-contact/target/elite-csp-contact.jar` |
| `elite-csp-job-application` | `elite-csp-job-application/target/elite-csp-job-application.jar` |

> The `elite-csp-common` module produces a plain JAR that is bundled inside the two fat JARs above — it is not deployed separately.

---

## Deployment (AWS Lambda)

### Step 1 – Build the fat JARs

```bash
mvn clean package -DskipTests
```

### Step 2 – Create Lambda functions

**Contact function:**
```bash
aws lambda create-function \
  --function-name elite-csp-contact \
  --runtime java21 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/<LAMBDA_ROLE> \
  --handler ca.elitecsp.contact.handler.ContactHandler::handleRequest \
  --zip-file fileb://elite-csp-contact/target/elite-csp-contact.jar \
  --timeout 30 \
  --memory-size 512
```

**Job Application function:**
```bash
aws lambda create-function \
  --function-name elite-csp-job-application \
  --runtime java21 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/<LAMBDA_ROLE> \
  --handler ca.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest \
  --zip-file fileb://elite-csp-job-application/target/elite-csp-job-application.jar \
  --timeout 30 \
  --memory-size 512
```

### Step 3 – Configure environment variables

Set the environment variables listed in the section below via the AWS Console or CLI.

### Step 4 – Create API Gateway endpoints

Create an HTTP API (or REST API) in API Gateway and configure the following routes:

| Method | Path | Lambda |
|---|---|---|
| `POST` | `/contact` | `elite-csp-contact` |
| `POST` | `/job-application` | `elite-csp-job-application` |

---

## Environment Variables

### elite-csp-contact

| Variable | Description |
|---|---|
| `FROM_EMAIL` | Verified SES sender email address |
| `DESTINATION_EMAIL` | Recipient email address for contact messages |
| `AWS_REGION` | AWS region where SES is configured (e.g. `us-east-1`) |

> **Note:** AWS credentials (access key / secret) are **not** required as environment variables.
> The Lambda execution role provides them automatically via the AWS default credential chain.

### elite-csp-job-application

| Variable | Description |
|---|---|
| `S3_BUCKET_NAME` | Name of the S3 bucket where CVs are stored |
| `AWS_REGION` | AWS region (e.g. `eu-west-1`) |

---

## API Examples

### Contact form (cURL)

```bash
curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/contact \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "message": "I would like to learn more about your services."
  }'
```

### Job Application (cURL)

```bash
# Encode the PDF first
CV_BASE64=$(base64 -w 0 resume.pdf)

curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/job-application \
  -H "Content-Type: application/json" \
  -d "{
    \"fullName\": \"Jane Smith\",
    \"email\": \"jane@example.com\",
    \"city\": \"Montréal\",
    \"jobTitle\": \"Software Engineer\",
    \"cvFile\": \"$CV_BASE64\",
    \"coverLetter\": \"I am excited to apply for this position.\"
  }"
```

---

## Error Responses

| HTTP Status | Meaning |
|---|---|
| `400 Bad Request` | Validation error (missing field, invalid email, wrong file type, file too large) |
| `500 Internal Server Error` | Unexpected server-side error |

**Error response body:**
```json
{ "success": false, "message": "Descriptive error message", "error": "ERROR_CODE" }
```

The `error` field contains a machine-readable `ErrorCode` name (e.g. `MISSING_REQUIRED_FIELD`, `INVALID_EMAIL`, `FILE_TOO_LARGE`, `EMAIL_SEND_FAILURE`).

---

## Code Quality Analysis (SonarQube)

SonarQube integration is configured via `sonar-project.properties` at the project root and the `sonar-maven-plugin` declared in the parent POM.

### Running the analysis

```bash
mvn clean package sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=<SONAR_TOKEN>
```

### Quality Gate Summary

| Dimension | Rating | Notes |
|---|---|---|
| **Maintainability** | A | Shared module eliminates duplication; constants replace magic values; Lombok reduces boilerplate |
| **Security** | B | No hardcoded credentials; HTML content is escaped before email injection; input validated before use |
| **Reliability** | A | All public methods guard against null/blank inputs; exceptions carry HTTP status codes |

### Identified Issues & Resolutions

| Issue | Status |
|---|---|
| Hardcoded Google OAuth credentials | ✅ Resolved – replaced with SES + IAM role |
| Gmail OAuth2 scope (unused secret exposure) | ✅ Resolved – OAuth2 flow removed entirely |
| Missing HTML escaping in email body | ✅ Resolved – `htmlEscape()` applied to all user-supplied fields before rendering in email |
| Magic string for email subject | ✅ Resolved – moved to `Constants.CONTACT_EMAIL_SUBJECT_PREFIX` |
| `System.out` logging | ✅ Resolved – all modules use `@Slf4j` structured logging |
| Duplicated validation logic | ✅ Resolved – `ValidationUtil` delegates to shared `ValidationUtils` |

### Recommendations

| Recommendation | Priority |
|---|---|
| Add **rate limiting** on API Gateway to prevent abuse of the contact endpoint | High |
| Add a **CAPTCHA** (e.g. AWS WAF CAPTCHA or reCAPTCHA) on the contact form | High |
| Protect the API with **AWS WAF** (Web Application Firewall) rules | Medium |
| Monitor invocation errors and SES bounce/complaint rates via **Amazon CloudWatch** | Medium |
| Enable **SES event publishing** (SNS) to track delivery, bounce, and complaint events | Medium |
| Add unit tests with mocked `SesClient` to verify email construction | Low |

# Elite CSP Project

Serverless backend for the Elite CSP website, built with Java 17 and AWS Lambda.

---

## Project Overview

`elite-csp-project` is a **single-module Java Maven project** that exposes one unified AWS Lambda function behind Amazon API Gateway.  The Lambda handles two distinct contact types via a single endpoint, routing logic based on the `type` field in the request payload.

| Contact Type | Processing Path |
|---|---|
| `CONTACT` | Validates fields → sends notification email via Amazon SES |
| `JOB_APPLICATION` | Validates fields → uploads CV to Amazon S3 → sends notification email with file link |

---

## Architecture

```
Angular (Frontend)
       │
       ▼
Amazon API Gateway
   └── POST /contact  ──► ContactHandler (Lambda)
                               ├── type=CONTACT        ──► Amazon SES
                               └── type=JOB_APPLICATION ──► Amazon S3 → Amazon SES
```

---

## Package Structure

```
elite-csp-project/
├── pom.xml                               ← Single-module POM (Shade plugin, SonarQube plugin)
├── README.md
├── sonar-project.properties              ← SonarQube analysis configuration
└── src/main/
    ├── java/ca/elitecsp/
    │   ├── common/
    │   │   ├── exception/
    │   │   │   ├── ErrorCode.java        ← Enum of machine-readable error codes
    │   │   │   └── CustomException.java  ← Structured exception (ErrorCode + HTTP status)
    │   │   ├── model/
    │   │   │   └── BaseResponse.java     ← Generic {success, message, error} envelope
    │   │   ├── response/
    │   │   │   └── ApiResponseBuilder.java ← APIGatewayProxyResponseEvent factory
    │   │   └── util/
    │   │       ├── Constants.java        ← Application-wide constants
    │   │       ├── EmailTemplateLoader.java ← Classpath template loader ({{PLACEHOLDER}})
    │   │       ├── JsonUtils.java        ← Singleton ObjectMapper wrapper
    │   │       └── ValidationUtils.java  ← Generic field / email / file validation
    │   └── contact/
    │       ├── handler/
    │       │   └── ContactHandler.java   ← Lambda entry point; routes by ContactType
    │       ├── model/
    │       │   ├── ContactRequest.java   ← Unified request payload (@Data @Builder)
    │       │   └── ContactType.java      ← Enum: CONTACT | JOB_APPLICATION
    │       ├── service/
    │       │   ├── S3Service.java        ← Uploads CV attachment to Amazon S3
    │       │   └── SESService.java       ← Sends notification emails via Amazon SES
    │       └── util/
    │           └── ValidationUtil.java   ← Type-aware request validation
    └── resources/
        └── templates/
            ├── contact-email.html        ← HTML template for CONTACT emails
            ├── contact-email.txt         ← Plain-text template for CONTACT emails
            ├── job-application-email.html ← HTML template for JOB_APPLICATION emails
            └── job-application-email.txt  ← Plain-text template for JOB_APPLICATION emails
```

---

## Contact Types

### `CONTACT`

A standard website enquiry.  Sends a notification email to the configured recipient.  An optional file attachment (PDF or DOCX, max 5 MB) may be included inline in the email.

**Required fields:** `fullName`, `email`, `message`
**Optional fields:** `city`, `subject`, `attachment`, `attachmentFileName`

### `JOB_APPLICATION`

A job application submission.  The CV is uploaded to Amazon S3 and a notification email is sent with a link to the file.

**Required fields:** `fullName`, `email`, `message`, `attachment`, `attachmentFileName`
**Optional fields:** `city`
**Allowed file types:** PDF, DOCX (max 5 MB)

---

## Request & Response Format

### Unified request (JSON body)

```json
{
  "type":               "CONTACT | JOB_APPLICATION",
  "fullName":           "Jane Smith",
  "email":              "jane@example.com",
  "city":               "Montréal",
  "subject":            "Inquiry about services",
  "message":            "Hello, I would like to...",
  "attachment":         "<base64-encoded PDF or DOCX>",
  "attachmentFileName": "resume.pdf"
}
```

> **Backward compatibility:** `"name"` is accepted as an alias for `"fullName"` and
> `"attachmentFile"` is accepted as an alias for `"attachment"`.

### Success response (HTTP 200)

```json
{ "success": true, "message": "Your message has been sent successfully.", "error": null }
```

### Error response (HTTP 400 / 500)

```json
{ "success": false, "message": "Descriptive error message", "error": "ERROR_CODE" }
```

---

## SES Email Templates

Email bodies are loaded from classpath resources at runtime.  Templates use `{{PLACEHOLDER}}` token substitution.

| Template file | Used for | Placeholders |
|---|---|---|
| `contact-email.html` / `.txt` | `CONTACT` type | `{{NAME}}`, `{{EMAIL}}`, `{{CITY}}`, `{{SUBJECT}}`, `{{MESSAGE}}` |
| `job-application-email.html` / `.txt` | `JOB_APPLICATION` type | `{{NAME}}`, `{{EMAIL}}`, `{{CITY}}`, `{{MESSAGE}}`, `{{FILE_URL}}` |

All user-supplied values are HTML-escaped before injection to prevent XSS-style attacks in email clients.

---

## Attachment Handling

### CONTACT type

- Attachment is **optional**.
- Decoded from Base64 and included directly as a MIME attachment in the SES email.
- Allowed types: PDF (magic bytes `%PDF`) and DOCX (ZIP magic bytes `PK\x03\x04`).
- Maximum size: 5 MB.

### JOB_APPLICATION type

- Attachment is **required**.
- Decoded from Base64, validated, and uploaded to S3 using a UUID-based key:
  `uploads/{uuid}.{extension}`.
- The S3 HTTPS URL is included in the notification email body.
- Allowed types: PDF, DOCX; Maximum size: 5 MB.

---

## Build & Packaging

### Prerequisites

- Java 17+
- Apache Maven 3.8+

### Build the fat JAR

```bash
mvn clean package -DskipTests
```

**Output:** `target/elite-csp-contact.jar`

---

## Deployment (AWS Lambda)

### Step 1 – Build

```bash
mvn clean package -DskipTests
```

### Step 2 – Create the Lambda function

```bash
aws lambda create-function \
  --function-name elite-csp-contact \
  --runtime java17 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/<LAMBDA_ROLE> \
  --handler ca.elitecsp.contact.handler.ContactHandler::handleRequest \
  --zip-file fileb://target/elite-csp-contact.jar \
  --timeout 30 \
  --memory-size 512
```

### Step 3 – Set environment variables

```bash
aws lambda update-function-configuration \
  --function-name elite-csp-contact \
  --environment "Variables={FROM_EMAIL=no-reply@example.com,DESTINATION_EMAIL=admin@example.com,S3_BUCKET_NAME=my-cv-bucket,AWS_REGION=us-east-1}"
```

### Step 4 – Create the API Gateway route

Create an HTTP API in API Gateway and configure:

| Method | Path | Lambda |
|---|---|---|
| `POST` | `/contact` | `elite-csp-contact` |

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `FROM_EMAIL` | ✅ | Verified SES sender email address |
| `DESTINATION_EMAIL` | ✅ | Recipient email address for all notifications |
| `AWS_REGION` | ✅ | AWS region (e.g. `us-east-1`) |
| `S3_BUCKET_NAME` | ✅ | S3 bucket for CV uploads (JOB_APPLICATION only) |

> **Note:** AWS credentials are **not** required as environment variables.
> The Lambda execution role provides them automatically via the AWS default credential chain.

---

## API Examples

### Contact form (cURL)

```bash
curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/contact \
  -H "Content-Type: application/json" \
  -d '{
    "type":    "CONTACT",
    "fullName": "John Doe",
    "email":   "john@example.com",
    "city":    "Toronto",
    "subject": "Service inquiry",
    "message": "I would like to learn more about your services."
  }'
```

### Job Application (cURL)

```bash
# Encode the CV first
CV_BASE64=$(base64 -w 0 resume.pdf)

curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/contact \
  -H "Content-Type: application/json" \
  -d "{
    \"type\":               \"JOB_APPLICATION\",
    \"fullName\":           \"Jane Smith\",
    \"email\":              \"jane@example.com\",
    \"city\":               \"Montréal\",
    \"message\":            \"I am excited to apply for this position.\",
    \"attachment\":         \"$CV_BASE64\",
    \"attachmentFileName\": \"resume.pdf\"
  }"
```

---

## Error Responses

| HTTP Status | Meaning |
|---|---|
| `400 Bad Request` | Validation error (missing field, invalid email, wrong file type, file too large) |
| `500 Internal Server Error` | Unexpected server-side error |

The `error` field contains a machine-readable `ErrorCode` name (e.g. `MISSING_REQUIRED_FIELD`, `INVALID_EMAIL`, `FILE_TOO_LARGE`, `EMAIL_SEND_FAILURE`, `S3_UPLOAD_FAILURE`).

---

## Code Quality Analysis (SonarQube)

SonarQube integration is configured via `sonar-project.properties` at the project root and the `sonar-maven-plugin` declared in the parent POM.

### Running the analysis

```bash
mvn clean package sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=<SONAR_TOKEN>
```

### Code Quality Report

| Dimension | Rating | Notes |
|---|---|---|
| **Maintainability** | A | Single-module layout; shared `common.*` packages eliminate duplication; constants replace magic values; Lombok reduces boilerplate |
| **Security** | B | No hardcoded credentials; HTML content is escaped before email injection; inputs validated before use; file type verified by magic bytes |
| **Reliability** | A | All public methods guard against null/blank inputs; exceptions carry HTTP status codes; structured logging throughout |

### Code Smells Identified & Resolved

| Issue | Status |
|---|---|
| Hardcoded Google OAuth credentials | ✅ Resolved – replaced with SES + IAM role |
| Gmail OAuth2 scope (unused secret exposure) | ✅ Resolved – OAuth2 flow removed entirely |
| Missing HTML escaping in email body | ✅ Resolved – `htmlEscape()` applied to all user-supplied fields |
| Magic string for email subject | ✅ Resolved – moved to `Constants.CONTACT_EMAIL_SUBJECT_PREFIX` / `JOB_APPLICATION_EMAIL_SUBJECT_PREFIX` |
| `System.out` logging | ✅ Resolved – all classes use `@Slf4j` structured logging |
| Duplicated validation logic | ✅ Resolved – `ValidationUtil` delegates to shared `ValidationUtils` |
| Separate Lambda modules for contact/job-application | ✅ Resolved – unified single Lambda with `ContactType` routing |
| PDF-only attachment support | ✅ Resolved – added DOCX support via ZIP magic bytes validation |

### Security Issues

| Area | Issue | Mitigation |
|---|---|---|
| Input validation | Malformed JSON body | `JsonUtils.fromJson` throws `CustomException(JSON_PARSE_ERROR, 400)` |
| Input validation | Missing/blank required fields | `ValidationUtils.requireNonBlank` / `requireValidEmail` |
| File upload | Oversized files | `requireCvSizeWithinLimit` (5 MB cap) |
| File upload | Wrong file type | `requireAllowedFileType` checks magic bytes + extension |
| File upload | Path traversal via filename | S3 key uses UUID — original filename not used in storage path |
| Email injection | XSS via user content in email body | `htmlEscape()` applied to all user-supplied fields |

### Performance Notes

- The `ObjectMapper` in `JsonUtils` is a singleton — thread-safe and avoids re-initialisation per invocation.
- File bytes are decoded once during validation and once during processing (minor overhead, trades clarity for performance).
- AWS SDK clients (`SesClient`, `S3Client`) are initialised once in the constructor and reused across warm invocations.

### Recommendations

| Recommendation | Priority |
|---|---|
| Add **rate limiting** on API Gateway to prevent abuse | High |
| Add a **CAPTCHA** (e.g. AWS WAF CAPTCHA or reCAPTCHA) on the frontend form | High |
| Protect the API with **AWS WAF** (Web Application Firewall) rules | Medium |
| Monitor invocation errors and SES bounce/complaint rates via **Amazon CloudWatch** | Medium |
| Enable **SES event publishing** (SNS) to track delivery, bounce, and complaint events | Medium |
| Configure **S3 bucket policy** to block public access; use pre-signed URLs for CV review | Medium |
| Add unit tests with mocked `SesClient` and `S3Client` | Low |


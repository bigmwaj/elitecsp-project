# Elite CSP Project

Serverless backend for the Elite CSP website, built with Java 17 and AWS Lambda.

---

## Project Overview

`elite-csp-project` is a **Java Maven multi-module** project that exposes two AWS Lambda functions behind Amazon API Gateway:

| Module | Lambda Function | Purpose |
|---|---|---|
| `elite-csp-contact` | `ContactHandler` | Sends contact-form emails via the Gmail API (OAuth2) |
| `elite-csp-job-application` | `JobApplicationHandler` | Stores CV uploads and metadata in Amazon S3 |

---

## Project Structure

```
elite-csp-project/
├── pom.xml                          ← Parent POM (dependency management + Shade plugin)
├── README.md
├── elite-csp-contact/
│   ├── pom.xml
│   └── src/main/java/com/elitecsp/contact/
│       ├── handler/ContactHandler.java
│       ├── model/ContactRequest.java
│       ├── service/GmailService.java
│       └── util/
│           ├── JsonUtil.java
│           ├── ValidationUtil.java
│           └── ResponseBuilder.java
└── elite-csp-job-application/
    ├── pom.xml
    └── src/main/java/com/elitecsp/jobapplication/
        ├── handler/JobApplicationHandler.java
        ├── model/JobApplicationRequest.java
        ├── service/S3Service.java
        └── util/
            ├── JsonUtil.java
            ├── ValidationUtil.java
            └── ResponseBuilder.java
```

---

## Modules

### elite-csp-contact

Receives a contact-form submission from the front end and sends an email to the configured recipient using the **Google Gmail API** with OAuth 2.0.

**Handler:** `com.elitecsp.contact.handler.ContactHandler::handleRequest`

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
{ "success": true, "message": "Your message has been sent successfully." }
```

---

### elite-csp-job-application

Receives a job application form and uploads the PDF CV to an **Amazon S3** bucket together with a JSON metadata file.

**Handler:** `com.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest`

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
{ "success": true, "message": "Application submitted successfully. File key: uploads/2024/06/uuid.pdf" }
```

Files are stored in S3 under the path `uploads/{year}/{month}/{uuid}.pdf`.

---

## Build & Packaging

### Prerequisites

- Java 17+
- Apache Maven 3.8+

### Build all modules

```bash
mvn clean package -DskipTests
```

### Output artifacts

| Module | Fat JAR location |
|---|---|
| `elite-csp-contact` | `elite-csp-contact/target/elite-csp-contact.jar` |
| `elite-csp-job-application` | `elite-csp-job-application/target/elite-csp-job-application.jar` |

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
  --runtime java17 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/<LAMBDA_ROLE> \
  --handler com.elitecsp.contact.handler.ContactHandler::handleRequest \
  --zip-file fileb://elite-csp-contact/target/elite-csp-contact.jar \
  --timeout 30 \
  --memory-size 512
```

**Job Application function:**
```bash
aws lambda create-function \
  --function-name elite-csp-job-application \
  --runtime java17 \
  --role arn:aws:iam::<ACCOUNT_ID>:role/<LAMBDA_ROLE> \
  --handler com.elitecsp.jobapplication.handler.JobApplicationHandler::handleRequest \
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
| `CLIENT_ID` | Google OAuth2 client ID |
| `CLIENT_SECRET` | Google OAuth2 client secret |
| `REFRESH_TOKEN` | OAuth2 refresh token for the Gmail sender account |
| `DESTINATION_EMAIL` | Recipient email address for contact messages |

### elite-csp-job-application

| Variable | Description |
|---|---|
| `S3_BUCKET_NAME` | Name of the S3 bucket where CVs are stored |
| `AWS_REGION` | AWS region (e.g. `eu-west-1`). Optional if the Lambda execution role has a default region. |

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
{ "success": false, "message": "Descriptive error message" }
```

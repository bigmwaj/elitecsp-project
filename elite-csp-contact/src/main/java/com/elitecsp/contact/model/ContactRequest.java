package com.elitecsp.contact.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the incoming contact form request payload.
 * This model maps to the JSON body sent from the API Gateway.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactRequest {

    /** Full name of the person submitting the contact form. */
    private String name;

    /** Email address of the sender. */
    private String email;

    /** Message body of the contact form. */
    private String message;

    public ContactRequest() {
    }

    public ContactRequest(String name, String email, String message) {
        this.name = name;
        this.email = email;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

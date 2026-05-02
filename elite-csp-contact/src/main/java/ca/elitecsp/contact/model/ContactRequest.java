package ca.elitecsp.contact.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the incoming contact form request payload.
 * This model maps to the JSON body sent from the API Gateway.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactRequest {

    /** Full name of the person submitting the contact form. */
    private String name;

    /** Email address of the sender. */
    private String email;

    /** Message body of the contact form. */
    private String message;
}

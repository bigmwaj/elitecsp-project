package ca.elitecsp.common.util;

import ca.elitecsp.common.exception.CustomException;
import ca.elitecsp.common.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility for loading and populating email templates from classpath resources.
 *
 * <p>Templates are stored under {@code templates/} in the classpath and use
 * {@code {{PLACEHOLDER}}} syntax for variable substitution.
 *
 * <p>Example:
 * <pre>{@code
 * String html = EmailTemplateLoader.load("contact-email.html",
 *         Map.of("{{NAME}}", "Alice", "{{EMAIL}}", "alice@example.com", "{{MESSAGE}}", "Hello!"));
 * }</pre>
 */
public final class EmailTemplateLoader {

    private static final String TEMPLATE_DIR = "templates/";

    private EmailTemplateLoader() {
        // Utility class – do not instantiate
    }

    /**
     * Loads a template from the classpath and replaces all provided placeholders.
     *
     * @param templateName the template file name (e.g. {@code "contact-email.html"})
     * @param placeholders a map from placeholder tokens (e.g. {@code "{{NAME}}"}) to replacement values
     * @return the rendered template string
     * @throws CustomException with {@link ErrorCode#INTERNAL_ERROR} (HTTP 500) if the template cannot be read
     */
    public static String load(String templateName, Map<String, String> placeholders) {
        String templatePath = TEMPLATE_DIR + templateName;
        try (InputStream is = EmailTemplateLoader.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                        "Email template not found: " + templatePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue());
            }
            return content;
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 500,
                    "Failed to load email template: " + templatePath, e);
        }
    }
}

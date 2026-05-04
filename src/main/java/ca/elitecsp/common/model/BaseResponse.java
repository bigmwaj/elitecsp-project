package ca.elitecsp.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response envelope returned by all Elite CSP Lambda endpoints.
 *
 * <p>JSON representation:
 * <pre>{@code
 * {
 *   "success": true,
 *   "message": "Operation completed successfully.",
 *   "error":   null
 * }
 * }</pre>
 *
 * <p>Use the {@link #builder()} or the static factory methods on
 * {@link ca.elitecsp.common.response.ApiResponseBuilder} to construct instances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse {

    /** {@code true} when the operation succeeded; {@code false} on error. */
    private boolean success;

    /** Human-readable message describing the outcome. */
    private String message;

    /**
     * Machine-readable error description; {@code null} on success.
     * Populated from {@link ca.elitecsp.common.exception.ErrorCode#name()} on failure.
     */
    private String error;
}

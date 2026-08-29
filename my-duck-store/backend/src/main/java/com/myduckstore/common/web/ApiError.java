package com.myduckstore.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every failing endpoint.
 *
 * <p>{@code error} is a stable machine-readable code (e.g. {@code VALIDATION_ERROR}); callers
 * should branch on that rather than on the human-readable {@code message}. {@code details} is
 * present only for field-level validation failures and is omitted from the JSON otherwise.
 *
 * <p>Nothing here ever carries a stack trace, a SQL statement or a database constraint name -
 * see {@link GlobalExceptionHandler} for how internal failures are scrubbed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> details) {

    /** One rejected field, named so a client can highlight the offending input. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<FieldViolation> details) {
        return new ApiError(Instant.now(), status, error, message, path, details);
    }
}

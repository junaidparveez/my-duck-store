package com.myduckstore.warehouse.service;

/**
 * Thrown when a duck does not exist, or exists but has been logically deleted.
 *
 * <p>Mapped to HTTP 404 by {@code GlobalExceptionHandler}. The mapping lives there and not in a
 * {@code @ResponseStatus} annotation here, so the service layer carries no dependency on the
 * web layer and every error response is built in one place.
 */
public class DuckNotFoundException extends RuntimeException {

    public DuckNotFoundException(Long id) {
        super("Duck " + id + " does not exist");
    }
}

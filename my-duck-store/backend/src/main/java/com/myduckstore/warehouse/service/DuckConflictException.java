package com.myduckstore.warehouse.service;

/**
 * Thrown when a write cannot be applied without breaking the warehouse invariant
 * "at most one active duck per colour + size + price".
 *
 * <p>In practice this means a concurrent request created the conflicting combination between
 * our check and our write. The operation is safe to retry.
 *
 * <p>Mapped to HTTP 409 by {@code GlobalExceptionHandler}; this class deliberately knows
 * nothing about HTTP.
 */
public class DuckConflictException extends RuntimeException {

    public DuckConflictException(String message) {
        super(message);
    }
}

package com.myduckstore.store.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;

/**
 * Thrown when the warehouse holds no active stock for the requested colour and size, so no
 * unit price can be resolved and the order cannot be priced.
 *
 * <p>Mapped to HTTP 422 by {@code GlobalExceptionHandler}: the request is well-formed and
 * passes validation, but it cannot be fulfilled against the current warehouse contents.
 */
public class NoStockException extends RuntimeException {

    public NoStockException(Color color, Size size) {
        super("No active stock for " + color.getLabel() + " / " + size.getLabel());
    }
}

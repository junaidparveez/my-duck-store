package com.myduckstore.store.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when there is no active stock for the requested colour and size. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class NoStockException extends RuntimeException {

    public NoStockException(Color color, Size size) {
        super("No active stock for " + color.getLabel() + " / " + size.getLabel());
    }
}

package com.myduckstore.warehouse.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a duck does not exist, or exists but has been logically deleted. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DuckNotFoundException extends RuntimeException {

    public DuckNotFoundException(Long id) {
        super("Duck " + id + " does not exist");
    }
}

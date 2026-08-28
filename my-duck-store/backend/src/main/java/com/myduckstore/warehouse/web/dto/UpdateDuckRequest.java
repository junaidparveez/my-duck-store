package com.myduckstore.warehouse.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Payload for editing a duck.
 *
 * <p>There are deliberately no colour or size fields: "only quantity and price can be edited"
 * is enforced by the shape of the request, not by a check somewhere in the service.
 */
public record UpdateDuckRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal price,

        @NotNull
        @PositiveOrZero
        Integer quantity) {
}

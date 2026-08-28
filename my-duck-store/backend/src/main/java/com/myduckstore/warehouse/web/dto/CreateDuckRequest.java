package com.myduckstore.warehouse.web.dto;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Payload for adding stock. A duck with the same colour, size and price is merged, not duplicated. */
public record CreateDuckRequest(

        @NotNull
        Color color,

        @NotNull
        Size size,

        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal price,

        @NotNull
        @PositiveOrZero
        Integer quantity) {
}

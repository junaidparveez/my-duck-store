package com.myduckstore.store.web.dto;

import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The input for a pricing quote.
 *
 * <p>The request carries no price: the store resolves the lowest active price
 * for the requested colour and size from the warehouse stock.
 */
public record QuoteRequest(

        @NotNull
        Color color,

        @NotNull
        Size size,

        @NotNull
        @Positive
        Integer quantity,

        /** Destination country — any string; unknown countries attract the 15% surcharge. */
        @NotBlank
        String country,

        @NotNull
        ShippingMode shippingMode) {
}

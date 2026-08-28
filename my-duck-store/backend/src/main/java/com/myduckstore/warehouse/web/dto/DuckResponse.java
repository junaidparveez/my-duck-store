package com.myduckstore.warehouse.web.dto;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;

import java.math.BigDecimal;

/** What the API returns for a duck. The {@code deleted} flag is internal and never exposed. */
public record DuckResponse(Long id, Color color, Size size, BigDecimal price, int quantity) {

    public static DuckResponse from(Duck duck) {
        return new DuckResponse(duck.getId(), duck.getColor(), duck.getSize(), duck.getPrice(), duck.getQuantity());
    }
}

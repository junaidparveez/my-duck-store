package com.myduckstore.store.web.dto;

import java.math.BigDecimal;

/**
 * One itemized line in the order quote.
 *
 * <p>Each line is rounded to 2 decimal places before being stored here.
 * The total in {@link QuoteResponse} is the exact sum of all line amounts.
 */
public record BreakdownLine(String description, BigDecimal amount) {
}

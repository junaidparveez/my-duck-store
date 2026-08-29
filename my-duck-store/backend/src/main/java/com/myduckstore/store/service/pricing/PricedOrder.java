package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * The output of {@link PricingEngine}: the itemized lines and their total.
 *
 * <p>The two travel together because they are only meaningful together - {@code total} is by
 * construction the exact sum of the (already rounded) line amounts. Returning them as one value
 * means no caller can pair a breakdown with a total computed some other way.
 */
public record PricedOrder(List<BreakdownLine> breakdown, BigDecimal total) {
}

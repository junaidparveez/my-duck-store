package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rule 2: an order of <em>more than</em> 100 units takes 20% off the base cost.
 *
 * <p>The spec says "more than", so 100 units is not a bulk order and 101 is. When it does not apply
 * there is no line at all, which is what the API breakdown should say: nothing was discounted.
 */
public final class BulkDiscountRule implements PricingRule {

    private static final int THRESHOLD = 100;
    private static final BigDecimal RATE = new BigDecimal("0.20");

    @Override
    public Optional<BreakdownLine> apply(PricingContext context) {
        if (context.quantity() <= THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of(new BreakdownLine(
                "Bulk discount >100 units (−20%)",
                context.percentageOfBase(RATE).negate()));
    }
}

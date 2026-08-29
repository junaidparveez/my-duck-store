package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.util.Optional;

/** Rule 1: the total cost is quantity x unit price. Always applies; every other rule is relative to it. */
public final class BaseCostRule implements PricingRule {

    @Override
    public Optional<BreakdownLine> apply(PricingContext context) {
        return Optional.of(new BreakdownLine(
                "Base cost (" + context.quantity() + " × $" + context.unitPrice().toPlainString() + ")",
                context.base()));
    }
}

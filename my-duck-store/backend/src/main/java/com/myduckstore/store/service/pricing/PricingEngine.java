package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Applies every {@link PricingRule} to an order and assembles the priced result.
 *
 * <p>The engine owns two things the individual rules deliberately do not:
 *
 * <ol>
 *   <li><strong>The order.</strong> The rule list is built here, explicitly, in one readable place -
 *       not scattered across five files as {@code @Order} annotations. The sequence of breakdown
 *       lines is part of the API response, so it has to be deterministic and reviewable at a glance.
 *   <li><strong>The rounding.</strong> Rules return unrounded amounts; every line is rounded to
 *       cents here, exactly once, and the total is the sum of those rounded lines. That is what
 *       makes "the breakdown always adds up to the total" true by construction instead of by
 *       convention - a new rule cannot break it by forgetting to round.
 * </ol>
 *
 * <p>Adding a thirteenth pricing rule means writing one class and adding one line below. No existing
 * rule is touched, and nothing else in the application changes.
 */
@Component
public class PricingEngine {

    /** Section 3c, in the order the breakdown reads. */
    private final List<PricingRule> rules = List.of(
            new BaseCostRule(),              // rule 1
            new BulkDiscountRule(),          // rule 2
            new PackagingAdjustmentRule(),   // rules 3-5
            new DestinationSurchargeRule(),  // rules 6-9
            new ShippingChargeRule());       // rules 10-12

    public PricedOrder price(PricingContext context) {
        List<BreakdownLine> breakdown = rules.stream()
                .map(rule -> rule.apply(context))
                .flatMap(Optional::stream)
                .map(PricingEngine::toCents)
                .toList();

        BigDecimal total = breakdown.stream()
                .map(BreakdownLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PricedOrder(breakdown, total);
    }

    /** Rounds a line to 2 decimal places. The single place any money is rounded. */
    private static BreakdownLine toCents(BreakdownLine line) {
        return new BreakdownLine(line.description(), line.amount().setScale(2, RoundingMode.HALF_UP));
    }
}

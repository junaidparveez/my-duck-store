package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rules 3-5: what the box costs. Wood +5%, plastic +10%, cardboard -1% of the base cost.
 *
 * <p>The rule prices the package; it does not choose it. Choosing is
 * {@code PackagingPolicy}'s job, and the choice arrives already made on the context.
 */
public final class PackagingAdjustmentRule implements PricingRule {

    private static final BigDecimal RATE_WOOD      = new BigDecimal("0.05");
    private static final BigDecimal RATE_PLASTIC   = new BigDecimal("0.10");
    private static final BigDecimal RATE_CARDBOARD = new BigDecimal("0.01");

    @Override
    public Optional<BreakdownLine> apply(PricingContext context) {
        return Optional.of(switch (context.packageType()) {
            case WOOD -> new BreakdownLine(
                    "Wood packaging (+5%)", context.percentageOfBase(RATE_WOOD));
            case PLASTIC -> new BreakdownLine(
                    "Plastic packaging (+10%)", context.percentageOfBase(RATE_PLASTIC));
            case CARDBOARD -> new BreakdownLine(
                    "Cardboard packaging (−1%)", context.percentageOfBase(RATE_CARDBOARD).negate());
        });
    }
}

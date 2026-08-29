package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rules 10-12: the shipping charge, the only part of the quote that is not a percentage of the base.
 *
 * <ul>
 *   <li>Sea: a flat $400, however many units ship.
 *   <li>Land: $10 per unit.
 *   <li>Air: $30 per unit, and the <em>air charge alone</em> drops 15% above 1,000 units.
 * </ul>
 *
 * <p>The air taper is read as "more than 1000", matching the wording of rule 2, so 1000 units pay
 * the full charge and 1001 do not. It reduces the air charge only - not the total.
 */
public final class ShippingChargeRule implements PricingRule {

    private static final BigDecimal SEA_FLAT_FEE     = new BigDecimal("400.00");
    private static final BigDecimal LAND_PER_UNIT    = new BigDecimal("10");
    private static final BigDecimal AIR_PER_UNIT     = new BigDecimal("30");
    private static final int        AIR_TAPER_ABOVE  = 1000;
    private static final BigDecimal AIR_TAPER_FACTOR = new BigDecimal("0.85"); // keep 85% -> reduce by 15%

    @Override
    public Optional<BreakdownLine> apply(PricingContext context) {
        int quantity = context.quantity();

        return Optional.of(switch (context.shippingMode()) {
            case SEA -> new BreakdownLine("Sea shipping (flat fee)", SEA_FLAT_FEE);
            case LAND -> new BreakdownLine(
                    "Land shipping ($10 × " + quantity + " units)",
                    LAND_PER_UNIT.multiply(BigDecimal.valueOf(quantity)));
            case AIR -> air(quantity);
        });
    }

    private static BreakdownLine air(int quantity) {
        BigDecimal charge = AIR_PER_UNIT.multiply(BigDecimal.valueOf(quantity));

        if (quantity > AIR_TAPER_ABOVE) {
            return new BreakdownLine(
                    "Air shipping ($30 × " + quantity + " units, −15% above 1,000)",
                    charge.multiply(AIR_TAPER_FACTOR));
        }
        return new BreakdownLine("Air shipping ($30 × " + quantity + " units)", charge);
    }
}

package com.myduckstore.store.service.pricing;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.web.dto.BreakdownLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine's own contract, as opposed to the arithmetic of any one rule.
 *
 * <p>{@code PricingCalculationTest} already proves every number to the cent through the whole
 * service. What it cannot pin is the part that moved <em>into</em> the engine when the rules were
 * split out: the order the rules run in, and the fact that rounding happens exactly once, here.
 * Both are now decided in a single file, so they are worth a test that names them directly.
 */
class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();

    private static PricingContext order(int quantity, String unitPrice,
                                        PackageType packageType, ShippingMode mode, String country) {
        return PricingContext.of(quantity, new BigDecimal(unitPrice), packageType, mode, country);
    }

    @Test
    @DisplayName("rules run in the declared order, and that order is the order of the breakdown")
    void breakdownFollowsTheDeclaredRuleOrder() {
        // 101 units engages every rule, so all five lines appear.
        PricingContext context = order(101, "10.00", PackageType.WOOD, ShippingMode.AIR, "USA");

        assertThat(engine.price(context).breakdown())
                .extracting(BreakdownLine::description)
                .as("the sequence of lines is part of the API response, not an implementation detail")
                .satisfiesExactly(
                        base -> assertThat(base).startsWith("Base cost"),
                        bulk -> assertThat(bulk).startsWith("Bulk discount"),
                        pkg -> assertThat(pkg).startsWith("Wood packaging"),
                        dest -> assertThat(dest).startsWith("Destination USA"),
                        ship -> assertThat(ship).startsWith("Air shipping"));
    }

    @Test
    @DisplayName("a rule that does not apply contributes no line at all, not a zero line")
    void inapplicableRulesAreOmitted() {
        PricingContext context = order(100, "10.00", PackageType.WOOD, ShippingMode.AIR, "USA");

        assertThat(engine.price(context).breakdown())
                .extracting(BreakdownLine::description)
                .noneMatch(description -> description.contains("Bulk discount"))
                .hasSize(4);
    }

    @Test
    @DisplayName("every line is rounded to cents by the engine, and the total is their exact sum")
    void engineOwnsRoundingAndSummation() {
        // 3.33 x 101 = 336.33; each percentage of that lands on a third decimal place.
        PricedOrder priced = engine.price(
                order(101, "3.33", PackageType.WOOD, ShippingMode.SEA, "USA"));

        assertThat(priced.breakdown())
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line.amount().scale())
                        .as("rules return unrounded amounts; the engine is the only thing that rounds")
                        .isEqualTo(2));

        BigDecimal sum = priced.breakdown().stream()
                .map(BreakdownLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(priced.total()).isEqualByComparingTo(sum);
    }
}

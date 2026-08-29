package com.myduckstore.store.service;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.web.dto.BreakdownLine;
import com.myduckstore.store.web.dto.QuoteResponse;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes a price quote for an order.
 *
 * <p>All percentage surcharges and discounts are applied to the <em>base subtotal</em>
 * (quantity × unit price), not compounding on each other. Each breakdown line is
 * rounded to 2 decimal places individually; the total is the exact sum of those
 * rounded lines, so the breakdown always adds up to the total shown.
 *
 * <p>This is intentionally straight-line code (Phase 2). The pricing rules become
 * a list of small, independently testable rule objects in Phase 4.
 */
@Service
@Transactional(readOnly = true)
public class QuoteService {

    private static final BigDecimal RATE_BULK_DISCOUNT     = new BigDecimal("0.20");
    private static final BigDecimal RATE_WOOD_SURCHARGE    = new BigDecimal("0.05");
    private static final BigDecimal RATE_PLASTIC_SURCHARGE = new BigDecimal("0.10");
    private static final BigDecimal RATE_CARDBOARD_DISCOUNT= new BigDecimal("0.01");

    private static final BigDecimal RATE_USA     = new BigDecimal("0.18");
    private static final BigDecimal RATE_BOLIVIA = new BigDecimal("0.13");
    private static final BigDecimal RATE_INDIA   = new BigDecimal("0.19");
    private static final BigDecimal RATE_OTHER   = new BigDecimal("0.15");

    private static final BigDecimal SEA_FLAT_FEE          = new BigDecimal("400.00");
    private static final BigDecimal LAND_PER_UNIT         = new BigDecimal("10");
    private static final BigDecimal AIR_PER_UNIT          = new BigDecimal("30");
    private static final BigDecimal AIR_HIGH_VOL_DISCOUNT = new BigDecimal("0.85"); // keep 85% → reduce by 15%

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final DuckRepository repository;

    public QuoteService(DuckRepository repository) {
        this.repository = repository;
    }

    public QuoteResponse quote(Color color, Size size, int quantity, String country, ShippingMode shippingMode) {

        log.info("quote: color={}, size={}, quantity={}, country={}, shippingMode={}",
                color.getLabel(), size.getLabel(), quantity, country, shippingMode.getLabel());

        // Resolve price — lowest active price for this colour + size (decision #3 in the plan).
        BigDecimal unitPrice = repository.findLowestActivePriceByColorAndSize(color, size)
                .orElseThrow(() -> {
                    log.warn("quote: no active stock for color={}, size={}",
                            color.getLabel(), size.getLabel());
                    return new NoStockException(color, size);
                });

        log.debug("quote: resolved unit price={} for color={}, size={}",
                unitPrice, color.getLabel(), size.getLabel());

        PackageType packageType = resolvePackage(size);
        List<ProtectionMaterial> protections = resolveProtections(packageType, shippingMode);

        log.debug("quote: packageType={}, protections={}", packageType.getLabel(), protections);

        List<BreakdownLine> breakdown = new ArrayList<>();

        // ── Rule 1: base subtotal ────────────────────────────────────────────────
        BigDecimal base = unitPrice.multiply(BigDecimal.valueOf(quantity));
        breakdown.add(line(
                "Base cost (" + quantity + " × $" + unitPrice.toPlainString() + ")",
                base));

        // ── Rule 2: bulk discount — >100 units → −20% of base ───────────────────
        if (quantity > 100) {
            breakdown.add(line("Bulk discount >100 units (−20%)",
                    base.multiply(RATE_BULK_DISCOUNT).negate()));
        }

        // ── Rules 3–5: packaging surcharge / discount (% of base) ───────────────
        switch (packageType) {
            case WOOD      -> breakdown.add(line("Wood packaging (+5%)",      base.multiply(RATE_WOOD_SURCHARGE)));
            case PLASTIC   -> breakdown.add(line("Plastic packaging (+10%)",  base.multiply(RATE_PLASTIC_SURCHARGE)));
            case CARDBOARD -> breakdown.add(line("Cardboard packaging (−1%)", base.multiply(RATE_CARDBOARD_DISCOUNT).negate()));
        }

        // ── Rule 6: destination surcharge (% of base) ───────────────────────────
        String upperCountry = country.trim().toUpperCase();
        BigDecimal destRate;
        String destDesc;
        switch (upperCountry) {
            case "USA"     -> { destRate = RATE_USA;     destDesc = "Destination USA (+18%)";     }
            case "BOLIVIA" -> { destRate = RATE_BOLIVIA; destDesc = "Destination Bolivia (+13%)"; }
            case "INDIA"   -> { destRate = RATE_INDIA;   destDesc = "Destination India (+19%)";   }
            default        -> { destRate = RATE_OTHER;   destDesc = "Destination other (+15%)";   }
        }
        breakdown.add(line(destDesc, base.multiply(destRate)));

        // ── Rules 7–9: shipping charge ───────────────────────────────────────────
        switch (shippingMode) {
            case SEA -> breakdown.add(line("Sea shipping (flat fee)", SEA_FLAT_FEE));
            case LAND -> breakdown.add(line(
                    "Land shipping ($10 × " + quantity + " units)",
                    LAND_PER_UNIT.multiply(BigDecimal.valueOf(quantity))));
            case AIR -> {
                BigDecimal airCharge = AIR_PER_UNIT.multiply(BigDecimal.valueOf(quantity));
                String airDesc;
                if (quantity > 1000) {
                    airCharge = airCharge.multiply(AIR_HIGH_VOL_DISCOUNT);
                    airDesc = "Air shipping ($30 × " + quantity + " units, −15% above 1,000)";
                } else {
                    airDesc = "Air shipping ($30 × " + quantity + " units)";
                }
                breakdown.add(line(airDesc, airCharge));
            }
        }

        // Total = exact sum of individually-rounded lines (decision #6 in the plan).
        BigDecimal total = breakdown.stream()
                .map(BreakdownLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("quote: total={} (breakdown: {} lines) for color={}, size={}, quantity={}",
                total, breakdown.size(), color.getLabel(), size.getLabel(), quantity);

        return new QuoteResponse(packageType, protections, total, breakdown);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Rounds {@code amount} to 2dp and wraps it in a breakdown line. */
    private static BreakdownLine line(String description, BigDecimal amount) {
        return new BreakdownLine(description, amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Packaging rule: XLarge/Large → Wood; Medium → Cardboard; Small/XSmall → Plastic.
     */
    private static PackageType resolvePackage(Size size) {
        return switch (size) {
            case XLARGE, LARGE -> PackageType.WOOD;
            case MEDIUM        -> PackageType.CARDBOARD;
            case SMALL, XSMALL -> PackageType.PLASTIC;
        };
    }

    /**
     * Protection-material rule.
     *
     * <ul>
     *   <li>Air: polystyrene balls for wood/cardboard; bubble-wrap bags for plastic.
     *   <li>Land: polystyrene balls (all packages).
     *   <li>Sea: moisture-absorbing beads + bubble-wrap bags (all packages).
     * </ul>
     */
    private static List<ProtectionMaterial> resolveProtections(PackageType pkg, ShippingMode mode) {
        return switch (mode) {
            case AIR  -> pkg == PackageType.PLASTIC
                    ? List.of(ProtectionMaterial.BUBBLE_WRAP_BAGS)
                    : List.of(ProtectionMaterial.POLYSTYRENE_BALLS);
            case LAND -> List.of(ProtectionMaterial.POLYSTYRENE_BALLS);
            case SEA  -> List.of(ProtectionMaterial.MOISTURE_ABSORBING_BEADS, ProtectionMaterial.BUBBLE_WRAP_BAGS);
        };
    }
}

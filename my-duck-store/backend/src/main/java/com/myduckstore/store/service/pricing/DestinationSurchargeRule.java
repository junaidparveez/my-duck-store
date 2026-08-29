package com.myduckstore.store.service.pricing;

import com.myduckstore.store.web.dto.BreakdownLine;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rules 6-9: the destination surcharge. USA +18%, Bolivia +13%, India +19%, anywhere else +15%.
 *
 * <p>A lookup table, not four classes. The named countries are data, so a fifth country is a new map
 * entry rather than a new type - splitting a table into a hierarchy would be ceremony with no
 * benefit. The open-ended default is also why {@code country} is a free string on the request and
 * not an enum: rule 9 requires an open set.
 *
 * <p>Matching is case- and whitespace-insensitive, under {@link Locale#ROOT} so that a server
 * running in, say, a Turkish locale still matches "India" (a locale-sensitive upper-case turns
 * {@code i} into a dotted capital there).
 */
public final class DestinationSurchargeRule implements PricingRule {

    private record Surcharge(String description, BigDecimal rate) {
    }

    private static final Map<String, Surcharge> BY_COUNTRY = Map.of(
            "USA",     new Surcharge("Destination USA (+18%)",     new BigDecimal("0.18")),
            "BOLIVIA", new Surcharge("Destination Bolivia (+13%)", new BigDecimal("0.13")),
            "INDIA",   new Surcharge("Destination India (+19%)",   new BigDecimal("0.19")));

    private static final Surcharge ANYWHERE_ELSE =
            new Surcharge("Destination other (+15%)", new BigDecimal("0.15"));

    @Override
    public Optional<BreakdownLine> apply(PricingContext context) {
        Surcharge surcharge = BY_COUNTRY.getOrDefault(
                context.country().trim().toUpperCase(Locale.ROOT), ANYWHERE_ELSE);

        return Optional.of(new BreakdownLine(
                surcharge.description(), context.percentageOfBase(surcharge.rate())));
    }
}

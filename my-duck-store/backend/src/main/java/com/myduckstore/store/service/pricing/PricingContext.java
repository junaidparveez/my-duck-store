package com.myduckstore.store.service.pricing;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ShippingMode;

import java.math.BigDecimal;

/**
 * Everything a pricing rule is allowed to look at. Immutable, and free of Spring, HTTP and JPA, so a
 * rule can be unit-tested by handing it a literal.
 *
 * <p>{@code base} is quantity x unit price, computed once in {@link #of}. That is not a convenience:
 * every percentage rule in section 3c applies to the base subtotal and none of them compound, so
 * making the base a field of the context is what makes the non-compounding reading structural. A
 * rule literally cannot see a running total, which means it cannot accidentally price off one.
 */
public record PricingContext(
        int quantity,
        BigDecimal unitPrice,
        BigDecimal base,
        PackageType packageType,
        ShippingMode shippingMode,
        String country) {

    public static PricingContext of(int quantity,
                                    BigDecimal unitPrice,
                                    PackageType packageType,
                                    ShippingMode shippingMode,
                                    String country) {
        return new PricingContext(
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                packageType,
                shippingMode,
                country);
    }

    /** {@code base x rate} - the shape every percentage rule needs. Unrounded; the engine rounds. */
    public BigDecimal percentageOfBase(BigDecimal rate) {
        return base.multiply(rate);
    }
}

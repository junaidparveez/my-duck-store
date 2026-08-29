package com.myduckstore.store.service;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.service.packaging.PackagingPolicy;
import com.myduckstore.store.service.pricing.PricedOrder;
import com.myduckstore.store.service.pricing.PricingContext;
import com.myduckstore.store.service.pricing.PricingEngine;
import com.myduckstore.store.web.dto.QuoteResponse;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computes a price quote for an order.
 *
 * <p>This class orchestrates; it decides nothing. There are four steps and each belongs to somebody
 * else:
 *
 * <ol>
 *   <li>resolve a unit price from warehouse stock - the one thing only this class can do, because
 *       the order arrives without a price;
 *   <li>ask {@link PackagingPolicy} for the box and the protection materials (section 3b);
 *   <li>hand a {@link PricingContext} to {@link PricingEngine}, which applies each pricing rule in
 *       turn (section 3c);
 *   <li>assemble the response (section 3d).
 * </ol>
 *
 * <p>Consequently a change to a pricing rule does not touch this file, and a change to packaging
 * does not touch the pricing rules. {@code @Transactional(readOnly = true)} because quoting reads
 * stock and never reserves or consumes it.
 */
@Service
@Transactional(readOnly = true)
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final DuckRepository repository;
    private final PackagingPolicy packagingPolicy;
    private final PricingEngine pricingEngine;

    public QuoteService(DuckRepository repository,
                        PackagingPolicy packagingPolicy,
                        PricingEngine pricingEngine) {
        this.repository = repository;
        this.packagingPolicy = packagingPolicy;
        this.pricingEngine = pricingEngine;
    }

    public QuoteResponse quote(Color color, Size size, int quantity, String country, ShippingMode shippingMode) {

        log.info("quote: color={}, size={}, quantity={}, country={}, shippingMode={}",
                color.getLabel(), size.getLabel(), quantity, country, shippingMode.getLabel());

        // Resolve price - lowest active price for this colour + size (decision #3 in the README).
        BigDecimal unitPrice = repository.findLowestActivePriceByColorAndSize(color, size)
                .orElseThrow(() -> {
                    log.warn("quote: no active stock for color={}, size={}",
                            color.getLabel(), size.getLabel());
                    return new NoStockException(color, size);
                });

        log.debug("quote: resolved unit price={} for color={}, size={}",
                unitPrice, color.getLabel(), size.getLabel());

        PackageType packageType = packagingPolicy.packageFor(size);
        List<ProtectionMaterial> protections = packagingPolicy.protectionFor(packageType, shippingMode);

        log.debug("quote: packageType={}, protections={}", packageType.getLabel(), protections);

        PricedOrder priced = pricingEngine.price(
                PricingContext.of(quantity, unitPrice, packageType, shippingMode, country));

        log.info("quote: total={} (breakdown: {} lines) for color={}, size={}, quantity={}",
                priced.total(), priced.breakdown().size(),
                color.getLabel(), size.getLabel(), quantity);

        return new QuoteResponse(packageType, protections, priced.total(), priced.breakdown());
    }
}

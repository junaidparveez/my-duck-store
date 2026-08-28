package com.myduckstore.store.web.dto;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;

import java.math.BigDecimal;
import java.util.List;

/**
 * The result of a pricing quote.
 *
 * <p>{@code total} is exactly the sum of every {@code amount} in {@code breakdown}
 * — the two are always consistent by construction in {@link com.myduckstore.store.service.QuoteService}.
 */
public record QuoteResponse(
        PackageType packageType,
        List<ProtectionMaterial> protectionMaterials,
        BigDecimal total,
        List<BreakdownLine> breakdown) {
}

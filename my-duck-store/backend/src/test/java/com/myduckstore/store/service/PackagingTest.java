package com.myduckstore.store.service;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.service.packaging.PackagingPolicy;
import com.myduckstore.store.service.pricing.PricingEngine;
import com.myduckstore.store.web.dto.QuoteResponse;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The packaging and protection rules of assignment section 3b, covered exhaustively:
 * all five sizes against all three shipping modes.
 *
 * <p>Exhaustive is cheap here (15 rows) and worth it, because the rules interact - the protection
 * material depends on the package, which depends on the size, and sea overrides everything.
 * Spot-checking a few combinations would leave the interactions untested.
 */
class PackagingTest {

    private static QuoteResponse quote(Size size, ShippingMode mode) {
        DuckRepository repository = Mockito.mock(DuckRepository.class);
        Mockito.when(repository.findLowestActivePriceByColorAndSize(Color.RED, size))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        return new QuoteService(repository, new PackagingPolicy(), new PricingEngine())
                .quote(Color.RED, size, 1, "USA", mode);
    }

    @ParameterizedTest(name = "{0} -> {1} package")
    @CsvSource({
            "XLARGE, WOOD",
            "LARGE,  WOOD",
            "MEDIUM, CARDBOARD",
            "SMALL,  PLASTIC",
            "XSMALL, PLASTIC",
    })
    @DisplayName("package type is decided by size alone (rules 1-3)")
    void packageTypeFollowsSize(Size size, PackageType expected) {
        // The shipping mode must not influence the package choice.
        for (ShippingMode mode : ShippingMode.values()) {
            assertThat(quote(size, mode).packageType())
                    .as("%s shipped by %s", size, mode)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{0} by {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
        XLARGE | AIR  | POLYSTYRENE_BALLS
        LARGE  | AIR  | POLYSTYRENE_BALLS
        MEDIUM | AIR  | POLYSTYRENE_BALLS
        SMALL  | AIR  | BUBBLE_WRAP_BAGS
        XSMALL | AIR  | BUBBLE_WRAP_BAGS
        XLARGE | LAND | POLYSTYRENE_BALLS
        LARGE  | LAND | POLYSTYRENE_BALLS
        MEDIUM | LAND | POLYSTYRENE_BALLS
        SMALL  | LAND | POLYSTYRENE_BALLS
        XSMALL | LAND | POLYSTYRENE_BALLS
        """)
    @DisplayName("air and land use a single protection material (rules 4-6)")
    void airAndLandProtection(Size size, ShippingMode mode, ProtectionMaterial expected) {
        assertThat(quote(size, mode).protectionMaterials()).containsExactly(expected);
    }

    @ParameterizedTest(name = "{0} by sea")
    @CsvSource({"XLARGE", "LARGE", "MEDIUM", "SMALL", "XSMALL"})
    @DisplayName("sea always uses moisture-absorbing beads and bubble wrap, whatever the package (rule 7)")
    void seaProtectionIsAlwaysBoth(Size size) {
        assertThat(quote(size, ShippingMode.SEA).protectionMaterials())
                .containsExactlyInAnyOrder(
                        ProtectionMaterial.MOISTURE_ABSORBING_BEADS,
                        ProtectionMaterial.BUBBLE_WRAP_BAGS);
    }

    @Test
    @DisplayName("air is the only mode where the package changes the protection material")
    void airProtectionDependsOnPackage() {
        // Rule 4 (wood/cardboard) and rule 5 (plastic) differ only under air shipment. This is the
        // distinction the rules turn on, so it gets its own assertion rather than living inside
        // the table above.
        assertThat(quote(Size.LARGE, ShippingMode.AIR).protectionMaterials())
                .containsExactly(ProtectionMaterial.POLYSTYRENE_BALLS);
        assertThat(quote(Size.SMALL, ShippingMode.AIR).protectionMaterials())
                .containsExactly(ProtectionMaterial.BUBBLE_WRAP_BAGS);

        // ... whereas under land they are identical.
        assertThat(quote(Size.LARGE, ShippingMode.LAND).protectionMaterials())
                .isEqualTo(quote(Size.SMALL, ShippingMode.LAND).protectionMaterials());
    }
}

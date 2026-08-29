package com.myduckstore.store.service;

import com.myduckstore.store.domain.PackageType;
import com.myduckstore.store.domain.ProtectionMaterial;
import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.web.dto.QuoteResponse;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the QuoteService.
 *
 * A unit test isolates the class being tested. We use @Mock to create a "fake"
 * DuckRepository that returns whatever price we tell it to, without needing
 * a real database connection.
 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    // 1. The mock (the fake dependency)
    @Mock
    private DuckRepository duckRepository;

    // 2. The class we are actually testing
    private QuoteService quoteService;

    // 3. Setup that runs before every single @Test method
    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(duckRepository);
    }

    @Test
    void quote_throwsException_whenNoActiveStockExists() {
        // Arrange: Tell the fake repository to return empty (no price found)
        when(duckRepository.findLowestActivePriceByColorAndSize(Color.RED, Size.SMALL))
                .thenReturn(Optional.empty());

        // Act & Assert: Call the service and check that it throws our custom exception
        assertThatThrownBy(() -> quoteService.quote(Color.RED, Size.SMALL, 10, "USA", ShippingMode.AIR))
                .isInstanceOf(NoStockException.class);
    }

    @Test
    void quote_appliesBulkDiscount_whenQuantityOver100() {
        // Arrange: Tell the fake repo that a Red Small duck costs $10.00
        when(duckRepository.findLowestActivePriceByColorAndSize(Color.RED, Size.SMALL))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        // Act: Request a quote for 200 ducks
        // Base cost: 200 * $10 = $2000
        // Bulk discount (20%): -$400
        QuoteResponse response = quoteService.quote(Color.RED, Size.SMALL, 200, "USA", ShippingMode.AIR);

        // Assert: We check the breakdown list contains our discount line
        boolean hasDiscount = response.breakdown().stream()
                .anyMatch(line -> line.description().contains("Bulk discount")
                               && line.amount().compareTo(new BigDecimal("-400.00")) == 0);

        assertThat(hasDiscount).as("Should contain a -$400.00 bulk discount line").isTrue();
    }

    @Test
    void quote_resolvesPackagingAndProtection_correctly() {
        // Arrange: Large duck -> Wood packaging. Land shipping -> Polystyrene balls.
        when(duckRepository.findLowestActivePriceByColorAndSize(Color.RED, Size.LARGE))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        // Act
        QuoteResponse response = quoteService.quote(Color.RED, Size.LARGE, 10, "USA", ShippingMode.LAND);

        // Assert
        assertThat(response.packageType()).isEqualTo(PackageType.WOOD);
        assertThat(response.protectionMaterials()).containsExactly(ProtectionMaterial.POLYSTYRENE_BALLS);
    }
}

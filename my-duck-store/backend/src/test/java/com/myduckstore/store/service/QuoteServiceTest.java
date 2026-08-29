package com.myduckstore.store.service;

import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the store gets a price at all.
 *
 * <p>The order carries no price, so the service must resolve one from warehouse stock. The
 * arithmetic that follows is covered in {@link PricingCalculationTest} and the packaging in
 * {@code PackagingTest}; this class is only about the resolution step and what happens when it
 * fails.
 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private DuckRepository repository;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(repository);
    }

    @Test
    @DisplayName("resolves the unit price from the warehouse, keyed on colour and size")
    void resolvesPriceFromWarehouse() {
        when(repository.findLowestActivePriceByColorAndSize(Color.GREEN, Size.MEDIUM))
                .thenReturn(Optional.of(new BigDecimal("4.00")));

        // 25 x 4.00 = 100.00 base; cardboard -1% = -1.00; USA +18% = 18.00; sea +400.00
        assertThat(service.quote(Color.GREEN, Size.MEDIUM, 25, "USA", ShippingMode.SEA).total())
                .isEqualByComparingTo(new BigDecimal("517.00"));

        verify(repository).findLowestActivePriceByColorAndSize(Color.GREEN, Size.MEDIUM);
    }

    @Test
    @DisplayName("refuses to guess a price when nothing is stocked")
    void failsWhenNoActiveStockExists() {
        when(repository.findLowestActivePriceByColorAndSize(Color.RED, Size.SMALL))
                .thenReturn(Optional.empty());

        // Quoting a duck the warehouse has never held is not a validation error - the request is
        // well formed - so this surfaces as 422, not 400. See GlobalExceptionHandler.
        assertThatThrownBy(() -> service.quote(Color.RED, Size.SMALL, 10, "USA", ShippingMode.AIR))
                .isInstanceOf(NoStockException.class)
                .hasMessageContaining("Red / Small");
    }

    @Test
    @DisplayName("prices the order without reserving or consuming stock")
    void neverWritesToTheWarehouse() {
        when(repository.findLowestActivePriceByColorAndSize(Color.RED, Size.LARGE))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        service.quote(Color.RED, Size.LARGE, 5, "USA", ShippingMode.LAND);

        // A quote is a question, not an order: it reads a price and writes nothing. Asserted as
        // "no write ever happens" rather than "the repository was touched exactly once", so the
        // test still passes if the pricing internals are refactored to read more.
        verify(repository).findLowestActivePriceByColorAndSize(Color.RED, Size.LARGE);
        verify(repository, never()).upsert(any(), any(), any(), anyInt());
        verify(repository, never()).addQuantity(anyLong(), anyInt());
        verify(repository, never()).save(any());
    }
}

package com.myduckstore.warehouse.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the warehouse logic (DuckService).
 *
 * Proves that logical deletion works, and that updates affect the right fields.
 */
@ExtendWith(MockitoExtension.class)
class DuckServiceTest {

    @Mock
    private DuckRepository duckRepository;

    private DuckService duckService;

    @BeforeEach
    void setUp() {
        duckService = new DuckService(duckRepository);
    }

    @Test
    void delete_flagsDuckAsDeleted_insteadOfRemovingFromDatabase() {
        // Arrange
        Duck duck = new Duck(Color.RED, Size.LARGE, new BigDecimal("10.00"), 5);
        duck.setId(100L);
        duck.setDeleted(false);
        
        // When the service looks up ID 100, return our test duck
        when(duckRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(duck));

        // Act
        duckService.delete(100L);

        // Assert
        // We prove that "deletion" is logical. The row is just flagged, not dropped.
        assertThat(duck.isDeleted()).as("Duck should be flagged as deleted").isTrue();
    }

    @Test
    void delete_throwsException_whenDuckDoesNotExist() {
        // Arrange
        when(duckRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> duckService.delete(999L))
                .isInstanceOf(DuckNotFoundException.class);
    }

    @Test
    void update_changesPriceAndQuantity_butNotColorOrSize() {
        // Arrange
        Duck duck = new Duck(Color.RED, Size.LARGE, new BigDecimal("10.00"), 5);
        duck.setId(100L);
        when(duckRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(duck));

        // Act
        // We only provide a new price and quantity
        Duck updated = duckService.update(100L, new BigDecimal("15.00"), 20);

        // Assert
        assertThat(updated.getPrice()).isEqualByComparingTo("15.00");
        assertThat(updated.getQuantity()).isEqualTo(20);
        
        // These fields must never change during an update
        assertThat(updated.getColor()).isEqualTo(Color.RED);
        assertThat(updated.getSize()).isEqualTo(Size.LARGE);
    }
}

package com.myduckstore.warehouse.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Warehouse rules that can be decided without a database: what {@code add} reports, which fields an
 * edit may touch, when an edit folds, and that deletion is logical.
 *
 * <p>The rules that only a database can prove - the merge under concurrency, the sort order, the
 * partial unique index - live in {@code DuckMergeIntegrationTest} and
 * {@code DuckWarehouseIntegrationTest} against a real PostgreSQL instance.
 */
@ExtendWith(MockitoExtension.class)
class DuckServiceTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");
    private static final BigDecimal TWENTY = new BigDecimal("20.00");

    @Mock
    private DuckRepository repository;

    private DuckService service;

    @BeforeEach
    void setUp() {
        service = new DuckService(repository);
    }

    private static Duck duck(long id, Color color, Size size, BigDecimal price, int quantity) {
        Duck duck = new Duck(color, size, price, quantity);
        duck.setId(id);
        return duck;
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("reports 'created' when no duck with that colour, size and price exists")
        void reportsCreatedForNewCombination() {
            Duck stored = duck(1, Color.RED, Size.LARGE, TEN, 5);
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(Color.RED, Size.LARGE, TEN))
                    .thenReturn(Optional.empty())      // pre-check: nothing there yet
                    .thenReturn(Optional.of(stored));  // after the upsert

            DuckService.AddOutcome outcome = service.add(Color.RED, Size.LARGE, TEN, 5);

            assertThat(outcome.created()).isTrue();
            assertThat(outcome.duck()).isSameAs(stored);
            verify(repository).upsert(Color.RED, Size.LARGE, TEN, 5);
        }

        @Test
        @DisplayName("reports 'merged' when the same colour, size and price is already stocked")
        void reportsMergedForExistingCombination() {
            Duck existing = duck(1, Color.RED, Size.LARGE, TEN, 100);
            Duck merged = duck(1, Color.RED, Size.LARGE, TEN, 150);
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(Color.RED, Size.LARGE, TEN))
                    .thenReturn(Optional.of(existing))
                    .thenReturn(Optional.of(merged));

            DuckService.AddOutcome outcome = service.add(Color.RED, Size.LARGE, TEN, 50);

            assertThat(outcome.created()).as("this is a merge, not a new record").isFalse();
            assertThat(outcome.duck().getQuantity()).isEqualTo(150);
        }

        @Test
        @DisplayName("delegates the merge to the atomic upsert rather than reading and writing back")
        void delegatesMergeToTheDatabase() {
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(any(), any(), any()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(duck(1, Color.RED, Size.LARGE, TEN, 5)));

            service.add(Color.RED, Size.LARGE, TEN, 5);

            // A read-modify-write in Java would lose increments under concurrency; the quantity
            // must never be computed in application code on the add path.
            verify(repository).upsert(Color.RED, Size.LARGE, TEN, 5);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("changes price and quantity but never colour or size")
        void changesOnlyPriceAndQuantity() {
            Duck existing = duck(100, Color.RED, Size.LARGE, TEN, 5);
            when(repository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(existing));
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(Color.RED, Size.LARGE, new BigDecimal("15.00")))
                    .thenReturn(Optional.empty());

            Duck updated = service.update(100L, new BigDecimal("15.00"), 20);

            assertThat(updated.getPrice()).isEqualByComparingTo("15.00");
            assertThat(updated.getQuantity()).isEqualTo(20);
            assertThat(updated.getColor()).isEqualTo(Color.RED);
            assertThat(updated.getSize()).isEqualTo(Size.LARGE);
        }

        @Test
        @DisplayName("keeps its own id when the new price collides with nothing")
        void keepsIdWhenThereIsNoCollision() {
            Duck existing = duck(100, Color.RED, Size.LARGE, TEN, 5);
            when(repository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(existing));
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(Color.RED, Size.LARGE, TWENTY))
                    .thenReturn(Optional.empty());

            Duck updated = service.update(100L, TWENTY, 5);

            assertThat(updated.getId()).as("a price correction must not renumber the record")
                    .isEqualTo(100L);
            assertThat(updated.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("editing only the quantity does not look for a collision at all")
        void unchangedPriceSkipsTheCollisionCheck() {
            Duck existing = duck(100, Color.RED, Size.LARGE, TEN, 5);
            when(repository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(existing));

            // The same price expressed with a different scale is still the same price.
            Duck updated = service.update(100L, new BigDecimal("10.000"), 42);

            assertThat(updated.getQuantity()).isEqualTo(42);
            verify(repository, never())
                    .findByColorAndSizeAndPriceAndDeletedFalse(any(), any(), any());
        }

        @Test
        @DisplayName("folds into the existing duck when the new price already exists")
        void foldsIntoTheCollidingDuck() {
            Duck edited = duck(4, Color.RED, Size.XLARGE, new BigDecimal("22.00"), 21);
            Duck survivor = duck(1, Color.RED, Size.XLARGE, new BigDecimal("200.00"), 15000);
            Duck survivorAfter = duck(1, Color.RED, Size.XLARGE, new BigDecimal("200.00"), 15021);

            when(repository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(edited));
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(
                    Color.RED, Size.XLARGE, new BigDecimal("200.00")))
                    .thenReturn(Optional.of(survivor));
            when(repository.addQuantity(1L, 21)).thenReturn(1);
            when(repository.findById(1L)).thenReturn(Optional.of(survivorAfter));

            Duck result = service.update(4L, new BigDecimal("200.00"), 21);

            assertThat(edited.isDeleted())
                    .as("the edited row is retired logically, never physically").isTrue();
            assertThat(result.getId()).as("the surviving duck is returned").isEqualTo(1L);
            assertThat(result.getQuantity()).isEqualTo(15021);

            // The units must move via an atomic increment, not a quantity computed here.
            verify(repository).addQuantity(1L, 21);
        }

        @Test
        @DisplayName("reports a conflict when the surviving duck disappears mid-fold")
        void raisesConflictWhenTheSurvivorVanishes() {
            Duck edited = duck(4, Color.RED, Size.XLARGE, new BigDecimal("22.00"), 21);
            Duck survivor = duck(1, Color.RED, Size.XLARGE, new BigDecimal("200.00"), 15000);

            when(repository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(edited));
            when(repository.findByColorAndSizeAndPriceAndDeletedFalse(
                    Color.RED, Size.XLARGE, new BigDecimal("200.00")))
                    .thenReturn(Optional.of(survivor));
            // A concurrent request deleted the survivor between our read and our write.
            when(repository.addQuantity(1L, 21)).thenReturn(0);

            assertThatThrownBy(() -> service.update(4L, new BigDecimal("200.00"), 21))
                    .isInstanceOf(DuckConflictException.class)
                    .hasMessageContaining("Retry");
        }

        @Test
        @DisplayName("a deleted duck cannot be edited")
        void rejectsDeletedDuck() {
            when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(7L, TEN, 1))
                    .isInstanceOf(DuckNotFoundException.class);

            verify(repository, never()).addQuantity(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("flags the duck rather than removing the row")
        void isLogical() {
            Duck existing = duck(100, Color.RED, Size.LARGE, TEN, 5);
            when(repository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(existing));

            service.delete(100L);

            assertThat(existing.isDeleted()).isTrue();
            verify(repository, never()).delete(any());
            verify(repository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("an already-deleted duck is not found")
        void rejectsUnknownId() {
            when(repository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(999L))
                    .isInstanceOf(DuckNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}

package com.myduckstore.warehouse.service;

import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.service.NoStockException;
import com.myduckstore.store.service.QuoteService;
import com.myduckstore.support.AbstractPostgresIntegrationTest;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Warehouse behaviour that only a real database can demonstrate: what the listing query returns and
 * in what order, how logical deletion interacts with the partial unique index, and how the store
 * resolves a price across several stocked price points.
 */
class DuckWarehouseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DuckService duckService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private DuckRepository duckRepository;

    @BeforeEach
    void clearWarehouse() {
        duckRepository.deleteAll();
    }

    private Duck add(Color color, Size size, String price, int quantity) {
        return duckService.add(color, size, new BigDecimal(price), quantity).duck();
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("is sorted by quantity, lowest stock first")
        void sortedByQuantity() {
            add(Color.RED, Size.LARGE, "10.00", 500);
            add(Color.GREEN, Size.SMALL, "10.00", 7);
            add(Color.BLACK, Size.MEDIUM, "10.00", 92);

            assertThat(duckService.findAll())
                    .extracting(Duck::getQuantity)
                    .containsExactly(7, 92, 500);
        }

        @Test
        @DisplayName("breaks ties on id, so the order is stable across calls")
        void tieBreaksOnId() {
            Duck first = add(Color.RED, Size.LARGE, "10.00", 50);
            Duck second = add(Color.GREEN, Size.LARGE, "10.00", 50);
            Duck third = add(Color.BLACK, Size.LARGE, "10.00", 50);

            assertThat(duckService.findAll())
                    .extracting(Duck::getId)
                    .containsExactly(first.getId(), second.getId(), third.getId());
        }

        @Test
        @DisplayName("never shows a deleted duck, though the row is still in the table")
        void excludesDeletedButKeepsTheRow() {
            Duck kept = add(Color.RED, Size.LARGE, "10.00", 5);
            Duck removed = add(Color.GREEN, Size.SMALL, "10.00", 5);

            duckService.delete(removed.getId());

            assertThat(duckService.findAll())
                    .extracting(Duck::getId)
                    .containsExactly(kept.getId());

            assertThat(duckRepository.findById(removed.getId()))
                    .as("logical deletion: the row survives with deleted = true")
                    .get()
                    .extracting(Duck::isDeleted)
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("logical deletion and the unique index")
    class DeletionAndTheIndex {

        @Test
        @DisplayName("a colour, size and price can be stocked again after being deleted")
        void deletedCombinationCanBeReAdded() {
            Duck original = add(Color.RED, Size.LARGE, "10.00", 5);
            duckService.delete(original.getId());

            // The unique index is partial (WHERE deleted = false). A plain unique index would
            // permanently block this, because the deleted row never leaves the table.
            Duck reAdded = add(Color.RED, Size.LARGE, "10.00", 30);

            assertThat(reAdded.getId())
                    .as("a new record, not a resurrection of the deleted one")
                    .isNotEqualTo(original.getId());
            assertThat(reAdded.getQuantity())
                    .as("the deleted duck's 5 units must not come back")
                    .isEqualTo(30);
            assertThat(duckRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("adding never merges into a deleted duck")
        void addDoesNotMergeIntoDeletedStock() {
            Duck original = add(Color.RED, Size.LARGE, "10.00", 100);
            duckService.delete(original.getId());

            Duck fresh = add(Color.RED, Size.LARGE, "10.00", 1);

            assertThat(fresh.getQuantity()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("merges into the existing duck when the new price is already stocked")
        void editingOntoAnExistingPriceFolds() {
            Duck cheap = add(Color.RED, Size.XLARGE, "22.00", 21);
            Duck dear = add(Color.RED, Size.XLARGE, "200.00", 15000);

            // Before this was implemented, the partial unique index rejected the UPDATE and the
            // caller received a 500 carrying the SQL statement and the constraint name.
            Duck result = duckService.update(cheap.getId(), new BigDecimal("200.00"), 21);

            assertThat(result.getId()).isEqualTo(dear.getId());
            assertThat(result.getQuantity()).isEqualTo(15021);

            assertThat(duckService.findAll())
                    .as("one active duck per colour + size + price")
                    .extracting(Duck::getId)
                    .containsExactly(dear.getId());

            assertThat(duckRepository.findById(cheap.getId()))
                    .get()
                    .extracting(Duck::isDeleted)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("a plain price change keeps the duck's id")
        void editingToAFreePriceKeepsTheId() {
            Duck duck = add(Color.RED, Size.XLARGE, "22.00", 21);

            Duck result = duckService.update(duck.getId(), new BigDecimal("25.00"), 21);

            assertThat(result.getId()).isEqualTo(duck.getId());
            assertThat(result.getPrice()).isEqualByComparingTo("25.00");
            assertThat(duckRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("the same colour and size at a different price stays a separate record")
        void differentPricesDoNotMerge() {
            add(Color.RED, Size.LARGE, "10.00", 5);
            add(Color.RED, Size.LARGE, "12.00", 8);

            assertThat(duckService.findAll())
                    .extracting(Duck::getQuantity)
                    .containsExactly(5, 8);
        }
    }

    @Nested
    @DisplayName("price resolution for a quote")
    class PriceResolution {

        @Test
        @DisplayName("uses the lowest active price when several are stocked")
        void usesTheLowestActivePrice() {
            add(Color.RED, Size.LARGE, "200.00", 15000);
            add(Color.RED, Size.LARGE, "22.00", 21);
            add(Color.RED, Size.LARGE, "75.50", 40);

            // 10 x 22.00 = 220.00 base; wood +5% = 11.00; USA +18% = 39.60; land 10 x 10 = 100.00
            assertThat(quoteService.quote(Color.RED, Size.LARGE, 10, "USA", ShippingMode.LAND).total())
                    .isEqualByComparingTo(new BigDecimal("370.60"));
        }

        @Test
        @DisplayName("ignores deleted stock, even when it was the cheapest")
        void ignoresDeletedStock() {
            Duck cheapest = add(Color.RED, Size.LARGE, "1.00", 10);
            add(Color.RED, Size.LARGE, "22.00", 21);

            duckService.delete(cheapest.getId());

            // Falls back to 22.00, giving the same total as the test above.
            assertThat(quoteService.quote(Color.RED, Size.LARGE, 10, "USA", ShippingMode.LAND).total())
                    .isEqualByComparingTo(new BigDecimal("370.60"));
        }

        @Test
        @DisplayName("quotes an order larger than the stock on hand")
        void doesNotCheckAvailability() {
            add(Color.RED, Size.LARGE, "10.00", 3);

            // A quote prices a potential order; it neither reserves nor consumes stock, so asking
            // for more units than are held is a legitimate question. Documented in the README.
            assertThat(quoteService.quote(Color.RED, Size.LARGE, 500, "USA", ShippingMode.SEA).total())
                    .isNotNull();
        }

        @Test
        @DisplayName("refuses to price a colour and size with no active stock at all")
        void refusesWhenNothingIsStocked() {
            Duck only = add(Color.RED, Size.LARGE, "10.00", 5);
            duckService.delete(only.getId());

            assertThatThrownBy(() ->
                    quoteService.quote(Color.RED, Size.LARGE, 1, "USA", ShippingMode.SEA))
                    .isInstanceOf(NoStockException.class);
        }
    }

    @Nested
    @DisplayName("adding")
    class Adding {

        @Test
        @DisplayName("a repeated add merges into one record and sums the quantities")
        void repeatedAddMerges() {
            Duck first = add(Color.RED, Size.LARGE, "10.00", 100);
            Duck second = add(Color.RED, Size.LARGE, "10.00", 50);

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(second.getQuantity()).isEqualTo(150);

            List<Duck> all = duckRepository.findAll();
            assertThat(all).hasSize(1);
        }

        @Test
        @DisplayName("reports created for a new record and merged for a repeat")
        void reportsWhichPathWasTaken() {
            assertThat(duckService.add(Color.RED, Size.LARGE, new BigDecimal("10.00"), 5).created())
                    .isTrue();
            assertThat(duckService.add(Color.RED, Size.LARGE, new BigDecimal("10.00"), 5).created())
                    .isFalse();
        }

        @Test
        @DisplayName("a price is stored and compared to the cent, not by its written scale")
        void priceScaleDoesNotSplitRecords() {
            Duck first = add(Color.RED, Size.LARGE, "10.00", 5);
            Duck sameValue = add(Color.RED, Size.LARGE, "10.000", 5);

            assertThat(sameValue.getId())
                    .as("10.00 and 10.000 are the same price and must merge")
                    .isEqualTo(first.getId());
            assertThat(sameValue.getQuantity()).isEqualTo(10);
        }
    }
}

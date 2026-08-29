package com.myduckstore.store.service;

import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.web.dto.BreakdownLine;
import com.myduckstore.store.web.dto.QuoteResponse;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cent-exact tests for the pricing rules in section 3c of the assignment.
 *
 * <p>Every expected total in this class was computed by hand from the rules in the PDF, not by
 * running the code and recording what came out. That matters: it makes these tests an independent
 * oracle for the pricing maths, so they stay meaningful when {@code QuoteService} is later
 * refactored into rule objects. A test that merely echoes the implementation proves nothing.
 *
 * <p>The rules, and the reading applied (documented in the README):
 * <ol>
 *   <li>total cost = quantity x unit price</li>
 *   <li>more than 100 units: -20%</li>
 *   <li>wood +5%, plastic +10%, cardboard -1%</li>
 *   <li>USA +18%, Bolivia +13%, India +19%, anywhere else +15%</li>
 *   <li>sea +$400 flat, land +$10/unit, air +$30/unit (air charge -15% above 1000 units)</li>
 * </ol>
 * Every percentage applies to the <em>base</em> subtotal; they do not compound.
 */
class PricingCalculationTest {

    /** Prices a quote with the unit price stubbed, so no database is involved. */
    private static QuoteResponse quote(String unitPrice, Size size, int quantity,
                                       String country, ShippingMode mode) {
        DuckRepository repository = Mockito.mock(DuckRepository.class);
        Mockito.when(repository.findLowestActivePriceByColorAndSize(Color.RED, size))
                .thenReturn(Optional.of(new BigDecimal(unitPrice)));

        return new QuoteService(repository).quote(Color.RED, size, quantity, country, mode);
    }

    @Nested
    @DisplayName("worked examples, asserted to the cent")
    class WorkedExamples {

        /**
         * Each row is an order and the total a reader can verify with a calculator.
         *
         * <p>The two pairs of rows around 100 and 1000 units are the boundaries the spec's wording
         * turns on: it says "more than", so 100 gets no bulk discount and 101 does, and 1000 gets
         * the full air charge while 1001 gets it reduced.
         */
        @ParameterizedTest(name = "{5}")
        @CsvSource(delimiter = '|', textBlock = """
            10.00 | MEDIUM |    5 | USA      | AIR  |  208.50 | cardboard, USA, air
            10.00 | LARGE  |  100 | India    | SEA  | 1640.00 | exactly 100 units - no bulk discount
            10.00 | LARGE  |  101 | India    | SEA  | 1450.40 | 101 units - bulk discount applies
             1.00 | XSMALL | 1000 | Bolivia  | AIR  | 31030.00 | exactly 1000 units - full air charge
             1.00 | XSMALL | 1001 | Bolivia  | AIR  | 26556.53 | 1001 units - air charge reduced 15%
             2.50 | SMALL  |    4 | Germany  | LAND |   52.50 | unlisted country - 15% surcharge
             3.33 | XLARGE |  101 | USA      | SEA  |  746.42 | awkward cents, every rule engaged
            """)
        void totalIsExact(String unitPrice, Size size, int quantity, String country,
                          ShippingMode mode, BigDecimal expectedTotal, String description) {

            QuoteResponse response = quote(unitPrice, size, quantity, country, mode);

            assertThat(response.total())
                    .as(description)
                    .isEqualByComparingTo(expectedTotal);
        }
    }

    @Nested
    @DisplayName("destination surcharge (rules 6-9)")
    class DestinationSurcharge {

        /** Base is 100 x $1.00 = $100.00, so the surcharge line is the percentage in dollars. */
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "USA,     18.00",
                "Bolivia, 13.00",
                "India,   19.00",
                "Germany, 15.00",
                "Narnia,  15.00",
        })
        void surchargeMatchesCountry(String country, BigDecimal expectedSurcharge) {
            QuoteResponse response = quote("1.00", Size.MEDIUM, 100, country, ShippingMode.SEA);

            assertThat(amountOfLineContaining(response, "Destination"))
                    .isEqualByComparingTo(expectedSurcharge);
        }

        @ParameterizedTest(name = "{0} is matched regardless of case and padding")
        @CsvSource({"usa", "USA", "  UsA  "})
        void countryMatchingIsCaseAndWhitespaceInsensitive(String country) {
            QuoteResponse response = quote("1.00", Size.MEDIUM, 100, country, ShippingMode.SEA);

            assertThat(amountOfLineContaining(response, "Destination"))
                    .as("'%s' must be recognised as the USA, not fall through to the 15%% default",
                            country)
                    .isEqualByComparingTo(new BigDecimal("18.00"));
        }
    }

    @Nested
    @DisplayName("shipping charge (rules 10-12)")
    class ShippingCharge {

        @Test
        @DisplayName("sea is a flat $400 however many units are shipped")
        void seaIsFlat() {
            BigDecimal small = amountOfLineContaining(
                    quote("1.00", Size.MEDIUM, 1, "USA", ShippingMode.SEA), "Sea shipping");
            BigDecimal large = amountOfLineContaining(
                    quote("1.00", Size.MEDIUM, 5000, "USA", ShippingMode.SEA), "Sea shipping");

            assertThat(small).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(large).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("land is $10 per unit")
        void landIsPerUnit() {
            assertThat(amountOfLineContaining(
                    quote("1.00", Size.MEDIUM, 37, "USA", ShippingMode.LAND), "Land shipping"))
                    .isEqualByComparingTo(new BigDecimal("370.00"));
        }

        @Test
        @DisplayName("air is $30 per unit up to 1000 units")
        void airIsPerUnit() {
            assertThat(amountOfLineContaining(
                    quote("1.00", Size.MEDIUM, 1000, "USA", ShippingMode.AIR), "Air shipping"))
                    .isEqualByComparingTo(new BigDecimal("30000.00"));
        }

        @Test
        @DisplayName("above 1000 units the air charge - and only the air charge - drops 15%")
        void airIsReducedAboveOneThousandUnits() {
            QuoteResponse response = quote("1.00", Size.MEDIUM, 1001, "USA", ShippingMode.AIR);

            // 30 x 1001 = 30,030.00, less 15% = 25,525.50
            assertThat(amountOfLineContaining(response, "Air shipping"))
                    .isEqualByComparingTo(new BigDecimal("25525.50"));
        }
    }

    @Nested
    @DisplayName("bulk discount (rule 2)")
    class BulkDiscount {

        @Test
        @DisplayName("100 units is not 'more than 100' - no discount line at all")
        void noDiscountAtExactlyOneHundred() {
            QuoteResponse response = quote("10.00", Size.MEDIUM, 100, "USA", ShippingMode.SEA);

            assertThat(response.breakdown())
                    .extracting(BreakdownLine::description)
                    .noneMatch(description -> description.contains("Bulk discount"));
        }

        @Test
        @DisplayName("101 units discounts 20% of the base cost")
        void discountAppliesAboveOneHundred() {
            QuoteResponse response = quote("10.00", Size.MEDIUM, 101, "USA", ShippingMode.SEA);

            // base 1010.00, less 20% = -202.00
            assertThat(amountOfLineContaining(response, "Bulk discount"))
                    .isEqualByComparingTo(new BigDecimal("-202.00"));
        }
    }

    @Nested
    @DisplayName("packaging adjustment (rules 3-5)")
    class PackagingAdjustment {

        /** Base is 10 x $10.00 = $100.00, so each adjustment is the percentage in dollars. */
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "XLARGE,   5.00",
                "LARGE,    5.00",
                "MEDIUM,  -1.00",
                "SMALL,   10.00",
                "XSMALL,  10.00",
        })
        void adjustmentMatchesPackage(Size size, BigDecimal expected) {
            QuoteResponse response = quote("10.00", size, 10, "USA", ShippingMode.SEA);

            assertThat(amountOfLineContaining(response, "packaging"))
                    .isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("the breakdown and the total agree")
    class BreakdownConsistency {

        /**
         * The response promises that {@code total} is exactly the sum of the breakdown lines.
         * With per-line rounding that is a claim worth checking on figures that do not divide
         * cleanly - here every one of the four percentage rules produces a third decimal place.
         */
        @Test
        @DisplayName("lines sum exactly to the total, even when every rule rounds")
        void linesSumToTotal() {
            QuoteResponse response = quote("3.33", Size.XLARGE, 101, "USA", ShippingMode.SEA);

            BigDecimal sum = response.breakdown().stream()
                    .map(BreakdownLine::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(sum).isEqualByComparingTo(response.total());
            assertThat(response.total()).isEqualByComparingTo(new BigDecimal("746.42"));
        }

        @Test
        @DisplayName("every amount is stated in whole cents")
        void everyLineIsRoundedToCents() {
            QuoteResponse response = quote("3.33", Size.XLARGE, 101, "USA", ShippingMode.SEA);

            assertThat(response.breakdown())
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line.amount().scale()).isEqualTo(2));
        }

        @Test
        @DisplayName("an order with no discounts still itemises base, packaging, destination and shipping")
        void breakdownItemisesEveryRuleApplied() {
            QuoteResponse response = quote("10.00", Size.LARGE, 10, "USA", ShippingMode.LAND);

            assertThat(response.breakdown())
                    .extracting(BreakdownLine::description)
                    .hasSize(4)
                    .anyMatch(d -> d.contains("Base cost"))
                    .anyMatch(d -> d.contains("Wood packaging"))
                    .anyMatch(d -> d.contains("Destination USA"))
                    .anyMatch(d -> d.contains("Land shipping"));
        }
    }

    // -- Helpers -----------------------------------------------------------------

    /** The amount of the single breakdown line whose description contains {@code fragment}. */
    private static BigDecimal amountOfLineContaining(QuoteResponse response, String fragment) {
        return response.breakdown().stream()
                .filter(line -> line.description().contains(fragment))
                .map(BreakdownLine::amount)
                .reduce((a, b) -> {
                    throw new AssertionError("more than one breakdown line contains " + fragment);
                })
                .orElseThrow(() -> new AssertionError(
                        "no breakdown line contains '" + fragment + "' in " + response.breakdown()));
    }
}

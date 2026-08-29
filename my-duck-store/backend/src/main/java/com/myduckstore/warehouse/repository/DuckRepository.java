package com.myduckstore.warehouse.repository;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Every finder filters on {@code deleted = false}, so "deleted ducks never appear" is a
 * property of the queries rather than something each caller has to remember.
 */
public interface DuckRepository extends JpaRepository<Duck, Long> {

    List<Duck> findByDeletedFalseOrderByQuantityAscIdAsc();

    Optional<Duck> findByIdAndDeletedFalse(Long id);

    Optional<Duck> findByColorAndSizeAndPriceAndDeletedFalse(Color color, Size size, BigDecimal price);

    /**
     * Returns the lowest unit price among active (non-deleted) ducks of the given colour and size.
     *
     * <p>Used by the store module to resolve the price for a quote. See the price-resolution
     * decision in the README.
     *
     * <p>{@code MIN} over an empty set yields SQL NULL, which Spring Data maps to an empty
     * {@link Optional} - so "no active stock" and "no rows" are the same answer here.
     */
    @Query("SELECT MIN(d.price) FROM Duck d WHERE d.color = :color AND d.size = :size AND d.deleted = false")
    Optional<BigDecimal> findLowestActivePriceByColorAndSize(@Param("color") Color color,
                                                             @Param("size") Size size);

    /**
     * Adds stock for a colour + size + price, merging into the existing active row if there is one.
     *
     * <p>This is the warehouse's single atomic "add stock" primitive, and the whole merge
     * invariant rests on it. A find-then-insert in Java is not atomic: two concurrent requests can
     * both find nothing and both insert. Here PostgreSQL evaluates the conflict and the increment
     * inside one statement, taking a row lock for the duration, so concurrent callers serialise on
     * the row instead of racing.
     *
     * <p>The conflict target repeats the {@code WHERE deleted = false} predicate because the
     * unique index is partial - deletion is logical, so deleted rows stay in the table and must
     * not participate in the conflict.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO duck (color, size, price, quantity, deleted)
        VALUES (:#{#color.name()}, :#{#size.name()}, :price, :quantity, false)
        ON CONFLICT (color, size, price) WHERE deleted = false
        DO UPDATE SET quantity = duck.quantity + EXCLUDED.quantity
        """, nativeQuery = true)
    void upsert(@Param("color") Color color,
                @Param("size") Size size,
                @Param("price") BigDecimal price,
                @Param("quantity") int quantity);

    /**
     * Atomically increases one duck's quantity.
     *
     * <p>Used when an edit folds a duck into an existing one. Written as a single
     * {@code SET quantity = quantity + :delta} statement rather than a read-modify-write in Java,
     * so it cannot lose an increment against a concurrent add.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Duck d SET d.quantity = d.quantity + :delta WHERE d.id = :id AND d.deleted = false")
    int addQuantity(@Param("id") Long id, @Param("delta") int delta);
}

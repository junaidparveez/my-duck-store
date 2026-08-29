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
     * <p>Used by the store module to resolve the price for a quote (decision #3: use the lowest
     * active price when multiple price points exist for the same colour + size).
     *
     * <p>Returns {@link Optional#empty()} when there is no active stock at all.
     */
    @Query("SELECT MIN(d.price) FROM Duck d WHERE d.color = :color AND d.size = :size AND d.deleted = false")
    Optional<BigDecimal> findLowestActivePriceByColorAndSize(@Param("color") Color color, @Param("size") Size size);

    /**
     * Atomically adds a duck or merges its quantity if the exact duck (color, size, price)
     * already exists. This delegates the concurrency control to the database's unique index
     * instead of relying on a racy check-then-insert at the application level.
     */
    @Modifying
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
}

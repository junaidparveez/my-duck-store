package com.myduckstore.warehouse.repository;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

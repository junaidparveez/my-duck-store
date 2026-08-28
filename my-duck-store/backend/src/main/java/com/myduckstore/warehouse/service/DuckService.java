package com.myduckstore.warehouse.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Warehouse operations.
 *
 * <p>Known limitation, addressed in a later step: {@link #add} reads and then writes, so two
 * simultaneous requests for the same duck can lose an increment or collide on the unique index.
 * The concurrency test comes first, the fix after it.
 */
@Service
@Transactional
public class DuckService {

    private final DuckRepository repository;

    public DuckService(DuckRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Duck> findAll() {
        return repository.findByDeletedFalseOrderByQuantityAscIdAsc();
    }

    /** Same colour, size and price as an existing duck means adding quantities, not creating a duplicate. */
    public Duck add(Color color, Size size, BigDecimal price, int quantity) {
        return repository.findByColorAndSizeAndPriceAndDeletedFalse(color, size, price)
                .map(existing -> {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    return existing;
                })
                .orElseGet(() -> repository.save(new Duck(color, size, price, quantity)));
    }

    /** Only price and quantity are editable; colour and size are fixed for the life of the record. */
    public Duck update(Long id, BigDecimal price, int quantity) {
        Duck duck = findActive(id);
        duck.setPrice(price);
        duck.setQuantity(quantity);
        return duck;
    }

    /** Logical delete: the row stays in the database and disappears from the listing. */
    public void delete(Long id) {
        findActive(id).setDeleted(true);
    }

    private Duck findActive(Long id) {
        return repository.findByIdAndDeletedFalse(id).orElseThrow(() -> new DuckNotFoundException(id));
    }
}

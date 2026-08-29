package com.myduckstore.warehouse.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Warehouse operations.
 */
@Service
@Transactional
public class DuckService {

    private static final Logger log = LoggerFactory.getLogger(DuckService.class);

    private final DuckRepository repository;

    public DuckService(DuckRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Duck> findAll() {
        List<Duck> ducks = repository.findByDeletedFalseOrderByQuantityAscIdAsc();
        log.debug("findAll: returned {} active duck(s)", ducks.size());
        return ducks;
    }

    /** Same colour, size and price as an existing duck means adding quantities atomically. */
    public Duck add(Color color, Size size, BigDecimal price, int quantity) {
        log.info("add: upserting color={}, size={}, price={}, quantity={}",
                color.getLabel(), size.getLabel(), price, quantity);

        repository.upsert(color, size, price, quantity);

        return repository.findByColorAndSizeAndPriceAndDeletedFalse(color, size, price)
                .orElseThrow(() -> new IllegalStateException("Duck should exist immediately after upsert"));
    }

    /** Only price and quantity are editable; colour and size are fixed for the life of the record. */
    public Duck update(Long id, BigDecimal price, int quantity) {
        log.info("update: id={}, newPrice={}, newQuantity={}", id, price, quantity);
        Duck duck = findActive(id);
        duck.setPrice(price);
        duck.setQuantity(quantity);
        log.debug("update: duck id={} updated successfully", id);
        return duck;
    }

    /** Logical delete: the row stays in the database and disappears from the listing. */
    public void delete(Long id) {
        log.info("delete: id={}", id);
        findActive(id).setDeleted(true);
        log.debug("delete: duck id={} marked as deleted", id);
    }

    private Duck findActive(Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("findActive: duck id={} not found or already deleted", id);
                    return new DuckNotFoundException(id);
                });
    }
}

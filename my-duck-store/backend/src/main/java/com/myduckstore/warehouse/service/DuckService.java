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
import java.util.Optional;

/**
 * Warehouse operations.
 *
 * <p>The whole module is built around one invariant:
 *
 * <blockquote>At most one <em>active</em> duck exists for a given colour + size + price.</blockquote>
 *
 * <p>Every write path has to preserve it, not just the obvious one. Adding stock merges
 * ({@link #add}); editing a price into an existing combination folds ({@link #update}). The
 * database enforces the invariant with a partial unique index, so a bug in this class degrades
 * into a rejected transaction rather than into duplicate rows.
 */
@Service
@Transactional
public class DuckService {

    private static final Logger log = LoggerFactory.getLogger(DuckService.class);

    private final DuckRepository repository;

    public DuckService(DuckRepository repository) {
        this.repository = repository;
    }

    /**
     * The result of an add: the resulting duck, and whether it was a new record or a merge.
     *
     * <p>The controller turns this into 201 or 200. The service reports what happened rather
     * than the controller inferring it, which would put a business rule in the web layer.
     */
    public record AddOutcome(Duck duck, boolean created) {
    }

    @Transactional(readOnly = true)
    public List<Duck> findAll() {
        List<Duck> ducks = repository.findByDeletedFalseOrderByQuantityAscIdAsc();
        log.debug("findAll: returned {} active duck(s)", ducks.size());
        return ducks;
    }

    /**
     * Adds stock. Same colour, size and price as an existing active duck means the quantities
     * are added together instead of a duplicate row being created.
     *
     * <p>The merge itself is a single atomic statement (see {@code DuckRepository.upsert}), so it
     * is correct under concurrency. The {@code created} flag is a best-effort label for the HTTP
     * status only: two simultaneous adds of a brand-new duck may both report "created". The
     * stored quantity is exact either way, which is the part that matters.
     */
    public AddOutcome add(Color color, Size size, BigDecimal price, int quantity) {
        log.info("add: color={}, size={}, price={}, quantity={}",
                color.getLabel(), size.getLabel(), price, quantity);

        boolean created = repository
                .findByColorAndSizeAndPriceAndDeletedFalse(color, size, price)
                .isEmpty();

        repository.upsert(color, size, price, quantity);

        Duck duck = repository.findByColorAndSizeAndPriceAndDeletedFalse(color, size, price)
                .orElseThrow(() -> new IllegalStateException(
                        "Duck must exist immediately after upsert"));

        log.debug("add: {} duck id={}, quantity now {}",
                created ? "created" : "merged into", duck.getId(), duck.getQuantity());

        return new AddOutcome(duck, created);
    }

    /**
     * Edits a duck. Only price and quantity can change; colour and size are fixed for the life of
     * the record, which is why they are absent from the request DTO entirely.
     *
     * <p>Changing a price can collide with the merge invariant: editing "Red / Large / $10" to
     * $20 when "Red / Large / $20" already exists would produce two active rows for the same
     * combination. Rather than reject that edit, we treat it as what it is - moving stock onto an
     * existing price point - and <em>fold</em>: the edited row is logically deleted and its new
     * quantity is atomically added to the surviving row, whose id is returned. This is the same
     * outcome the caller would get by deleting the row and re-adding the stock at the new price,
     * and it keeps the invariant true on every write path rather than only on {@link #add}.
     *
     * <p>The common case - editing without colliding - keeps the duck's own id, because the id is
     * a stable identifier and a routine price correction should not renumber the record.
     */
    public Duck update(Long id, BigDecimal newPrice, int newQuantity) {
        log.info("update: id={}, newPrice={}, newQuantity={}", id, newPrice, newQuantity);

        Duck duck = findActive(id);

        // Price unchanged: no index slot moves, so no collision is possible.
        if (duck.getPrice().compareTo(newPrice) == 0) {
            duck.setQuantity(newQuantity);
            return duck;
        }

        Optional<Duck> collision = repository
                .findByColorAndSizeAndPriceAndDeletedFalse(duck.getColor(), duck.getSize(), newPrice);

        if (collision.isPresent()) {
            return foldInto(collision.get(), duck, newQuantity);
        }

        // No existing duck at the new price. If a concurrent request creates one before this
        // transaction commits, the partial unique index rejects the write and the caller gets a
        // 409 telling them to retry - never a duplicate row.
        duck.setPrice(newPrice);
        duck.setQuantity(newQuantity);
        return duck;
    }

    /** Logical delete: the row stays in the database and disappears from the listing. */
    public void delete(Long id) {
        log.info("delete: id={}", id);
        findActive(id).setDeleted(true);
        log.debug("delete: duck id={} marked as deleted", id);
    }

    // -- Internals ---------------------------------------------------------------

    /**
     * Retires {@code source} and moves {@code quantity} units onto {@code survivor}.
     *
     * <p>The soft delete is flushed before the increment so the two statements reach the database
     * in a defined order, and the increment is a single atomic {@code quantity = quantity + n}
     * statement so it cannot lose units to a concurrent add on the surviving row.
     */
    private Duck foldInto(Duck survivor, Duck source, int quantity) {
        log.info("update: folding duck id={} into id={} (+{} units)",
                source.getId(), survivor.getId(), quantity);

        source.setDeleted(true);
        repository.flush();

        if (repository.addQuantity(survivor.getId(), quantity) != 1) {
            // The survivor was deleted by a concurrent request between our read and our write.
            throw new DuckConflictException(
                    "Duck " + survivor.getId() + " changed concurrently. Retry the request.");
        }

        return repository.findById(survivor.getId())
                .orElseThrow(() -> new DuckConflictException(
                        "Duck " + survivor.getId() + " changed concurrently. Retry the request."));
    }

    private Duck findActive(Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("findActive: duck id={} not found or already deleted", id);
                    return new DuckNotFoundException(id);
                });
    }
}

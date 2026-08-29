package com.myduckstore.warehouse.service;

import com.myduckstore.support.AbstractPostgresIntegrationTest;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the warehouse merge invariant holds under genuinely concurrent writes, against a real
 * PostgreSQL instance - the guarantee comes from the partial unique index and the atomic upsert,
 * so an in-memory database would not be testing the thing that matters.
 */
class DuckMergeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DuckService duckService;

    @Autowired
    private DuckRepository duckRepository;

    @BeforeEach
    void clearWarehouse() {
        duckRepository.deleteAll();
    }

    @Test
    @DisplayName("many simultaneous adds of the same duck produce one row with every unit counted")
    void concurrentAddsOfSameDuckMergeWithoutDuplicatesOrLostUpdates() throws Exception {
        Color color = Color.RED;
        Size size = Size.LARGE;
        BigDecimal price = new BigDecimal("10.00");
        int threads = 20;
        int quantityPerRequest = 5;

        runConcurrently(threads, () -> duckService.add(color, size, price, quantityPerRequest));

        List<Duck> all = duckRepository.findAll();
        assertThat(all).as("the unique index must make a duplicate row impossible").hasSize(1);

        Duck merged = all.getFirst();
        assertThat(merged.getColor()).isEqualTo(color);
        assertThat(merged.getSize()).isEqualTo(size);
        assertThat(merged.getPrice()).isEqualByComparingTo(price);
        assertThat(merged.getQuantity())
                .as("every one of the %d increments must survive - no lost updates", threads)
                .isEqualTo(threads * quantityPerRequest);
    }

    @Test
    @DisplayName("the assignment's worked example: 100 + 50 + 30 concurrently = 180")
    void concurrentAddsOntoExistingStockSumExactly() throws Exception {
        Color color = Color.RED;
        Size size = Size.LARGE;
        BigDecimal price = new BigDecimal("200.00");

        duckService.add(color, size, price, 100);

        // Two adds arriving at the same moment: 50 and 30. The answer is 180 - never 150 or 130.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(awaiting(start, () -> duckService.add(color, size, price, 50)));
            Future<?> b = pool.submit(awaiting(start, () -> duckService.add(color, size, price, 30)));
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        List<Duck> all = duckRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getQuantity()).isEqualTo(180);
    }

    @Test
    @DisplayName("concurrent adds at different prices stay as separate records")
    void concurrentAddsAtDifferentPricesDoNotMerge() throws Exception {
        Color color = Color.GREEN;
        Size size = Size.SMALL;

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(awaiting(start,
                    () -> duckService.add(color, size, new BigDecimal("10.00"), 7)));
            Future<?> b = pool.submit(awaiting(start,
                    () -> duckService.add(color, size, new BigDecimal("12.00"), 9)));
            start.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(duckRepository.findAll())
                .as("price is part of the merge key, so these are two different ducks")
                .hasSize(2)
                .extracting(Duck::getQuantity)
                .containsExactlyInAnyOrder(7, 9);
    }

    // -- Helpers -----------------------------------------------------------------

    /**
     * Runs {@code task} on {@code threads} threads released at the same instant, and rethrows the
     * first failure. Calling {@link Future#get()} on every task is what makes this a real test:
     * without it an exception inside a worker is captured in the Future and silently discarded,
     * and the assertions below would report a confusing empty table instead of the actual cause.
     */
    private static void runConcurrently(int threads, Callable<?> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(awaiting(start, task)));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Object> awaiting(CountDownLatch start, Callable<?> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }
}

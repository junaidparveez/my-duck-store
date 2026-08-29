package com.myduckstore.warehouse.service;

import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.repository.DuckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving the warehouse merge invariant holds under concurrent load.
 *
 * <p>Uses Testcontainers to spin up a real PostgreSQL database, ensuring the
 * native UPSERT query and unique partial index behave exactly as they will in production.
 */
@SpringBootTest
@Testcontainers
class DuckMergeIntegrationTest {

    // 1. Spin up a real, temporary PostgreSQL database using Docker.
    // @ServiceConnection automatically injects the JDBC URL, username, and password into Spring Boot!
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DuckService duckService;

    @Autowired
    private DuckRepository duckRepository;

    @Test
    void concurrent_adds_for_same_duck_merge_quantities_without_duplicates() throws InterruptedException {
        // Arrange
        // Clear the database so we have a clean slate for this test
        duckRepository.deleteAll();

        Color color = Color.RED;
        Size size = Size.LARGE;
        BigDecimal price = new BigDecimal("10.00");
        int quantityPerRequest = 5;
        int numberOfConcurrentRequests = 20;

        // We use an ExecutorService to simulate 20 users hitting the "Add Duck" endpoint at the exact same millisecond.
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfConcurrentRequests);

        // Act
        for (int i = 0; i < numberOfConcurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Every thread waits here...
                    duckService.add(color, size, price, quantityPerRequest); // ...and they all fire at once!
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release the hounds! All 20 threads execute DuckService.add() concurrently.
        startLatch.countDown();
        // Wait for all 20 threads to finish.
        doneLatch.await();
        executor.shutdown();

        // Assert
        // We should have EXACTLY ONE duck in the database. No duplicates.
        List<Duck> allDucks = duckRepository.findAll();
        assertThat(allDucks).hasSize(1);

        Duck mergedDuck = allDucks.getFirst();
        assertThat(mergedDuck.getColor()).isEqualTo(color);
        assertThat(mergedDuck.getSize()).isEqualTo(size);
        assertThat(mergedDuck.getPrice()).isEqualByComparingTo(price);

        // 20 requests * 5 quantity = 100 total quantity.
        // If the UPSERT works, no increments are lost to race conditions.
        assertThat(mergedDuck.getQuantity()).isEqualTo(100);
    }
}

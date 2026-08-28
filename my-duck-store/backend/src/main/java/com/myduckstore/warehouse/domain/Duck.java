package com.myduckstore.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A duck held in the warehouse.
 *
 * <p>Deletion is logical: the row stays in the table with {@code deleted = true} and is
 * filtered out of every query, so a deleted duck can never appear in the listing again.
 */
@Entity
@Table(name = "duck")
@Getter
@Setter
@NoArgsConstructor
public class Duck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Color color;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 16)
    private Size size;

    /** Unit price in USD. NUMERIC(12,2) in the database - never a binary float. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean deleted;

    public Duck(Color color, Size size, BigDecimal price, int quantity) {
        this.color = color;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
    }
}

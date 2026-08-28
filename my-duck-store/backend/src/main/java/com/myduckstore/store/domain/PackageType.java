package com.myduckstore.store.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The outer packaging chosen based on duck size.
 *
 * <p>XLarge/Large → Wood; Medium → Cardboard; Small/XSmall → Plastic.
 */
public enum PackageType {

    WOOD("Wood"),
    CARDBOARD("Cardboard"),
    PLASTIC("Plastic");

    private final String label;

    PackageType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}

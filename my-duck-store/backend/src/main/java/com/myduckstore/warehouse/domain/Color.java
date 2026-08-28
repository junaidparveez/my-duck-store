package com.myduckstore.warehouse.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The colours a duck can be sold in. */
public enum Color {

    RED("Red"), GREEN("Green"), YELLOW("Yellow"), BLACK("Black");

    private final String label;

    Color(String label) {
        this.label = label;
    }

    /** Returned in API responses, e.g. {@code "Red"} instead of {@code "RED"}. */
    @JsonValue
    public String getLabel() {
        return label;
    }

    /** Accepts both title-case ("Red") and upper-case ("RED") from API callers. */
    @JsonCreator
    public static Color from(String value) {
        for (Color c : values()) {
            if (c.label.equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException(
                "Unknown color: '" + value + "'. Valid values: Red, Green, Yellow, Black");
    }
}

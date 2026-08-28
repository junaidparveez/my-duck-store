package com.myduckstore.store.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The shipping mode chosen by the customer. Determines the base shipping charge and protection materials. */
public enum ShippingMode {

    AIR("Air"),
    LAND("Land"),
    SEA("Sea");

    private final String label;

    ShippingMode(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ShippingMode from(String value) {
        for (ShippingMode mode : values()) {
            if (mode.label.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown shipping mode: '" + value + "'. Valid values: Air, Land, Sea");
    }
}

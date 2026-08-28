package com.myduckstore.warehouse.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The sizes a duck can be sold in. Determines the packaging used by the store module. */
public enum Size {

    XLARGE("XLarge"), LARGE("Large"), MEDIUM("Medium"), SMALL("Small"), XSMALL("XSmall");

    private final String label;

    Size(String label) {
        this.label = label;
    }

    /** Returned in API responses, e.g. {@code "XLarge"} instead of {@code "XLARGE"}. */
    @JsonValue
    public String getLabel() {
        return label;
    }

    /** Accepts both title-case ("XLarge") and upper-case ("XLARGE") from API callers. */
    @JsonCreator
    public static Size from(String value) {
        for (Size s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "Unknown size: '" + value + "'. Valid values: XLarge, Large, Medium, Small, XSmall");
    }
}

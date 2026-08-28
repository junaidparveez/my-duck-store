package com.myduckstore.store.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A material used to protect the ducks during shipping.
 *
 * <p>Which materials are used depends on the package type and the shipping mode.
 */
public enum ProtectionMaterial {

    POLYSTYRENE_BALLS("Polystyrene balls"),
    BUBBLE_WRAP_BAGS("Bubble wrap bags"),
    MOISTURE_ABSORBING_BEADS("Moisture-absorbing beads");

    private final String label;

    ProtectionMaterial(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}

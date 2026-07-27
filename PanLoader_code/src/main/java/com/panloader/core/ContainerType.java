package com.panloader.core;

public enum ContainerType {
    PANLOADER("PanLoader"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    UNKNOWN("Unknown");

    private final String displayName;

    ContainerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

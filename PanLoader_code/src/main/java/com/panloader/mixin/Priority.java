package com.panloader.mixin;

public enum Priority {
    HIGHEST(0),
    HIGH(100),
    NORMAL(200),
    LOW(300),
    LOWEST(400);

    private final int value;

    Priority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

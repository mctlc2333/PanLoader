package com.panloader.core;

public enum ConflictResolutionStrategy {

    FAIL_ON_CONFLICT,

    KEEP_FIRST,

    KEEP_LAST,

    KEEP_BY_PRIORITY,

    MERGE;

    public String getDisplayName() {
        return switch (this) {
            case FAIL_ON_CONFLICT -> "Fail on Conflict";
            case KEEP_FIRST -> "Keep First";
            case KEEP_LAST -> "Keep Last";
            case KEEP_BY_PRIORITY -> "Keep by Priority";
            case MERGE -> "Merge";
        };
    }

    public boolean canResolve() {
        return this != FAIL_ON_CONFLICT;
    }
}
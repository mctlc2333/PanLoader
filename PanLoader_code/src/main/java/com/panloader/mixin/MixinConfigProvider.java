package com.panloader.mixin;

import java.util.List;

public interface MixinConfigProvider {

    String getContainerId();

    String getContainerTypeString();

    default Priority getMixinPriority() {
        return Priority.NORMAL;
    }

    List<String> getMixinConfigPaths();

    default List<String> getMixinPackages() {
        return List.of();
    }

    default List<String> getExcludedClasses() {
        return List.of();
    }
}

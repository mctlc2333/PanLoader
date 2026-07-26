package com.panloader.api;

public interface PanMod {

    void onInitialize();

    default void onUnload() {
    }

    default void onGameReady(ClassLoader gameClassLoader) {
    }

    default void onPreLaunch(ClassLoader gameClassLoader) {
    }

    default ModMetadata getMetadata() {
        return null;
    }

    default void setMetadata(ModMetadata metadata) {
    }

    default String getModId() {
        ModMetadata meta = getMetadata();
        return meta != null ? meta.getId() : getClass().getSimpleName();
    }

    default String getModName() {
        ModMetadata meta = getMetadata();
        return meta != null ? meta.getName() : getModId();
    }

    default String getModVersion() {
        ModMetadata meta = getMetadata();
        return meta != null ? meta.getVersion() : "0.0.0";
    }
}

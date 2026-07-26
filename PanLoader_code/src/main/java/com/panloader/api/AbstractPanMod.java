package com.panloader.api;

public abstract class AbstractPanMod implements PanMod {

    private ModMetadata metadata;

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(ModMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + getModId() + '\'' +
                ", name='" + getModName() + '\'' +
                ", version='" + getModVersion() + '\'' +
                '}';
    }
}

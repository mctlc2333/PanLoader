package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;
import com.panloader.mixin.MixinConfigProvider;
import com.panloader.mixin.Priority;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ModContainer extends MixinConfigProvider {

    ContainerType getContainerType();

    @Override
    default String getContainerTypeString() {
        return getContainerType().name();
    }

    String getContainerId();

    void addMod(Path jarPath) throws Exception;

    void initialize() throws Exception;

    void setGameClassLoader(ClassLoader gameClassLoader);

    void notifyGameReady();

    void notifyPreLaunch();

    ClassLoader getClassLoader();

    List<URL> getClasspathEntries();

    List<Path> getModJarPaths();

    Set<String> getLoadedModIds();

    PanMod getMod(String modId);

    ModMetadata getModMetadata(String modId);

    int getLoadedModCount();

    void unloadAll();

    boolean isInitialized();
}

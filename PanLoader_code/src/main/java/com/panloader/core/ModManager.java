package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModManager {

    private final Map<String, PanMod> loadedMods = new ConcurrentHashMap<>();
    private final Map<String, ModMetadata> modMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, ModClassLoader> modClassLoaders = new ConcurrentHashMap<>();
    private ClassLoader gameClassLoader;

    public void setGameClassLoader(ClassLoader classLoader) {
        this.gameClassLoader = classLoader;
        notifyGameReady();
    }

    public ClassLoader getGameClassLoader() {
        return gameClassLoader;
    }

    public void notifyGameReady() {
        for (PanMod mod : loadedMods.values()) {
            try {
                mod.onGameReady(gameClassLoader);
            } catch (Exception e) {
                System.err.println("[PanLoader] Error notifying mod " + mod.getModId() + " of game ready: " + e.getMessage());
            }
        }
    }

    public void notifyPreLaunch() {
        for (PanMod mod : loadedMods.values()) {
            try {
                mod.onPreLaunch(gameClassLoader);
            } catch (Exception e) {
                System.err.println("[PanLoader] Error in pre-launch for " + mod.getModId() + ": " + e.getMessage());
            }
        }
    }

    public PanMod loadMod(Path jarPath) throws Exception {
        ModMetadata metadata = null;
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
            metadata = MetadataParser.parseFromJar(jarFile);
        }

        if (metadata == null) {
            return null;
        }

        String modId = metadata.getId();
        if (loadedMods.containsKey(modId)) {
            System.err.println("[PanLoader] Mod already loaded, skipping: " + modId);
            return null;
        }

        String entrypoint = metadata.getEntrypoint();
        if (entrypoint == null || entrypoint.isEmpty()) {
            System.err.println("[PanLoader] No entrypoint defined for mod: " + modId);
            return null;
        }

        ModClassLoader classLoader = new ModClassLoader(jarPath, modId, getClass().getClassLoader());

        try {
            PanMod mod = classLoader.createModInstance(entrypoint);

            mod.setMetadata(metadata);

            System.out.println("[PanLoader] Initializing mod: " + metadata.getName() + " v" + metadata.getVersion());
            mod.onInitialize();

            loadedMods.put(modId, mod);
            modMetadataMap.put(modId, metadata);
            modClassLoaders.put(modId, classLoader);

            return mod;
        } catch (Exception e) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    public void registerMod(PanMod mod, ModMetadata metadata) {
        String modId = metadata.getId();
        if (loadedMods.containsKey(modId)) {
            System.err.println("[PanLoader] Mod already registered, skipping: " + modId);
            return;
        }
        loadedMods.put(modId, mod);
        modMetadataMap.put(modId, metadata);
    }

    public void unloadMod(String modId) {
        PanMod mod = loadedMods.get(modId);
        if (mod == null) {
            return;
        }

        try {
            mod.onUnload();
        } catch (Exception e) {
            System.err.println("[PanLoader] Error during unload of " + modId + ": " + e.getMessage());
        }

        loadedMods.remove(modId);
        modMetadataMap.remove(modId);

        ModClassLoader classLoader = modClassLoaders.remove(modId);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception e) {
                System.err.println("[PanLoader] Error closing classloader for " + modId + ": " + e.getMessage());
            }
        }
    }

    public void unloadAllMods() {
        List<String> modIds = new ArrayList<>(loadedMods.keySet());
        Collections.reverse(modIds);
        for (String modId : modIds) {
            unloadMod(modId);
        }
    }

    public PanMod getMod(String modId) {
        return loadedMods.get(modId);
    }

    public ModMetadata getModMetadata(String modId) {
        return modMetadataMap.get(modId);
    }

    public Set<String> getLoadedModIds() {
        return Collections.unmodifiableSet(loadedMods.keySet());
    }

    public Collection<PanMod> getLoadedMods() {
        return Collections.unmodifiableCollection(loadedMods.values());
    }

    public boolean isModLoaded(String modId) {
        return loadedMods.containsKey(modId);
    }

    public int getLoadedModCount() {
        return loadedMods.size();
    }

    public ModClassLoader getClassLoader(String modId) {
        return modClassLoaders.get(modId);
    }

    public List<Path> getAllModJarPaths() {
        List<Path> paths = new ArrayList<>();
        for (ModClassLoader cl : modClassLoaders.values()) {
            paths.add(cl.getJarPath());
        }
        return paths;
    }
}

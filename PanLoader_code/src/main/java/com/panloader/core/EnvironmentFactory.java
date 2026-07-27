package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnvironmentFactory {

    private final Map<ContainerType, ModContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, ModContainer> modToContainer = new ConcurrentHashMap<>();
    private final CrossContainerBus bus;
    private ClassLoader sharedClassLoader;
    private Path gameDir;

    public EnvironmentFactory(Path gameDir) {
        this.bus = CrossContainerBus.getInstance();
        this.gameDir = gameDir;
    }

    public void setSharedClassLoader(ClassLoader classLoader) {
        this.sharedClassLoader = classLoader;
        for (ModContainer container : containers.values()) {
            container.setGameClassLoader(classLoader);
        }
    }

    public ClassLoader getSharedClassLoader() {
        return sharedClassLoader;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public ModContainer getOrCreateContainer(ContainerType type) {
        return containers.computeIfAbsent(type, t -> {
            ModContainer container;
            switch (t) {
                case PANLOADER:
                    container = new PanLoaderContainer("panloader", sharedClassLoader);
                    break;
                case FABRIC:
                    container = new FabricContainer("fabric", gameDir);
                    break;
                case FORGE:
                    container = new ForgeContainer("forge", gameDir);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown container type: " + t);
            }
            bus.registerContainer(container);
            System.out.println("[EnvFactory] Created " + t.getDisplayName() + " container: " + container.getContainerId());
            return container;
        });
    }

    public void routeMod(ModsFolderScanner.ModCandidate candidate) throws Exception {
        ContainerType targetType = mapModType(candidate.getType());
        ModContainer container = getOrCreateContainer(targetType);

        Path jarPath = candidate.getJarPath();
        container.addMod(jarPath);
        modToContainer.put(jarPath.getFileName().toString(), container);
    }

    private ContainerType mapModType(ModsFolderScanner.ModType modType) {
        switch (modType) {
            case PANLOADER:
                return ContainerType.PANLOADER;
            case FABRIC:
                return ContainerType.FABRIC;
            case FORGE:
                return ContainerType.FORGE;
            case UNKNOWN:
                return ContainerType.PANLOADER;
            default:
                return ContainerType.PANLOADER;
        }
    }

    public void initializeAll() throws Exception {
        for (ModContainer container : containers.values()) {
            if (!container.isInitialized()) {
                try {
                    container.initialize();
                } catch (Exception e) {
                    System.err.println("[EnvFactory] Failed to initialize container "
                            + container.getContainerId() + ": " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    public void notifyGameReadyAll() {
        for (ModContainer container : containers.values()) {
            try {
                container.notifyGameReady();
            } catch (Exception e) {
                System.err.println("[EnvFactory] Error in game-ready for "
                        + container.getContainerId() + ": " + e.getMessage());
            }
        }
    }

    public void notifyPreLaunchAll() {
        for (ModContainer container : containers.values()) {
            try {
                container.notifyPreLaunch();
            } catch (Exception e) {
                System.err.println("[EnvFactory] Error in pre-launch for "
                        + container.getContainerId() + ": " + e.getMessage());
            }
        }
    }

    public List<URL> getAllClasspathEntries() {
        List<URL> entries = new ArrayList<>();
        for (ModContainer container : containers.values()) {
            entries.addAll(container.getClasspathEntries());
        }
        return entries;
    }

    public List<Path> getAllModJarPaths() {
        List<Path> paths = new ArrayList<>();
        for (ModContainer container : containers.values()) {
            paths.addAll(container.getModJarPaths());
        }
        return paths;
    }

    public Collection<ModContainer> getAllContainers() {
        return Collections.unmodifiableCollection(containers.values());
    }

    public ModContainer getContainer(ContainerType type) {
        return containers.get(type);
    }

    public ModContainer getContainerForMod(String modId) {
        for (ModContainer container : containers.values()) {
            if (container.getLoadedModIds().contains(modId)) {
                return container;
            }
        }
        return null;
    }

    public PanMod findMod(String modId) {
        for (ModContainer container : containers.values()) {
            PanMod mod = container.getMod(modId);
            if (mod != null) {
                return mod;
            }
        }
        return null;
    }

    public ModMetadata findModMetadata(String modId) {
        for (ModContainer container : containers.values()) {
            ModMetadata meta = container.getModMetadata(modId);
            if (meta != null) {
                return meta;
            }
        }
        return null;
    }

    public int getTotalLoadedModCount() {
        int total = 0;
        for (ModContainer container : containers.values()) {
            total += container.getLoadedModCount();
        }
        return total;
    }

    public int getContainerCount() {
        return containers.size();
    }

    public void unloadAll() {
        for (ModContainer container : containers.values()) {
            try {
                container.unloadAll();
            } catch (Exception e) {
                System.err.println("[EnvFactory] Error unloading container "
                        + container.getContainerId() + ": " + e.getMessage());
            }
            bus.unregisterContainer(container);
        }
        containers.clear();
        modToContainer.clear();
    }

    public CrossContainerBus getBus() {
        return bus;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EnvironmentFactory{containers=[");
        boolean first = true;
        for (ModContainer c : containers.values()) {
            if (!first) sb.append(", ");
            sb.append(c.getContainerId()).append("(").append(c.getContainerType().getDisplayName())
                    .append("): ").append(c.getLoadedModCount()).append(" mods");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }
}

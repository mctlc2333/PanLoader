package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;
import com.panloader.mixin.Priority;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PanLoaderContainer implements ModContainer {

    private final String containerId;
    private final ModManager modManager;
    private final SandboxClassLoader classLoader;
    private final List<Path> modJarPaths = new ArrayList<>();
    private final List<DeferredRegisterShim> deferredRegisters = new ArrayList<>();
    private ClassLoader gameClassLoader;
    private boolean initialized = false;

    public PanLoaderContainer(String containerId, ClassLoader sharedClassLoader) {
        this.containerId = containerId;
        this.modManager = new ModManager();
        this.classLoader = new SandboxClassLoader(
                containerId,
                new URL[0],
                sharedClassLoader
        );
        this.classLoader.addSharedPackage("com.panloader.api");
        this.classLoader.addSharedPackage("com.panloader.core");
        this.classLoader.addIsolatedPackage("com.panloader.test");
    }

    @Override
    public Priority getMixinPriority() {
        return Priority.HIGH;
    }

    @Override
    public List<String> getMixinConfigPaths() {
        List<String> configs = new ArrayList<>();
        for (Path jarPath : modJarPaths) {
            configs.add(jarPath.getFileName().toString().replace(".jar", "") + ".mixins.json");
        }
        configs.add("panloader.mixins.json");
        return configs;
    }

    @Override
    public ContainerType getContainerType() {
        return ContainerType.PANLOADER;
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public void addMod(Path jarPath) throws Exception {
        modJarPaths.add(jarPath);
        classLoader.addJarPath(jarPath);
        com.panloader.mixin.MixinManager.getInstance().registerMixinConfigsFromMod(jarPath);
        System.out.println("[PanLoader-" + containerId + "] Added mod JAR: " + jarPath.getFileName());
    }

    @Override
    public void initialize() throws Exception {
        com.panloader.mixin.MixinOrchestrator.getInstance().registerProvider(this);

        for (Path jarPath : modJarPaths) {
            try {
                ModMetadata metadata = null;
                try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
                    metadata = MetadataParser.parseFromJar(jarFile);
                }

                if (metadata == null) {
                    continue;
                }

                String modId = metadata.getId();
                if (modManager.isModLoaded(modId)) {
                    continue;
                }

                String entrypoint = metadata.getEntrypoint();
                if (entrypoint == null || entrypoint.isEmpty()) {
                    continue;
                }

                Class<?> modClass = classLoader.loadClass(entrypoint);
                if (!com.panloader.api.PanMod.class.isAssignableFrom(modClass)) {
                    System.err.println("[PanLoader-" + containerId + "] Entrypoint does not implement PanMod: " + entrypoint);
                    continue;
                }

                com.panloader.api.PanMod mod = (com.panloader.api.PanMod) modClass.getDeclaredConstructor().newInstance();
                mod.setMetadata(metadata);
                mod.onInitialize();

                modManager.registerMod(mod, metadata);

                System.out.println("[PanLoader-" + containerId + "] Loaded: "
                        + metadata.getName() + " v" + metadata.getVersion());
            } catch (Exception e) {
                System.err.println("[PanLoader-" + containerId + "] Failed to load "
                        + jarPath.getFileName() + ": " + e.getMessage());
                throw e;
            }
        }

        for (DeferredRegisterShim register : deferredRegisters) {
            try {
                register.registerAll();
            } catch (Exception e) {
                System.err.println("[PanLoader-" + containerId + "] Error registering deferred register: " + e.getMessage());
            }
        }

        initialized = true;
    }

    @Override
    public void setGameClassLoader(ClassLoader gameClassLoader) {
        this.gameClassLoader = gameClassLoader;
        this.classLoader.addSharedPackage("net.minecraft");
        modManager.setGameClassLoader(gameClassLoader);
    }

    @Override
    public void notifyGameReady() {
        modManager.notifyGameReady();
    }

    @Override
    public void notifyPreLaunch() {
        modManager.notifyPreLaunch();
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public List<URL> getClasspathEntries() {
        List<URL> entries = new ArrayList<>();
        for (Path p : modJarPaths) {
            try {
                entries.add(p.toUri().toURL());
            } catch (Exception e) {
                System.err.println("[PanLoader-" + containerId + "] Invalid URL: " + p);
            }
        }
        return entries;
    }

    @Override
    public List<Path> getModJarPaths() {
        return Collections.unmodifiableList(modJarPaths);
    }

    @Override
    public Set<String> getLoadedModIds() {
        return modManager.getLoadedModIds();
    }

    @Override
    public PanMod getMod(String modId) {
        return modManager.getMod(modId);
    }

    @Override
    public ModMetadata getModMetadata(String modId) {
        return modManager.getModMetadata(modId);
    }

    @Override
    public int getLoadedModCount() {
        return modManager.getLoadedModCount();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public DeferredRegisterShim createDeferredRegister(String modId, String registryName) {
        DeferredRegisterShim register = new DeferredRegisterShim(modId, registryName);
        deferredRegisters.add(register);
        return register;
    }

    public List<DeferredRegisterShim> getDeferredRegisters() {
        return Collections.unmodifiableList(deferredRegisters);
    }

    @Override
    public void unloadAll() {
        modManager.unloadAllMods();
        modJarPaths.clear();
        deferredRegisters.clear();
        initialized = false;

        try {
            classLoader.close();
        } catch (Exception e) {
            System.err.println("[PanLoader-" + containerId + "] Error closing sandbox classloader: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "PanLoaderContainer{" +
                "id='" + containerId + '\'' +
                ", mods=" + modManager.getLoadedModCount() +
                ", paths=" + modJarPaths.size() +
                '}';
    }
}

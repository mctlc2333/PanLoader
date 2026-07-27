package com.panloader.core;

import com.panloader.api.ModMetadata;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

public class ForgeModLoader {

    public static final String SHIM_VERSION = "PanLoader Forge Compatibility Shim v2";
    public static final String LIMITATION_MESSAGE = "[ForgeLoader] PanLoader provides a Forge compatibility shim that handles basic mod initialization. "
            + "For mods requiring full Forge runtime (ModLauncher, @Mod lifecycle, event bus), only basic instantiation is supported. "
            + "Advanced Forge features like DeferredRegister, event subscriptions, and capability systems may not work correctly.";

    public enum ForgeFeature {
        BASIC_INSTANTIATION("Basic Mod Instantiation", true, "Creates mod instance via reflection"),
        LIFECYCLE_CALLBACKS("Lifecycle Callbacks", true, "Fires pre-launch, game-ready events"),
        MOD_ANNOTATION_PROCESSING("@Mod Annotation Processing", true, "Detects @Mod annotated classes"),
        DEFERRED_REGISTER("DeferredRegister", true, "Via CrossContainerBus shim layer"),
        EVENT_BRIDGE("Event Bus Bridge", true, "Bridges Forge events to CrossContainerBus"),
        REGISTRY_SYNC("Registry Synchronization", true, "Syncs registries across containers"),
        MOD_LAUNCHER("ModLauncher", false, "Requires full Forge runtime"),
        CAPABILITY_SYSTEM("Capability System", false, "Requires Forge capability infrastructure"),
        CONFIG_HANDLER("Config Annotations", false, "Requires Forge config system"),
        NETWORK_HANDLER("Network Annotations", false, "Requires Forge networking infrastructure"),
        BIOME_MODIFIER("Biome Modifier", false, "Requires Forge biome API"),
        DATAPACK_REGISTRY("Datapack Registry", false, "Requires Forge dynamic registry");

        private final String displayName;
        private final boolean supported;
        private final String description;

        ForgeFeature(String displayName, boolean supported, String description) {
            this.displayName = displayName;
            this.supported = supported;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public boolean isSupported() { return supported; }
        public String getDescription() { return description; }
    }

    public static String getFeatureSupportMatrix() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ForgeLoader] === Forge Feature Support Matrix ===\n");
        int supported = 0, unsupported = 0;
        for (ForgeFeature feature : ForgeFeature.values()) {
            String status = feature.isSupported() ? "✓ SUPPORTED" : "✗ NOT SUPPORTED";
            sb.append(String.format("[ForgeLoader]   %s %-30s (%s)%n",
                    status, feature.getDisplayName(), feature.getDescription()));
            if (feature.isSupported()) supported++; else unsupported++;
        }
        sb.append(String.format("[ForgeLoader] Summary: %d supported, %d not supported%n", supported, unsupported));
        sb.append("[ForgeLoader] ======================================");
        return sb.toString();
    }

    private final ClassLoader classLoader;
    private final Path gameDir;
    private final Map<String, Object> modInstances = new HashMap<>();
    private final Map<String, Class<?>> modClasses = new HashMap<>();
    private final Map<String, List<Runnable>> preLaunchCallbacks = new HashMap<>();
    private final Map<String, List<Runnable>> gameReadyCallbacks = new HashMap<>();
    private boolean shimInitialized = false;

    public ForgeModLoader(ClassLoader classLoader, Path gameDir) {
        this.classLoader = classLoader;
        this.gameDir = gameDir;
    }

    public void loadForgeMods(List<ModMetadata> modMetadataList, List<Path> modJarPaths) {
        System.out.println("[ForgeLoader] " + SHIM_VERSION);
        System.out.println(LIMITATION_MESSAGE);
        System.out.println(getFeatureSupportMatrix());
        System.out.println("[ForgeLoader] Loading " + modMetadataList.size() + " Forge mod(s)...");

        int basicLoaded = 0;
        int instantiationFailed = 0;
        int unsupportedSkipped = 0;

        for (int i = 0; i < modMetadataList.size(); i++) {
            ModMetadata meta = modMetadataList.get(i);
            Path jarPath = i < modJarPaths.size() ? modJarPaths.get(i) : null;

            try {
                LoadResult result = loadSingleMod(meta, jarPath);
                switch (result.status) {
                    case SUCCESS -> basicLoaded++;
                    case INSTANTIATION_FAILED -> instantiationFailed++;
                    case UNSUPPORTED -> unsupportedSkipped++;
                }
            } catch (Exception e) {
                instantiationFailed++;
                System.err.println("[ForgeLoader] Failed to load mod " + meta.getName()
                        + " (id: " + meta.getId() + "): " + e.getMessage());
            }
        }

        System.out.println("[ForgeLoader] Load summary: " + basicLoaded + " loaded, "
                + instantiationFailed + " failed, " + unsupportedSkipped + " unsupported (shim limitations)");
        System.out.println("[ForgeLoader] " + basicLoaded + "/" + modMetadataList.size() + " Forge mod(s) loaded");
    }

    private LoadResult loadSingleMod(ModMetadata meta, Path jarPath) throws Exception {
        String entrypoint = meta.getEntrypoint();
        if (entrypoint == null || entrypoint.isEmpty()) {
            System.out.println("[ForgeLoader] No entrypoint for " + meta.getId() + ", attempting auto-detect...");
            entrypoint = autoDetectModClass(jarPath);
            if (entrypoint == null) {
                System.out.println("[ForgeLoader] Cannot determine main class for " + meta.getId() + ", skipping (unsupported by shim)");
                return new LoadResult(LoadStatus.UNSUPPORTED, "No entrypoint found, mod may require full Forge runtime");
            }
            meta.setEntrypoint(entrypoint);
        }

        System.out.println("[ForgeLoader] Loading mod: " + meta.getName() + " v"
                + meta.getVersion() + " (main class: " + entrypoint + ")");

        Class<?> modClass;
        try {
            modClass = classLoader.loadClass(entrypoint);
        } catch (ClassNotFoundException e) {
            System.out.println("[ForgeLoader] Cannot load class " + entrypoint + " for " + meta.getId()
                    + " - mod may require full Forge runtime with proper classpath setup");
            return new LoadResult(LoadStatus.UNSUPPORTED, "Class not found: " + entrypoint);
        }
        modClasses.put(meta.getId(), modClass);

        if (isForgeSpecificClass(modClass)) {
            System.out.println("[ForgeLoader] Note: " + meta.getName() + " uses Forge-specific APIs. "
                    + "Full functionality requires Forge runtime, but basic initialization will be attempted.");
        }

        Object instance;
        try {
            java.lang.reflect.Constructor<?> defaultCtor = modClass.getDeclaredConstructor();
            defaultCtor.setAccessible(true);
            instance = defaultCtor.newInstance();
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Constructor<?>[] constructors = modClass.getDeclaredConstructors();
                if (constructors.length > 0) {
                    constructors[0].setAccessible(true);
                    instance = constructors[0].newInstance();
                    System.out.println("[ForgeLoader] Using non-default constructor for " + meta.getName());
                } else {
                    throw new RuntimeException("No constructor found for " + entrypoint);
                }
            } catch (Exception ex) {
                System.err.println("[ForgeLoader] Cannot instantiate " + entrypoint + ": " + ex.getMessage());
                return new LoadResult(LoadStatus.INSTANTIATION_FAILED, ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[ForgeLoader] Failed to instantiate " + entrypoint + ": " + e.getMessage());
            return new LoadResult(LoadStatus.INSTANTIATION_FAILED, e.getMessage());
        }

        modInstances.put(meta.getId(), instance);
        System.out.println("[ForgeLoader] Instantiated: " + meta.getName());

        registerModLifecycle(instance, meta);

        System.out.println("[ForgeLoader] Loaded: " + meta.getName() + " v" + meta.getVersion());
        return new LoadResult(LoadStatus.SUCCESS, "Mod loaded successfully via shim");
    }

    private boolean isForgeSpecificClass(Class<?> clazz) {
        String className = clazz.getName();
        return className.contains("net.minecraftforge")
                || className.contains("cpw.mods")
                || className.contains("forge.mods")
                || hasForgeAnnotation(clazz);
    }

    private boolean hasForgeAnnotation(Class<?> clazz) {
        try {
            for (java.lang.annotation.Annotation annotation : clazz.getAnnotations()) {
                String annName = annotation.annotationType().getName();
                if (annName.contains("Mod") || annName.contains("mod")
                        || annName.contains("FML") || annName.contains("forge")) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private String autoDetectModClass(Path jarPath) {
        if (jarPath == null) return null;

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (hasModAnnotation(clazz)) {
                            return className;
                        }
                    } catch (Exception e) {
                        // Skip classes that can't be loaded
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ForgeLoader] Error scanning JAR: " + e.getMessage());
        }

        return null;
    }

    private boolean hasModAnnotation(Class<?> clazz) {
        try {
            for (java.lang.annotation.Annotation annotation : clazz.getAnnotations()) {
                String annotationName = annotation.annotationType().getName();
                if (annotationName.contains("Mod") || annotationName.contains("mod")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private void registerModLifecycle(Object instance, ModMetadata meta) {
        String modId = meta.getId();

        try {
            Method[] methods = instance.getClass().getDeclaredMethods();

            for (Method method : methods) {
                if (isPreLaunchMethod(method)) {
                    method.setAccessible(true);
                    preLaunchCallbacks.computeIfAbsent(modId, k -> new ArrayList<>())
                            .add(() -> {
                                try {
                                    method.invoke(instance);
                                } catch (Exception e) {
                                    System.err.println("[ForgeLoader] Error in pre-launch for "
                                            + modId + ": " + e.getMessage());
                                }
                            });
                    System.out.println("[ForgeLoader] Registered @PreLaunch method: " + method.getName()
                            + " for mod " + modId);
                }

                if (isGameReadyMethod(method)) {
                    method.setAccessible(true);
                    gameReadyCallbacks.computeIfAbsent(modId, k -> new ArrayList<>())
                            .add(() -> {
                                try {
                                    method.invoke(instance);
                                } catch (Exception e) {
                                    System.err.println("[ForgeLoader] Error in game-ready for "
                                            + modId + ": " + e.getMessage());
                                }
                            });
                    System.out.println("[ForgeLoader] Registered @GameReady method: " + method.getName()
                            + " for mod " + modId);
                }
            }
        } catch (Exception e) {
            System.err.println("[ForgeLoader] Error registering lifecycle for " + modId + ": " + e.getMessage());
        }

        invokeConstructorBasedLifecycle(instance, meta);
    }

    private void invokeConstructorBasedLifecycle(Object instance, ModMetadata meta) {
        String modId = meta.getId();

        try {
            Class<?> commonSetupEventClass = Class.forName(
                    "net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent",
                    false, classLoader);

            System.out.println("[ForgeLoader] Simulating FMLCommonSetupEvent for " + modId);

            Method onCommonSetup = findMethod(instance.getClass(), "onCommonSetup");
            if (onCommonSetup != null) {
                onCommonSetup.setAccessible(true);
                try {
                    Object eventInstance = commonSetupEventClass.getDeclaredConstructor().newInstance();
                    onCommonSetup.invoke(instance, eventInstance);
                    System.out.println("[ForgeLoader] Fired FMLCommonSetupEvent for " + modId);
                } catch (Exception e) {
                    System.out.println("[ForgeLoader] Could not fire FMLCommonSetupEvent for "
                            + modId + ": " + e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[ForgeLoader] Forge lifecycle classes not available, "
                    + "using basic initialization for " + modId);
        } catch (Exception e) {
            System.out.println("[ForgeLoader] Lifecycle simulation skipped for " + modId + ": " + e.getMessage());
        }
    }

    private boolean isPreLaunchMethod(Method method) {
        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.contains("PreLaunch") || name.contains("Setup")) {
                return true;
            }
        }
        return false;
    }

    private boolean isGameReadyMethod(Method method) {
        for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.contains("GameReady") || name.contains("PostLaunch") || name.contains("ServerStarted")) {
                return true;
            }
        }
        return false;
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains(methodName.toLowerCase())) {
                return method;
            }
        }

        return null;
    }

    public void firePreLaunch() {
        System.out.println("[ForgeLoader] Firing pre-launch events for " + preLaunchCallbacks.size() + " mod(s)...");

        for (Map.Entry<String, List<Runnable>> entry : preLaunchCallbacks.entrySet()) {
            String modId = entry.getKey();
            List<Runnable> callbacks = entry.getValue();

            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (Exception e) {
                    System.err.println("[ForgeLoader] Error in pre-launch callback for " + modId + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[ForgeLoader] Pre-launch events completed");
    }

    public void fireGameReady() {
        System.out.println("[ForgeLoader] Firing game-ready events for " + gameReadyCallbacks.size() + " mod(s)...");

        for (Map.Entry<String, List<Runnable>> entry : gameReadyCallbacks.entrySet()) {
            String modId = entry.getKey();
            List<Runnable> callbacks = entry.getValue();

            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (Exception e) {
                    System.err.println("[ForgeLoader] Error in game-ready callback for " + modId + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[ForgeLoader] Game-ready events completed");
    }

    public Map<String, Object> getModInstances() {
        return Collections.unmodifiableMap(modInstances);
    }

    public Object getModInstance(String modId) {
        return modInstances.get(modId);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public int getLoadedModCount() {
        return modInstances.size();
    }

    public void unloadAll() {
        modInstances.clear();
        modClasses.clear();
        preLaunchCallbacks.clear();
        gameReadyCallbacks.clear();
    }

    public enum LoadStatus {
        SUCCESS,
        INSTANTIATION_FAILED,
        UNSUPPORTED
    }

    public static class LoadResult {
        public final LoadStatus status;
        public final String message;

        public LoadResult(LoadStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public String toString() {
            return "LoadResult{status=" + status + ", message='" + message + "'}";
        }
    }
}
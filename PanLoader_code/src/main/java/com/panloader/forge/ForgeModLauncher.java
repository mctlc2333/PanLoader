package com.panloader.forge;

import com.panloader.api.ModMetadata;
import com.panloader.core.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ForgeModLauncher {

    public enum LaunchPhase {
        DISCOVERY,
        LOADING,
        PRE_INIT,
        INIT,
        POST_INIT,
        REGISTRY_SYNC,
        GAME_READY
    }

    public static class ModEntry {
        private final ModMetadata metadata;
        private final Path jarPath;
        private Class<?> modClass;
        private Object instance;
        private LaunchPhase currentPhase = LaunchPhase.DISCOVERY;
        private final List<String> detectedAnnotations = new ArrayList<>();
        private final Map<String, List<Method>> lifecycleMethods = new HashMap<>();
        private boolean initialized = false;

        public ModEntry(ModMetadata metadata, Path jarPath) {
            this.metadata = metadata;
            this.jarPath = jarPath;
        }

        public ModMetadata getMetadata() { return metadata; }
        public Path getJarPath() { return jarPath; }
        public Class<?> getModClass() { return modClass; }
        public void setModClass(Class<?> modClass) { this.modClass = modClass; }
        public Object getInstance() { return instance; }
        public void setInstance(Object instance) { this.instance = instance; }
        public LaunchPhase getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(LaunchPhase phase) { this.currentPhase = phase; }
        public List<String> getDetectedAnnotations() { return detectedAnnotations; }
        public Map<String, List<Method>> getLifecycleMethods() { return lifecycleMethods; }
        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }
    }

    public interface ModLifecycleListener {
        void onPhaseChange(ModEntry mod, LaunchPhase oldPhase, LaunchPhase newPhase);
        void onModLoaded(ModEntry mod);
        void onModError(ModEntry mod, Throwable error);
    }

    private static final ForgeModLauncher INSTANCE = new ForgeModLauncher();

    private final List<ModEntry> modEntries = new CopyOnWriteArrayList<>();
    private final Map<String, ModEntry> modEntryMap = new ConcurrentHashMap<>();
    private final List<ModLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final CrossContainerBus bus;
    private final ForgeEventBridge eventBridge;
    private final ForgeCapabilitySystem capabilitySystem;
    private final ForgeConfigSystem configSystem;
    private final ForgeNetworking networking;
    private final ForgeBiomeModifier biomeModifier;
    private LaunchPhase globalPhase = LaunchPhase.DISCOVERY;
    private ClassLoader classLoader;
    private boolean launcherInitialized = false;
    private long startTime;

    private ForgeModLauncher() {
        this.bus = CrossContainerBus.getInstance();
        this.eventBridge = ForgeEventBridge.getInstance();
        this.capabilitySystem = ForgeCapabilitySystem.getInstance();
        this.configSystem = ForgeConfigSystem.getInstance();
        this.networking = ForgeNetworking.getInstance();
        this.biomeModifier = ForgeBiomeModifier.getInstance();
    }

    public static ForgeModLauncher getInstance() {
        return INSTANCE;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public LaunchPhase getGlobalPhase() {
        return globalPhase;
    }

    public void addLifecycleListener(ModLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    public void removeLifecycleListener(ModLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    public void registerMod(ModMetadata metadata, Path jarPath) {
        ModEntry entry = new ModEntry(metadata, jarPath);
        modEntries.add(entry);
        modEntryMap.put(metadata.getId(), entry);
        System.out.println("[ForgeModLauncher] Registered mod: " + metadata.getName()
                + " v" + metadata.getVersion() + " (id: " + metadata.getId() + ")");
    }

    public ModEntry getModEntry(String modId) {
        return modEntryMap.get(modId);
    }

    public List<ModEntry> getAllModEntries() {
        return Collections.unmodifiableList(modEntries);
    }

    public void startLaunchSequence() {
        startTime = System.currentTimeMillis();
        System.out.println("[ForgeModLauncher] ========== Forge Mod Launch Sequence Start ==========");
        System.out.println("[ForgeModLauncher] Found " + modEntries.size() + " Forge mod(s) to load");

        try {
            executePhase(LaunchPhase.DISCOVERY, LaunchPhase.LOADING);
            executePhase(LaunchPhase.LOADING, LaunchPhase.PRE_INIT);
            executePhase(LaunchPhase.PRE_INIT, LaunchPhase.INIT);
            executePhase(LaunchPhase.INIT, LaunchPhase.POST_INIT);
            executePhase(LaunchPhase.POST_INIT, LaunchPhase.REGISTRY_SYNC);
            executePhase(LaunchPhase.REGISTRY_SYNC, LaunchPhase.GAME_READY);

            launcherInitialized = true;
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[ForgeModLauncher] ========== Forge Mod Launch Sequence Complete ==========");
            System.out.println("[ForgeModLauncher] Loaded " + getLoadedCount() + "/" + modEntries.size() + " mod(s) in " + elapsed + "ms");

            printLaunchSummary();

        } catch (Exception e) {
            System.err.println("[ForgeModLauncher] Launch sequence failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executePhase(LaunchPhase from, LaunchPhase to) {
        System.out.println("[ForgeModLauncher] --- Phase: " + to.name() + " ---");
        globalPhase = to;

        long phaseStart = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        for (ModEntry entry : modEntries) {
            try {
                switch (to) {
                    case LOADING:
                        phaseLoading(entry);
                        break;
                    case PRE_INIT:
                        phasePreInit(entry);
                        break;
                    case INIT:
                        phaseInit(entry);
                        break;
                    case POST_INIT:
                        phasePostInit(entry);
                        break;
                    case REGISTRY_SYNC:
                        phaseRegistrySync(entry);
                        break;
                    case GAME_READY:
                        phaseGameReady(entry);
                        break;
                    default:
                        break;
                }
                successCount++;
                notifyPhaseChange(entry, to);
            } catch (Exception e) {
                failCount++;
                System.err.println("[ForgeModLauncher] Error in " + to.name()
                        + " for " + entry.getMetadata().getId() + ": " + e.getMessage());
                notifyModError(entry, e);
            }
        }

        long elapsed = System.currentTimeMillis() - phaseStart;
        System.out.println("[ForgeModLauncher] Phase " + to.name() + " complete: "
                + successCount + " success, " + failCount + " failed (" + elapsed + "ms)");
    }

    private void phaseLoading(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();
        String entrypoint = meta.getEntrypoint();

        if (entrypoint == null || entrypoint.isEmpty()) {
            entrypoint = autoDetectModClass(entry.getJarPath());
            if (entrypoint == null) {
                throw new IllegalStateException("Cannot determine main class for " + meta.getId());
            }
            meta.setEntrypoint(entrypoint);
        }

        Class<?> modClass;
        try {
            modClass = classLoader.loadClass(entrypoint);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + entrypoint, e);
        }

        entry.setModClass(modClass);
        detectForgeAnnotations(entry);
        entry.setCurrentPhase(LaunchPhase.LOADING);

        System.out.println("[ForgeModLauncher] LOADING: " + meta.getName()
                + " -> " + modClass.getName() + " [annotations: " + entry.getDetectedAnnotations() + "]");
    }

    private void detectForgeAnnotations(ModEntry entry) {
        Class<?> clazz = entry.getModClass();
        List<String> annotations = entry.getDetectedAnnotations();

        try {
            for (java.lang.annotation.Annotation annotation : clazz.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                annotations.add(name);
            }

            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Method method : current.getDeclaredMethods()) {
                    for (java.lang.annotation.Annotation ann : method.getAnnotations()) {
                        String annName = ann.annotationType().getSimpleName();
                        if (isLifecycleAnnotation(annName)) {
                            entry.getLifecycleMethods()
                                    .computeIfAbsent(annName, k -> new ArrayList<>())
                                    .add(method);
                        }
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Exception e) {
            System.err.println("[ForgeModLauncher] Error detecting annotations for "
                    + entry.getMetadata().getId() + ": " + e.getMessage());
        }
    }

    private boolean isLifecycleAnnotation(String name) {
        return name.contains("PreLaunch") || name.contains("GameReady")
                || name.contains("SubscribeEvent") || name.contains("Mod")
                || name.contains("Config") || name.contains("EventBus");
    }

    private void phasePreInit(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();
        Class<?> modClass = entry.getModClass();

        System.out.println("[ForgeModLauncher] PRE_INIT: " + meta.getName());

        if (hasForgeSpecificAPI(modClass)) {
            System.out.println("[ForgeModLauncher]   Note: " + meta.getName()
                    + " uses Forge-specific APIs. Full functionality requires Forge runtime.");
        }

        Object instance;
        try {
            java.lang.reflect.Constructor<?> defaultCtor = modClass.getDeclaredConstructor();
            defaultCtor.setAccessible(true);
            instance = defaultCtor.newInstance();
        } catch (NoSuchMethodException e) {
            java.lang.reflect.Constructor<?>[] constructors = modClass.getDeclaredConstructors();
            if (constructors.length > 0) {
                constructors[0].setAccessible(true);
                instance = constructors[0].newInstance();
                System.out.println("[ForgeModLauncher]   Using non-default constructor for " + meta.getName());
            } else {
                throw new RuntimeException("No constructor found for " + modClass.getName());
            }
        }

        entry.setInstance(instance);
        entry.setCurrentPhase(LaunchPhase.PRE_INIT);

        eventBridge.fireForgeEvent(ForgeEventType.FML_PRE_INIT, createModData(meta, "pre_init"));
        eventBridge.fireForgeEvent(ForgeEventType.MOD_LOADING, createModData(meta, "loading"));
    }

    private void phaseInit(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();
        Object instance = entry.getInstance();

        System.out.println("[ForgeModLauncher] INIT: " + meta.getName());

        invokeLifecycleCallbacks(instance, entry, "PreLaunch");

        ForgeModLoadingContext.getInstance().setActiveMod(meta.getId());

        eventBridge.fireForgeEvent(ForgeEventType.MOD_CONFIG_LOADING, createModData(meta, "config_loading"));

        configSystem.loadConfigsForMod(meta.getId());

        eventBridge.fireForgeEvent(ForgeEventType.MOD_CONFIG_LOADED, createModData(meta, "config_loaded"));

        DeferredRegisterShim deferredShim = ForgeShim.getInstance()
                .createDeferredRegister(meta.getId(), "forge:" + meta.getId());

        forgeDeferredRegisterEntries(entry, deferredShim);

        ForgeModLoadingContext.getInstance().addInitCallback(() -> {
            try {
                invokeLifecycleCallbacks(instance, entry, "CommonSetup");
                invokeLifecycleCallbacks(instance, entry, "ClientSetup");
            } catch (Exception e) {
                System.err.println("[ForgeModLauncher] Error in init callbacks for "
                        + meta.getId() + ": " + e.getMessage());
            }
        });

        eventBridge.fireForgeEvent(ForgeEventType.FML_COMMON_SETUP, createModData(meta, "common_setup"));

        entry.setCurrentPhase(LaunchPhase.INIT);
    }

    private void forgeDeferredRegisterEntries(ModEntry entry, DeferredRegisterShim deferredShim) {
        ModMetadata meta = entry.getMetadata();
        Object instance = entry.getInstance();

        System.out.println("[ForgeModLauncher]   Processing deferred registers for " + meta.getName());

        try {
            Map<String, List<Method>> lifecycleMethods = entry.getLifecycleMethods();

            for (Map.Entry<String, List<Method>> methodEntry : lifecycleMethods.entrySet()) {
                String annotationName = methodEntry.getKey();
                List<Method> methods = methodEntry.getValue();

                for (Method method : methods) {
                    String methodName = method.getName();

                    if (methodName.toLowerCase().contains("register") ||
                            methodName.toLowerCase().contains("defer")) {
                        System.out.println("[ForgeModLauncher]   Found registration method: " + methodName
                                + " in " + meta.getName());
                    }
                }
            }

            List<String> registries = Arrays.asList(
                    "forge:blocks", "forge:items", "forge:entities",
                    "forge:biomes", "forge:fluids", "forge:sounds",
                    "forge:structures", "forge:features"
            );

            for (String registryName : registries) {
                registerDefaultRegistry(meta.getId(), registryName);
            }

        } catch (Exception e) {
            System.err.println("[ForgeModLauncher] Error processing deferred registers for "
                    + meta.getId() + ": " + e.getMessage());
        }
    }

    private void registerDefaultRegistry(String modId, String registryName) {
        CrossContainerBus.RegistryProxy proxy = bus.getRegistryProxy();
        proxy.markRegistered(registryName, modId);
        System.out.println("[ForgeModLauncher]   Marked registry " + registryName + " for " + modId);
    }

    private void phasePostInit(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();
        Object instance = entry.getInstance();

        System.out.println("[ForgeModLauncher] POST_INIT: " + meta.getName());

        invokeLifecycleCallbacks(instance, entry, "GameReady");
        invokeLifecycleCallbacks(instance, entry, "PostLaunch");

        capabilitySystem.attachCapabilitiesForMod(meta.getId(), instance);

        eventBridge.fireForgeEvent(ForgeEventType.MOD_LOADED, createModData(meta, "loaded"));

        entry.setCurrentPhase(LaunchPhase.POST_INIT);
        entry.setInitialized(true);

        notifyModLoaded(entry);
    }

    private void phaseRegistrySync(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();

        System.out.println("[ForgeModLauncher] REGISTRY_SYNC: " + meta.getName());

        ForgeShim.getInstance().registerAllForgeMods();

        CrossContainerBus.RegistryProxy proxy = bus.getRegistryProxy();

        String[] registryNames = {
                "forge:blocks", "forge:items", "forge:entities",
                "forge:biomes", "forge:fluids", "forge:sounds",
                "forge:structures", "forge:features"
        };

        for (String registryName : registryNames) {
            proxy.syncRegistry(registryName);
        }

        biomeModifier.syncBiomeModifiers(meta.getId());

        networking.registerNetworkPayloadsForMod(meta.getId());

        System.out.println("[ForgeModLauncher]   Registry sync complete for " + meta.getName());
        entry.setCurrentPhase(LaunchPhase.REGISTRY_SYNC);
    }

    private void phaseGameReady(ModEntry entry) throws Exception {
        ModMetadata meta = entry.getMetadata();
        Object instance = entry.getInstance();

        System.out.println("[ForgeModLauncher] GAME_READY: " + meta.getName());

        eventBridge.fireForgeEvent(ForgeEventType.FML_LOAD_COMPLETE, createModData(meta, "load_complete"));

        invokeLifecycleCallbacks(instance, entry, "ServerStarted");

        entry.setCurrentPhase(LaunchPhase.GAME_READY);
    }

    private void invokeLifecycleCallbacks(Object instance, ModEntry entry, String phaseName) {
        Map<String, List<Method>> lifecycleMethods = entry.getLifecycleMethods();

        for (Map.Entry<String, List<Method>> methodEntry : lifecycleMethods.entrySet()) {
            String annotationName = methodEntry.getKey();

            if (annotationName.contains(phaseName) || annotationName.contains("SubscribeEvent")) {
                List<Method> methods = methodEntry.getValue();
                for (Method method : methods) {
                    try {
                        method.setAccessible(true);
                        Class<?>[] paramTypes = method.getParameterTypes();

                        if (paramTypes.length == 0) {
                            method.invoke(instance);
                        } else if (paramTypes.length == 1) {
                            Object event = createEvent(paramTypes[0], entry.getMetadata());
                            method.invoke(instance, event);
                        }
                    } catch (Exception e) {
                        System.err.println("[ForgeModLauncher] Error invoking " + phaseName
                                + " callback " + method.getName() + " for "
                                + entry.getMetadata().getId() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private Object createEvent(Class<?> eventClass, ModMetadata meta) {
        try {
            java.lang.reflect.Constructor<?> ctor = eventClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            return createFallbackEvent(eventClass, meta);
        }
    }

    private Object createFallbackEvent(Class<?> eventClass, ModMetadata meta) {
        try {
            java.lang.reflect.Constructor<?>[] constructors = eventClass.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> ctor : constructors) {
                ctor.setAccessible(true);
                Class<?>[] paramTypes = ctor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = getDefaultValue(paramTypes[i]);
                }
                return ctor.newInstance(args);
            }
        } catch (Exception e) {
            System.out.println("[ForgeModLauncher] Could not create event instance for "
                    + eventClass.getSimpleName() + " in " + meta.getId());
        }
        return null;
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == String.class) return "";
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : null;
        }
        return null;
    }

    private boolean hasForgeSpecificAPI(Class<?> clazz) {
        String className = clazz.getName();
        return className.contains("net.minecraftforge")
                || className.contains("cpw.mods")
                || className.contains("forge.mods");
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
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ForgeModLauncher] Error scanning JAR: " + e.getMessage());
        }

        return null;
    }

    private boolean hasModAnnotation(Class<?> clazz) {
        try {
            for (java.lang.annotation.Annotation annotation : clazz.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                if (name.equals("Mod") || name.equals("mod")) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private Map<String, Object> createModData(ModMetadata meta, String phase) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", meta.getId());
        data.put("modName", meta.getName());
        data.put("version", meta.getVersion());
        data.put("phase", phase);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    private void notifyPhaseChange(ModEntry entry, LaunchPhase newPhase) {
        LaunchPhase oldPhase = entry.getCurrentPhase();
        for (ModLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onPhaseChange(entry, oldPhase, newPhase);
            } catch (Exception e) {
                System.err.println("[ForgeModLauncher] Error in phase change listener: " + e.getMessage());
            }
        }
    }

    private void notifyModLoaded(ModEntry entry) {
        for (ModLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onModLoaded(entry);
            } catch (Exception e) {
                System.err.println("[ForgeModLauncher] Error in mod loaded listener: " + e.getMessage());
            }
        }
    }

    private void notifyModError(ModEntry entry, Throwable error) {
        for (ModLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onModError(entry, error);
            } catch (Exception e) {
                System.err.println("[ForgeModLauncher] Error in mod error listener: " + e.getMessage());
            }
        }
    }

    public int getLoadedCount() {
        int count = 0;
        for (ModEntry entry : modEntries) {
            if (entry.isInitialized()) {
                count++;
            }
        }
        return count;
    }

    public void printLaunchSummary() {
        System.out.println("[ForgeModLauncher] === Launch Summary ===");
        Map<LaunchPhase, List<ModEntry>> phaseGroups = new EnumMap<>(LaunchPhase.class);

        for (ModEntry entry : modEntries) {
            LaunchPhase phase = entry.getCurrentPhase();
            phaseGroups.computeIfAbsent(phase, k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<LaunchPhase, List<ModEntry>> group : phaseGroups.entrySet()) {
            System.out.println("[ForgeModLauncher]   Phase " + group.getKey() + ": "
                    + group.getValue().size() + " mod(s)");
            for (ModEntry mod : group.getValue()) {
                ModMetadata meta = mod.getMetadata();
                System.out.println("[ForgeModLauncher]     - " + meta.getName()
                        + " v" + meta.getVersion() + " (id: " + meta.getId()
                        + ", initialized: " + mod.isInitialized() + ")");
            }
        }

        System.out.println("[ForgeModLauncher]   Total: " + getLoadedCount() + "/" + modEntries.size() + " loaded");
        System.out.println("[ForgeModLauncher]   Capability registrations: " + capabilitySystem.getRegistrationCount());
        System.out.println("[ForgeModLauncher]   Config registrations: " + configSystem.getConfigCount());
        System.out.println("[ForgeModLauncher]   Network payloads: " + networking.getPayloadCount());
        System.out.println("[ForgeModLauncher]   Biome modifiers: " + biomeModifier.getModifierCount());
    }

    public boolean isLauncherInitialized() {
        return launcherInitialized;
    }

    public void reset() {
        modEntries.clear();
        modEntryMap.clear();
        globalPhase = LaunchPhase.DISCOVERY;
        launcherInitialized = false;
        startTime = 0;
    }
}
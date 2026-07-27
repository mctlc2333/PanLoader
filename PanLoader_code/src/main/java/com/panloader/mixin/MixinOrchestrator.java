package com.panloader.mixin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MixinOrchestrator {

    private static final MixinOrchestrator INSTANCE = new MixinOrchestrator();

    private final Map<String, MixinConfigProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredConfigs = new ConcurrentHashMap<>();
    private final Map<String, Priority> containerPriorities = new ConcurrentHashMap<>();
    private final List<String> globalExclusions = new ArrayList<>();
    private boolean initialized = false;

    private MixinOrchestrator() {
    }

    public static MixinOrchestrator getInstance() {
        return INSTANCE;
    }

    public void registerProvider(MixinConfigProvider provider) {
        String containerId = provider.getContainerId();
        providers.put(containerId, provider);
        containerPriorities.put(containerId, provider.getMixinPriority());

        List<String> configPaths = provider.getMixinConfigPaths();
        registeredConfigs.put(containerId, new ArrayList<>(configPaths));

        System.out.println("[MixinOrchestrator] Registered provider: " + containerId
                + " (priority: " + provider.getMixinPriority()
                + ", configs: " + configPaths + ")");
    }

    public void unregisterProvider(String containerId) {
        providers.remove(containerId);
        registeredConfigs.remove(containerId);
        containerPriorities.remove(containerId);
    }

    public void addGlobalExclusion(String className) {
        globalExclusions.add(className);
    }

    public void initialize() {
        if (initialized) {
            System.out.println("[MixinOrchestrator] Already initialized");
            return;
        }

        System.out.println("[MixinOrchestrator] Initializing Mixin orchestration...");
        System.out.println("[MixinOrchestrator] Registered providers: " + providers.size());

        List<Map.Entry<String, List<String>>> sortedConfigs = getSortedConfigs();
        System.out.println("[MixinOrchestrator] Mixin config resolution order:");
        for (Map.Entry<String, List<String>> entry : sortedConfigs) {
            Priority priority = containerPriorities.get(entry.getKey());
            System.out.println("[MixinOrchestrator]   [" + priority + "] " + entry.getKey()
                    + ": " + entry.getValue());
        }

        initialized = true;
        System.out.println("[MixinOrchestrator] Mixin orchestrator initialized successfully");
    }

    private List<Map.Entry<String, List<String>>> getSortedConfigs() {
        List<Map.Entry<String, List<String>>> sorted = new ArrayList<>(registeredConfigs.entrySet());
        sorted.sort((a, b) -> {
            Priority pa = containerPriorities.getOrDefault(a.getKey(), Priority.NORMAL);
            Priority pb = containerPriorities.getOrDefault(b.getKey(), Priority.NORMAL);
            return pa.compareTo(pb);
        });
        return sorted;
    }

    public List<String> getOrderedMixinConfigs() {
        List<String> result = new ArrayList<>();
        List<Map.Entry<String, List<String>>> sorted = getSortedConfigs();
        for (Map.Entry<String, List<String>> entry : sorted) {
            result.addAll(entry.getValue());
        }
        return result;
    }

    public void applyToEnvironment(Object mixinEnv, Class<?> envClass) throws Exception {
        List<String> configs = getOrderedMixinConfigs();

        try {
            Method addConfig = envClass.getMethod("addConfig", String.class);
            for (String config : configs) {
                try {
                    addConfig.invoke(mixinEnv, config);
                    System.out.println("[MixinOrchestrator] Applied config: " + config);
                } catch (Exception e) {
                    System.err.println("[MixinOrchestrator] Failed to apply config " + config + ": " + e.getMessage());
                }
            }
        } catch (NoSuchMethodException e) {
            System.out.println("[MixinOrchestrator] addConfig method not found on " + envClass.getName());
        }

        if (!globalExclusions.isEmpty()) {
            try {
                Method setExclusions = envClass.getMethod("setExcludedClasses", Set.class);
                setExclusions.invoke(mixinEnv, new HashSet<>(globalExclusions));
                System.out.println("[MixinOrchestrator] Applied " + globalExclusions.size() + " global exclusions");
            } catch (NoSuchMethodException e) {
                System.out.println("[MixinOrchestrator] setExcludedClasses method not found on " + envClass.getName());
            }
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public MixinConfigProvider getProvider(String containerId) {
        return providers.get(containerId);
    }

    public int getRegisteredProviderCount() {
        return providers.size();
    }
}

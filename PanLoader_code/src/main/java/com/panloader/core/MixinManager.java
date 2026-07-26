package com.panloader.core;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

public class MixinManager {

    private static final String CONFIG_FILE = "panloader.mixins.json";

    private final Instrumentation instrumentation;
    private final Set<String> loadedMixinConfigs = new HashSet<>();
    private boolean initialized = false;
    private Object mixinTransformer;

    public MixinManager(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public void init() {
        if (instrumentation == null) {
            System.out.println("[PanLoader] No instrumentation available, Mixin support disabled");
            return;
        }

        try {
            System.setProperty("mixin.env.remap", "true");
            System.setProperty("mixin.checks", "false");
            System.setProperty("mixin.debug", "false");

            Class<?> transformerClass = tryLoadClass(
                    "org.spongepowered.asm.mixin.transformer.MixinTransformer",
                    "net.fabricmc.sponge.mixin.transformer.MixinTransformer"
            );

            if (transformerClass == null) {
                System.out.println("[PanLoader] MixinTransformer class not found, Mixin support disabled");
                System.out.println("[PanLoader] Add Mixin to your classpath to enable Mixin transformations");
                return;
            }

            mixinTransformer = transformerClass.getDeclaredConstructor().newInstance();

            Class<?> mixinEnvClass = tryLoadClass(
                    "org.spongepowered.asm.mixin.MixinEnvironment",
                    "net.fabricmc.sponge.mixin.MixinEnvironment"
            );

            if (mixinEnvClass != null) {
                Object env = mixinEnvClass.getMethod("getDefaultEnvironment").invoke(null);
                registerConfig(env, mixinEnvClass, CONFIG_FILE);
                loadedMixinConfigs.add(CONFIG_FILE);
            }

            instrumentation.addTransformer((ClassFileTransformer) mixinTransformer, true);

            initialized = true;
            System.out.println("[PanLoader] Mixin transformer registered successfully");
            System.out.println("[PanLoader] Mixin configs: " + loadedMixinConfigs);
        } catch (Exception e) {
            System.err.println("[PanLoader] Failed to initialize Mixin: " + e.getMessage());
        }
    }

    private Class<?> tryLoadClass(String... classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                // try next
            }
        }
        return null;
    }

    private void registerConfig(Object env, Class<?> envClass, String configName) {
        try {
            InputStream configStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(configName);

            if (configStream != null) {
                Method addConfig = envClass.getMethod("addConfig", String.class);
                addConfig.invoke(env, configName);
                System.out.println("[PanLoader] Registered mixin config: " + configName);
            } else {
                URL configUrl = Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(configName);
                if (configUrl != null) {
                    Method addConfig = envClass.getMethod("addConfig", String.class);
                    addConfig.invoke(env, configName);
                    System.out.println("[PanLoader] Registered mixin config from URL: " + configUrl);
                } else {
                    System.out.println("[PanLoader] Mixin config not found on classpath: " + configName);
                }
            }
        } catch (Exception e) {
            System.err.println("[PanLoader] Failed to register mixin config " + configName + ": " + e.getMessage());
        }
    }

    public void registerMixinConfig(String configName) {
        if (loadedMixinConfigs.contains(configName)) {
            return;
        }
        loadedMixinConfigs.add(configName);

        if (!initialized) {
            System.out.println("[PanLoader] Mixin not yet initialized, deferring config: " + configName);
            return;
        }

        try {
            Class<?> mixinEnvClass = tryLoadClass(
                    "org.spongepowered.asm.mixin.MixinEnvironment",
                    "net.fabricmc.sponge.mixin.MixinEnvironment"
            );
            if (mixinEnvClass != null) {
                Object env = mixinEnvClass.getMethod("getDefaultEnvironment").invoke(null);
                registerConfig(env, mixinEnvClass, configName);
            }
        } catch (Exception e) {
            System.err.println("[PanLoader] Failed to register mixin config " + configName + ": " + e.getMessage());
        }
    }

    public void registerMixinConfigsFromMod(Path jarPath) {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            List<String> configsFound = new ArrayList<>();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".mixins.json") || name.endsWith(".mixin.json")) {
                    String configName = name.substring(0, name.lastIndexOf('.'));
                    configsFound.add(configName);
                }
            }
            for (String config : configsFound) {
                registerMixinConfig(config);
            }
        } catch (Exception e) {
            System.err.println("[PanLoader] Error scanning for mixin configs in " + jarPath.getFileName() + ": " + e.getMessage());
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public Set<String> getLoadedMixinConfigs() {
        return Collections.unmodifiableSet(loadedMixinConfigs);
    }
}

package com.panloader.core;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

public class FabricLoaderIntegrator {

    private final FabricContainer fabricContainer;
    private final String mcVersion;
    private ClassLoader fabricClassLoader;
    private boolean fabricInitialized = false;

    public FabricLoaderIntegrator(FabricContainer fabricContainer, String mcVersion) {
        this.fabricContainer = fabricContainer;
        this.mcVersion = mcVersion;
    }

    public void initializeFabricLoader() throws Exception {
        if (fabricContainer.getModJarPaths().isEmpty()) {
            System.out.println("[FabricLoader] No Fabric mods found, skipping Fabric Loader initialization");
            return;
        }

        System.out.println("[FabricLoader] Initializing Fabric Loader with "
                + fabricContainer.getModJarPaths().size() + " mod(s)...");

        List<URL> classpathUrls = new ArrayList<>();

        for (Path jarPath : fabricContainer.getModJarPaths()) {
            classpathUrls.add(jarPath.toUri().toURL());
            System.out.println("[FabricLoader] Added mod to classpath: " + jarPath.getFileName());
        }

        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources("");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String path = url.getPath();
                if (path.contains("fabric-loader")) {
                    classpathUrls.add(url);
                    System.out.println("[FabricLoader] Found Fabric Loader at: " + path);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[FabricLoader] Could not enumerate classpath for Fabric Loader: " + e.getMessage());
        }

        fabricClassLoader = new URLClassLoader(
                classpathUrls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader()
        );

        Thread.currentThread().setContextClassLoader(fabricClassLoader);

        try {
            Class<?> knotClientClass = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClient",
                    true, fabricClassLoader);
            System.out.println("[FabricLoader] Found KnotClient: " + knotClientClass.getName());

            System.out.println("[FabricLoader] Fabric Loader successfully initialized");
            fabricInitialized = true;

        } catch (ClassNotFoundException e) {
            System.out.println("[FabricLoader] KnotClient not found in classpath. "
                    + "Attempting alternative initialization...");
            initializeFabricModsManually();
        } catch (Exception e) {
            System.err.println("[FabricLoader] Error initializing Fabric Loader: " + e.getMessage());
            e.printStackTrace(System.err);
            initializeFabricModsManually();
        }
    }

    private void initializeFabricModsManually() {
        System.out.println("[FabricLoader] Falling back to manual Fabric mod initialization...");

        for (var meta : fabricContainer.getModMetadataList()) {
            try {
                String entrypoint = meta.getEntrypoint();
                if (entrypoint == null || entrypoint.isEmpty()) {
                    System.out.println("[FabricLoader] No entrypoint for " + meta.getId() + ", skipping");
                    continue;
                }

                Class<?> modClass = fabricClassLoader.loadClass(entrypoint);

                try {
                    Class<?> initializerClass = Class.forName("net.fabricmc.api.ModInitializer",
                            false, fabricClassLoader);
                    if (initializerClass.isAssignableFrom(modClass)) {
                        Object instance = modClass.getDeclaredConstructor().newInstance();
                        java.lang.reflect.Method onInitialize = initializerClass.getMethod("onInitialize");
                        onInitialize.invoke(instance);
                        System.out.println("[FabricLoader] Initialized via ModInitializer: "
                                + meta.getName());
                    } else {
                        System.out.println("[FabricLoader] Entrypoint does not implement ModInitializer: "
                                + entrypoint);
                    }
                } catch (ClassNotFoundException e) {
                    System.out.println("[FabricLoader] ModInitializer not available, "
                            + "trying raw instantiation of " + entrypoint);
                    Object instance = modClass.getDeclaredConstructor().newInstance();
                    System.out.println("[FabricLoader] Instantiated: " + meta.getName());
                }
            } catch (Exception e) {
                System.err.println("[FabricLoader] Failed to initialize "
                        + meta.getName() + ": " + e.getMessage());
            }
        }

        fabricInitialized = true;
    }

    public void launchWithFabric(String[] originalMinecraftArgs) throws Exception {
        if (!fabricInitialized) {
            System.out.println("[FabricLoader] Fabric not initialized, skipping Fabric launch");
            return;
        }

        System.out.println("[FabricLoader] Attempting Fabric-aware launch...");

        String[] fabricArgs = buildFabricArgs(originalMinecraftArgs);

        try {
            Class<?> knotClientClass = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClient",
                    true, fabricClassLoader);

            Method mainMethod = knotClientClass.getMethod("main", String[].class);
            System.out.println("[FabricLoader] Launching via KnotClient.main()...");
            mainMethod.invoke(null, (Object) fabricArgs);

        } catch (ClassNotFoundException e) {
            System.out.println("[FabricLoader] KnotClient not available, using standard Minecraft launch");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("[FabricLoader] Fabric launch failed: " + cause.getMessage());
            cause.printStackTrace(System.err);
            throw e;
        }
    }

    private String[] buildFabricArgs(String[] originalArgs) {
        List<String> args = new ArrayList<>();

        Path gameDir = fabricContainer.getGameDir();
        args.add("--gameDir");
        args.add(gameDir.toString());
        args.add("--version");
        args.add(mcVersion);
        args.add("--versionType");
        args.add("PanLoader+Fabric");
        args.add("--loader");
        args.add("fabric-loader");

        for (int i = 0; i < originalArgs.length; i++) {
            if (originalArgs[i].equals("--username") && i + 1 < originalArgs.length) {
                args.add("--username");
                args.add(originalArgs[++i]);
            } else if (originalArgs[i].equals("--uuid") && i + 1 < originalArgs.length) {
                args.add("--uuid");
                args.add(originalArgs[++i]);
            }
        }

        System.out.println("[FabricLoader] Built launch args: " + Arrays.toString(args.toArray()));
        return args.toArray(new String[0]);
    }

    public ClassLoader getFabricClassLoader() {
        return fabricClassLoader;
    }

    public boolean isFabricInitialized() {
        return fabricInitialized;
    }

    public int getFabricModCount() {
        return fabricContainer.getLoadedModCount();
    }

    public List<Path> getFabricModPaths() {
        return fabricContainer.getModJarPaths();
    }
}
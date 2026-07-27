package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;
import com.panloader.mixin.Priority;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FabricContainer implements ModContainer {

    private final String containerId;
    private final List<Path> modJarPaths = new ArrayList<>();
    private final List<ModMetadata> modMetadataList = new ArrayList<>();
    private ClassLoader classLoader;
    private ClassLoader gameClassLoader;
    private boolean initialized = false;
    private final Path gameDir;

    public FabricContainer(String containerId, Path gameDir) {
        this.containerId = containerId;
        this.gameDir = gameDir;
    }

    @Override
    public Priority getMixinPriority() {
        return Priority.LOW;
    }

    @Override
    public List<String> getMixinConfigPaths() {
        List<String> configs = new ArrayList<>();
        for (Path jarPath : modJarPaths) {
            configs.add(jarPath.getFileName().toString().replace(".jar", "") + ".mixins.json");
        }
        return configs;
    }

    @Override
    public ContainerType getContainerType() {
        return ContainerType.FABRIC;
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public void addMod(Path jarPath) throws Exception {
        modJarPaths.add(jarPath);
        com.panloader.mixin.MixinManager.getInstance().registerMixinConfigsFromMod(jarPath);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("fabric.mod.json");
            if (entry != null) {
                String json = readJson(jar, entry);
                ModMetadata meta = parseFabricModJson(json, jarPath);
                if (meta != null) {
                    modMetadataList.add(meta);
                    System.out.println("[Fabric-" + containerId + "] Detected Fabric mod: "
                            + meta.getName() + " v" + meta.getVersion() + " (from " + jarPath.getFileName() + ")");
                }
            }
        }
    }

    private String readJson(JarFile jar, JarEntry entry) throws Exception {
        try (InputStream is = jar.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private ModMetadata parseFabricModJson(String json, Path jarPath) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            ModMetadata meta = new ModMetadata();

            meta.setId(getJsonString(root, "id", jarPath.getFileName().toString().replace(".jar", "")));
            meta.setVersion(getJsonString(root, "version", "0.0.0"));
            meta.setName(getJsonString(root, "name", meta.getId()));
            meta.setDescription(getJsonString(root, "description", ""));

            if (root.has("entrypoints") && root.get("entrypoints").isJsonObject()) {
                com.google.gson.JsonObject entrypoints = root.getAsJsonObject("entrypoints");

                String mainEntrypoint = findEntrypoint(entrypoints, "main");
                if (mainEntrypoint != null) {
                    meta.setEntrypoint(mainEntrypoint);
                }

                String clientEntrypoint = findEntrypoint(entrypoints, "client");
                if (clientEntrypoint != null) {
                    meta.setClientEntrypoint(clientEntrypoint);
                }

                String serverEntrypoint = findEntrypoint(entrypoints, "server");
                if (serverEntrypoint != null) {
                    meta.setServerEntrypoint(serverEntrypoint);
                }

                if (meta.getEntrypoint() == null && meta.getClientEntrypoint() != null) {
                    meta.setEntrypoint(meta.getClientEntrypoint());
                }

                if (meta.getEntrypoint() == null && meta.getServerEntrypoint() != null) {
                    meta.setEntrypoint(meta.getServerEntrypoint());
                }
            }

            if (root.has("authors")) {
                StringBuilder authors = new StringBuilder();
                for (com.google.gson.JsonElement author : root.getAsJsonArray("authors")) {
                    if (authors.length() > 0) authors.append(", ");
                    if (author.isJsonObject()) {
                        authors.append(author.getAsJsonObject().get("name").getAsString());
                    } else {
                        authors.append(author.getAsString());
                    }
                }
                meta.setAuthor(authors.toString());
            }

            return meta;
        } catch (Exception e) {
            System.err.println("[Fabric-" + containerId + "] Failed to parse fabric.mod.json: " + e.getMessage());
            return null;
        }
    }

    private String findEntrypoint(com.google.gson.JsonObject entrypoints, String key) {
        if (!entrypoints.has(key)) {
            return null;
        }
        com.google.gson.JsonElement epValue = entrypoints.get(key);
        if (epValue.isJsonArray()) {
            com.google.gson.JsonArray arr = epValue.getAsJsonArray();
            if (arr.size() == 0) return null;

            com.google.gson.JsonElement first = arr.get(0);
            if (first.isJsonObject()) {
                com.google.gson.JsonObject obj = first.getAsJsonObject();
                if (obj.has("value")) {
                    return obj.get("value").getAsString();
                }
                if (obj.has("entrypoint")) {
                    return obj.get("entrypoint").getAsString();
                }
                for (Map.Entry<String, com.google.gson.JsonElement> prop : obj.entrySet()) {
                    if (prop.getValue().isJsonPrimitive()) {
                        return prop.getValue().getAsString();
                    }
                }
                return null;
            } else if (first.isJsonPrimitive()) {
                return first.getAsString();
            }
            return null;
        } else if (epValue.isJsonPrimitive()) {
            return epValue.getAsString();
        }
        return null;
    }

    private String getJsonString(com.google.gson.JsonObject obj, String key, String defaultVal) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return defaultVal;
    }

    @Override
    public void initialize() throws Exception {
        com.panloader.mixin.MixinOrchestrator.getInstance().registerProvider(this);

        System.out.println("[Fabric-" + containerId + "] Preparing Fabric Loader for " + modMetadataList.size()
                + " Fabric mod(s)...");

        List<URL> urls = new ArrayList<>();
        for (Path jarPath : modJarPaths) {
            urls.add(jarPath.toUri().toURL());
        }

        ClassLoader parentCL = gameClassLoader != null ? gameClassLoader : getClass().getClassLoader();
        classLoader = new URLClassLoader(urls.toArray(new URL[0]), parentCL);

        CrossContainerBus.getInstance().getRegistryProxy().syncAllRegistries();

        System.out.println("[Fabric-" + containerId + "] Fabric container initialized with "
                + modJarPaths.size() + " mod JAR(s)");
        System.out.println("[Fabric-" + containerId + "] Mod list:");
        for (ModMetadata meta : modMetadataList) {
            System.out.println("[Fabric-" + containerId + "]   - " + meta.getName() + " v"
                    + meta.getVersion() + " (id: " + meta.getId() + ")");
        }

        initialized = true;
    }

    @Override
    public void setGameClassLoader(ClassLoader gameClassLoader) {
        this.gameClassLoader = gameClassLoader;
    }

    @Override
    public void notifyGameReady() {
        System.out.println("[Fabric-" + containerId + "] Game ready callback for Fabric mods");
    }

    @Override
    public void notifyPreLaunch() {
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader != null ? classLoader : getClass().getClassLoader();
    }

    @Override
    public List<URL> getClasspathEntries() {
        List<URL> entries = new ArrayList<>();
        for (Path p : modJarPaths) {
            try {
                entries.add(p.toUri().toURL());
            } catch (Exception e) {
                System.err.println("[Fabric-" + containerId + "] Invalid URL: " + p);
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
        Set<String> ids = new LinkedHashSet<>();
        for (ModMetadata m : modMetadataList) {
            ids.add(m.getId());
        }
        return ids;
    }

    @Override
    public PanMod getMod(String modId) {
        return null;
    }

    @Override
    public ModMetadata getModMetadata(String modId) {
        for (ModMetadata m : modMetadataList) {
            if (m.getId().equals(modId)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public int getLoadedModCount() {
        return modMetadataList.size();
    }

    @Override
    public void unloadAll() {
        modJarPaths.clear();
        modMetadataList.clear();
        initialized = false;

        if (classLoader instanceof java.net.URLClassLoader) {
            try {
                ((java.net.URLClassLoader) classLoader).close();
            } catch (Exception e) {
                System.err.println("[Fabric-" + containerId + "] Error closing classloader: " + e.getMessage());
            }
        }
        classLoader = null;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public List<ModMetadata> getModMetadataList() {
        return Collections.unmodifiableList(modMetadataList);
    }

    public String[] buildFabricLaunchArgs(String mcVersion) {
        List<String> args = new ArrayList<>();
        args.add("--gameDir");
        args.add(gameDir.toString());
        args.add("--version");
        args.add(mcVersion);
        args.add("--loader");
        args.add("fabric-loader");

        System.out.println("[Fabric-" + containerId + "] Fabric launch args prepared: " + args);
        return args.toArray(new String[0]);
    }

    @Override
    public String toString() {
        return "FabricContainer{" +
                "id='" + containerId + '\'' +
                ", mods=" + modMetadataList.size() +
                ", jars=" + modJarPaths.size() +
                '}';
    }
}
package com.panloader.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LaunchHandler {

    private static final String MINECRAFT_MAIN_CLASS = "net.minecraft.client.main.Main";
    private static final String FABRIC_LOADER_MAIN = "net.fabricmc.loader.impl.launch.knot.Knot";

    private final String mcVersion;
    private final String gameDir;
    private final ModManager modManager;
    private final MixinManager mixinManager;
    private String assetIndexId;

    public LaunchHandler(String mcVersion, String gameDir, ModManager modManager, MixinManager mixinManager) {
        this.mcVersion = mcVersion;
        this.gameDir = gameDir;
        this.modManager = modManager;
        this.mixinManager = mixinManager;
        this.assetIndexId = mcVersion;
    }

    public void launch(String[] originalArgs) throws Exception {
        System.out.println("[PanLoader] Initializing Mixin support...");
        mixinManager.init();

        System.out.println("[PanLoader] Preparing Minecraft launch...");

        Path gameDirPath = Path.of(gameDir);
        Path libDir = gameDirPath.resolve("libraries");
        Path versionsDir = gameDirPath.resolve("versions");
        Path versionDir = versionsDir.resolve(mcVersion);

        loadAssetIndexId(versionDir);

        List<URL> classpath = new ArrayList<>();
        List<String> classpathEntries = new ArrayList<>();

        addVersionJsonLibraries(classpath, classpathEntries, libDir, versionDir);
        addMinecraftVersion(classpath, classpathEntries, versionDir);
        addFabricLoaderClasses(classpath, classpathEntries);
        addModClasses(classpath, classpathEntries);
        addPanLoaderClasses(classpath, classpathEntries);

        if (classpath.isEmpty()) {
            System.err.println("[PanLoader] FATAL: Classpath is empty. Cannot launch Minecraft.");
            System.err.println("[PanLoader] Make sure Minecraft is installed at: " + gameDir);
            throw new IllegalStateException("Empty classpath, cannot launch Minecraft");
        }

        System.out.println("[PanLoader] Classpath contains " + classpath.size() + " entries:");
        for (String entry : classpathEntries) {
            System.out.println("  - " + entry);
        }

        URLClassLoader gameClassLoader = new URLClassLoader(
                classpath.toArray(new URL[0]),
                null
        );

        modManager.setGameClassLoader(gameClassLoader);
        Thread.currentThread().setContextClassLoader(gameClassLoader);

        String[] minecraftArgs = buildMinecraftArgs(originalArgs);

        System.out.println("[PanLoader] Launching Minecraft " + mcVersion + "...");
        System.out.println("[PanLoader] Main class: " + MINECRAFT_MAIN_CLASS);

        modManager.notifyPreLaunch();

        try {
            Class<?> mainClass = gameClassLoader.loadClass(MINECRAFT_MAIN_CLASS);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) minecraftArgs);
        } catch (ClassNotFoundException e) {
            System.err.println("[PanLoader] Minecraft main class not found: " + MINECRAFT_MAIN_CLASS);
            System.err.println("[PanLoader] This usually means Minecraft " + mcVersion + " is not installed.");
            System.err.println("[PanLoader] Please install Minecraft via the Minecraft Launcher first.");
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("[PanLoader] Failed to launch Minecraft: " + cause.getMessage());
            cause.printStackTrace(System.err);
            throw e;
        }
    }

    private void addPanLoaderClasses(List<URL> classpath, List<String> entries) {
        try {
            URL panLoaderUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
            if (panLoaderUrl != null) {
                classpath.add(panLoaderUrl);
                entries.add("PanLoader -> " + panLoaderUrl.getPath());
            }
        } catch (Exception e) {
            System.err.println("[PanLoader] Failed to add PanLoader classes: " + e.getMessage());
        }
    }

    private void addVersionJsonLibraries(List<URL> classpath, List<String> entries, Path libDir, Path versionDir) {
        Path versionJson = versionDir.resolve(mcVersion + ".json");
        if (!Files.exists(versionJson)) {
            System.err.println("[PanLoader] Version JSON not found: " + versionJson);
            System.err.println("[PanLoader] Falling back to full libraries scan...");
            addLibrariesFallback(classpath, entries, libDir);
            return;
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean isWindows = osName.contains("windows");

        Set<String> addedPaths = new LinkedHashSet<>();
        int added = 0;
        int skipped = 0;

        try (Reader reader = Files.newBufferedReader(versionJson)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("libraries")) {
                System.err.println("[PanLoader] No 'libraries' array in version JSON");
                addLibrariesFallback(classpath, entries, libDir);
                return;
            }

            JsonArray libraries = root.getAsJsonArray("libraries");
            System.out.println("[PanLoader] Parsing " + libraries.size() + " libraries from version JSON...");

            for (JsonElement libElem : libraries) {
                if (!libElem.isJsonObject()) continue;
                JsonObject libObj = libElem.getAsJsonObject();

                if (!isLibraryAllowed(libObj, isWindows)) {
                    continue;
                }

                String libPath = getLibraryPath(libObj);
                if (libPath == null || libPath.isEmpty()) {
                    skipped++;
                    continue;
                }

                Path fullPath = libDir.resolve(libPath);
                if (!Files.exists(fullPath)) {
                    if (addedPaths.add(libPath)) {
                        skipped++;
                    }
                    continue;
                }

                if (addedPaths.add(libPath)) {
                    try {
                        classpath.add(fullPath.toUri().toURL());
                        added++;
                    } catch (MalformedURLException e) {
                        System.err.println("[PanLoader] Invalid library URL: " + fullPath);
                    }
                }
            }

            entries.add("Minecraft libraries (version JSON, " + added + " added, " + skipped + " skipped)");
            System.out.println("[PanLoader] Added " + added + " libraries from version JSON");

        } catch (Exception e) {
            System.err.println("[PanLoader] Error parsing version JSON: " + e.getMessage());
            addLibrariesFallback(classpath, entries, libDir);
        }
    }

    private boolean isLibraryAllowed(JsonObject libObj, boolean isWindows) {
        if (!libObj.has("rules") || !libObj.get("rules").isJsonArray()) {
            return true;
        }

        JsonArray rules = libObj.getAsJsonArray("rules");
        boolean allowed = false;

        for (JsonElement ruleElem : rules) {
            if (!ruleElem.isJsonObject()) continue;
            JsonObject rule = ruleElem.getAsJsonObject();

            String action = rule.has("action") ? rule.get("action").getAsString() : "allow";
            boolean osMatches = true;

            if (rule.has("os") && rule.get("os").isJsonObject()) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    String requiredOs = os.get("name").getAsString();
                    osMatches = isWindows ? requiredOs.equals("windows") : !requiredOs.equals("windows");
                }
            }

            if (action.equals("allow") && osMatches) {
                allowed = true;
            } else if (action.equals("disallow") && osMatches) {
                allowed = false;
            }
        }

        return allowed;
    }

    private String getLibraryPath(JsonObject libObj) {
        if (libObj.has("downloads") && libObj.get("downloads").isJsonObject()) {
            JsonObject downloads = libObj.getAsJsonObject("downloads");
            if (downloads.has("artifact") && downloads.get("artifact").isJsonObject()) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                if (artifact.has("path")) {
                    return artifact.get("path").getAsString();
                }
            }
        }

        if (libObj.has("name")) {
            String name = libObj.get("name").getAsString();
            return mavenNameToPath(name);
        }

        return null;
    }

    private String mavenNameToPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return null;

        StringBuilder path = new StringBuilder();
        path.append(parts[0].replace('.', '/'));
        path.append('/');
        path.append(parts[1]);
        path.append('/');
        path.append(parts[2]);
        path.append('/');

        String classifier = parts.length >= 4 ? parts[3] : null;
        path.append(parts[1]);
        path.append('-');
        path.append(parts[2]);
        if (classifier != null && !classifier.isEmpty()) {
            path.append('-');
            path.append(classifier);
        }
        path.append(".jar");

        return path.toString();
    }

    private void addLibrariesFallback(List<URL> classpath, List<String> entries, Path libDir) {
        if (!Files.exists(libDir)) {
            System.out.println("[PanLoader] Libraries directory not found: " + libDir);
            return;
        }

        try {
            List<Path> jars = new ArrayList<>();
            Files.walk(libDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jars::add);

            jars.sort(Comparator.comparing(p -> p.getFileName().toString()));

            for (Path jar : jars) {
                try {
                    classpath.add(jar.toUri().toURL());
                } catch (MalformedURLException e) {
                    System.err.println("[PanLoader] Invalid library URL: " + jar);
                }
            }
            entries.add("Minecraft libraries (fallback, " + jars.size() + " jars)");
            System.out.println("[PanLoader] Fallback: added " + jars.size() + " libraries");
        } catch (Exception e) {
            System.err.println("[PanLoader] Error scanning libraries: " + e.getMessage());
        }
    }

    private void addMinecraftVersion(List<URL> classpath, List<String> entries, Path versionDir) {
        if (!Files.exists(versionDir)) {
            System.err.println("[PanLoader] Version directory not found: " + versionDir);
            System.err.println("[PanLoader] Expected path: " + versionDir);
            System.err.println("[PanLoader] Please install Minecraft " + mcVersion + " via the Minecraft Launcher.");
            return;
        }

        Path clientJar = versionDir.resolve(mcVersion + ".jar");
        if (Files.exists(clientJar)) {
            try {
                classpath.add(clientJar.toUri().toURL());
                entries.add("Minecraft " + mcVersion + " -> " + clientJar.toString());
                System.out.println("[PanLoader] Added Minecraft client JAR: " + clientJar.getFileName());
            } catch (MalformedURLException e) {
                System.err.println("[PanLoader] Invalid Minecraft JAR URL: " + clientJar);
            }
        } else {
            System.err.println("[PanLoader] Minecraft client JAR not found: " + clientJar);
            System.err.println("[PanLoader] Please run Minecraft " + mcVersion + " at least once via the official launcher.");
        }

        Path versionJson = versionDir.resolve(mcVersion + ".json");
        if (Files.exists(versionJson)) {
            entries.add("Version JSON -> " + versionJson.toString());
        }
    }

    private void addModClasses(List<URL> classpath, List<String> entries) {
        for (String modId : modManager.getLoadedModIds()) {
            ModClassLoader modCl = modManager.getClassLoader(modId);
            if (modCl != null) {
                try {
                    Path jarPath = modCl.getJarPath();
                    URL url = jarPath.toUri().toURL();
                    classpath.add(url);
                    entries.add("Mod[" + modId + "] -> " + jarPath.getFileName());
                    System.out.println("[PanLoader] Added mod to classpath: " + modId);
                } catch (MalformedURLException e) {
                    System.err.println("[PanLoader] Invalid mod JAR URL for " + modId + ": " + e.getMessage());
                }
            }
        }
    }

    private void addFabricLoaderClasses(List<URL> classpath, List<String> entries) {
        try {
            Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources("");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String path = url.getPath();
                if (path.contains("fabric-loader")) {
                    classpath.add(url);
                    entries.add("Fabric Loader -> " + path);
                    System.out.println("[PanLoader] Added Fabric Loader from: " + path);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[PanLoader] Fabric Loader not found on classpath (not required for direct launch)");
        }
    }

    private void loadAssetIndexId(Path versionDir) {
        Path versionJson = versionDir.resolve(mcVersion + ".json");
        if (!Files.exists(versionJson)) {
            System.err.println("[PanLoader] Version JSON not found for asset index lookup");
            return;
        }

        try (Reader reader = Files.newBufferedReader(versionJson)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("assetIndex") && root.get("assetIndex").isJsonObject()) {
                JsonObject assetIndex = root.getAsJsonObject("assetIndex");
                if (assetIndex.has("id")) {
                    assetIndexId = assetIndex.get("id").getAsString();
                    System.out.println("[PanLoader] Asset index: " + assetIndexId);
                }
            }
        } catch (Exception e) {
            System.err.println("[PanLoader] Error reading asset index: " + e.getMessage());
        }
    }

    private String[] buildMinecraftArgs(String[] originalArgs) {
        List<String> args = new ArrayList<>();

        args.add("--gameDir");
        args.add(gameDir);
        args.add("--version");
        args.add(mcVersion);
        args.add("--versionType");
        args.add("PanLoader");

        Path assetsDir = Path.of(gameDir, "assets");
        args.add("--assetsDir");
        args.add(assetsDir.toString());

        args.add("--assetIndex");
        args.add(assetIndexId);
        args.add("--accessToken");
        args.add("0");
        args.add("--userProperties");
        args.add("{}");
        args.add("--userType");
        args.add("mojang");

        for (int i = 0; i < originalArgs.length; i++) {
            if (originalArgs[i].equals("--username") && i + 1 < originalArgs.length) {
                args.add("--username");
                args.add(originalArgs[++i]);
            } else if (originalArgs[i].equals("--uuid") && i + 1 < originalArgs.length) {
                args.add("--uuid");
                args.add(originalArgs[++i]);
            }
        }

        return args.toArray(new String[0]);
    }
}

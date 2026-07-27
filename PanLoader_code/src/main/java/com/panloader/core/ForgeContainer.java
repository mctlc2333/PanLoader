package com.panloader.core;

import com.panloader.api.ModMetadata;
import com.panloader.api.PanMod;
import com.panloader.forge.ForgeShim;
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

public class ForgeContainer implements ModContainer {

    private final String containerId;
    private final List<Path> modJarPaths = new ArrayList<>();
    private final List<ModMetadata> modMetadataList = new ArrayList<>();
    private final Map<String, Object> forgeModInstances = new HashMap<>();
    private ClassLoader classLoader;
    private ClassLoader gameClassLoader;
    private boolean initialized = false;
    private final Path gameDir;
    private ForgeModLoader forgeModLoader;
    private final ForgeEventBridge eventBridge;
    private final ForgeShim forgeShim;

    public ForgeContainer(String containerId, Path gameDir) {
        this.containerId = containerId;
        this.gameDir = gameDir;
        this.eventBridge = ForgeEventBridge.getInstance();
        this.forgeShim = ForgeShim.getInstance();
    }

    @Override
    public Priority getMixinPriority() {
        return Priority.NORMAL;
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
        return ContainerType.FORGE;
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
            JarEntry entry = jar.getJarEntry("mods.toml");
            if (entry != null) {
                String toml = readToml(jar, entry);
                ModMetadata meta = parseForgeModsToml(toml, jarPath);
                if (meta != null) {
                    modMetadataList.add(meta);
                    System.out.println("[Forge-" + containerId + "] Detected Forge mod: "
                            + meta.getName() + " v" + meta.getVersion() + " (from " + jarPath.getFileName() + ")");
                }
            }
        }
    }

    private String readToml(JarFile jar, JarEntry entry) throws Exception {
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

    private ModMetadata parseForgeModsToml(String toml, Path jarPath) {
        ModMetadata meta = new ModMetadata();
        meta.setId(jarPath.getFileName().toString().replace(".jar", ""));
        meta.setVersion("0.0.0");
        meta.setName(meta.getId());

        String[] lines = toml.split("\n");
        boolean inModsSection = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("[[mods]]") || trimmed.startsWith("[mods")) {
                inModsSection = true;
                continue;
            } else if (trimmed.startsWith("[") && !trimmed.startsWith("[[mods]]")) {
                inModsSection = false;
                continue;
            }

            if (!inModsSection) continue;

            if (trimmed.startsWith("modId") || trimmed.startsWith("mod_id")) {
                meta.setId(extractTomlValue(trimmed));
            } else if (trimmed.startsWith("version")) {
                meta.setVersion(extractTomlValue(trimmed));
            } else if (trimmed.startsWith("displayName") || trimmed.startsWith("display_name")) {
                meta.setName(extractTomlValue(trimmed));
            } else if (trimmed.startsWith("description")) {
                meta.setDescription(extractTomlValue(trimmed));
            } else if (trimmed.startsWith("authors")) {
                meta.setAuthor(extractTomlValue(trimmed));
            }
        }

        return meta;
    }

    private String extractTomlValue(String line) {
        int eqIdx = line.indexOf('=');
        if (eqIdx == -1) return "";
        String value = line.substring(eqIdx + 1).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        } else if (value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    private void scanForgeModClasses() {
        System.out.println("[Forge-" + containerId + "] Scanning for Forge mod classes with classLoader...");

        for (int i = 0; i < modJarPaths.size(); i++) {
            Path jarPath = modJarPaths.get(i);
            if (i >= modMetadataList.size()) break;

            ModMetadata meta = modMetadataList.get(i);
            String mainClass = findModMainClass(jarPath);

            if (mainClass != null) {
                meta.setEntrypoint(mainClass);
                System.out.println("[Forge-" + containerId + "] Found @Mod main class for "
                        + meta.getName() + ": " + mainClass);
            } else {
                String detectedClass = findAnyModClass(jarPath);
                if (detectedClass != null) {
                    meta.setEntrypoint(detectedClass);
                    System.out.println("[Forge-" + containerId + "] Auto-detected main class for "
                            + meta.getName() + ": " + detectedClass);
                } else {
                    System.out.println("[Forge-" + containerId + "] No main class found for "
                            + meta.getName() + ", will try manifest fallback");
                    String manifestClass = findMainClassByManifest(jarPath);
                    if (manifestClass != null) {
                        meta.setEntrypoint(manifestClass);
                    }
                }
            }
        }
    }

    private String findModMainClass(Path jarPath) {
        String mainClass = null;

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    try {
                        Class<?> clazz = Class.forName(className, false, classLoader);

                        if (hasForgeModAnnotation(clazz)) {
                            mainClass = className;
                            break;
                        }
                    } catch (NoClassDefFoundError e) {
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Forge-" + containerId + "] Error scanning JAR " + jarPath.getFileName() + ": " + e.getMessage());
        }

        return mainClass;
    }

    private boolean hasForgeModAnnotation(Class<?> clazz) {
        try {
            for (java.lang.annotation.Annotation annotation : clazz.getAnnotations()) {
                String annotationName = annotation.annotationType().getCanonicalName();
                String simpleName = annotation.annotationType().getSimpleName();

                if (annotationName.equals("net.minecraftforge.fml.common.Mod")
                        || annotationName.equals("net.minecraftforge.fml.common.Mod$Container")
                        || annotationName.equals("cpw.mods.fml.common.Mod")
                        || simpleName.equals("Mod")) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private String findAnyModClass(Path jarPath) {
        String detectedClass = null;

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    try {
                        Class<?> clazz = Class.forName(className, false, classLoader);

                        if (clazz.getSimpleName().equalsIgnoreCase("Mod")
                                || clazz.getName().endsWith(".Mod")) {
                            continue;
                        }

                        boolean hasAnnotation = false;
                        for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
                            String annName = ann.annotationType().getSimpleName();
                            if (annName.equals("Mod")) {
                                hasAnnotation = true;
                                break;
                            }
                        }

                        if (hasAnnotation) {
                            detectedClass = className;
                            break;
                        }
                    } catch (NoClassDefFoundError e) {
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
        }

        return detectedClass;
    }

    private String findMainClassByManifest(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mainClass = manifest.getMainAttributes().getValue("Main-Class");
                if (mainClass != null) {
                    return mainClass;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public void initialize() throws Exception {
        com.panloader.mixin.MixinOrchestrator.getInstance().registerProvider(this);

        System.out.println("[Forge-" + containerId + "] Initializing " + modMetadataList.size()
                + " Forge mod(s)...");

        List<URL> urls = new ArrayList<>();
        for (Path jarPath : modJarPaths) {
            urls.add(jarPath.toUri().toURL());
        }

        ClassLoader parentCL = gameClassLoader != null ? gameClassLoader : getClass().getClassLoader();
        classLoader = new URLClassLoader(urls.toArray(new URL[0]), parentCL);

        scanForgeModClasses();

        forgeModLoader = new ForgeModLoader(classLoader, gameDir);
        forgeModLoader.loadForgeMods(modMetadataList, modJarPaths);

        forgeShim.registerAllForgeMods();

        initialized = true;

        System.out.println("[Forge-" + containerId + "] Forge container initialized with "
                + modMetadataList.size() + " mod(s)");
        for (ModMetadata meta : modMetadataList) {
            System.out.println("[Forge-" + containerId + "]   - " + meta.getName() + " v"
                    + meta.getVersion() + " (id: " + meta.getId()
                    + ", entrypoint: " + (meta.getEntrypoint() != null ? meta.getEntrypoint() : "N/A") + ")");
        }
    }

    @Override
    public void setGameClassLoader(ClassLoader gameClassLoader) {
        this.gameClassLoader = gameClassLoader;
    }

    @Override
    public void notifyGameReady() {
        if (forgeModLoader != null) {
            forgeModLoader.fireGameReady();
        }
        eventBridge.fireGameReadyEvents();
        CrossContainerBus.getInstance().getRegistryProxy().syncAllRegistries();
        System.out.println("[Forge-" + containerId + "] Game ready callback for Forge mods");
    }

    @Override
    public void notifyPreLaunch() {
        if (forgeModLoader != null) {
            forgeModLoader.firePreLaunch();
        }
        eventBridge.firePreLaunchEvents();
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
                System.err.println("[Forge-" + containerId + "] Invalid URL: " + p);
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
        forgeModInstances.clear();
        initialized = false;

        if (forgeModLoader != null) {
            try {
                forgeModLoader.unloadAll();
            } catch (Exception e) {
                System.err.println("[Forge-" + containerId + "] Error unloading forge mod loader: " + e.getMessage());
            }
            forgeModLoader = null;
        }

        if (classLoader instanceof java.net.URLClassLoader) {
            try {
                ((java.net.URLClassLoader) classLoader).close();
            } catch (Exception e) {
                System.err.println("[Forge-" + containerId + "] Error closing classloader: " + e.getMessage());
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

    public ForgeModLoader getForgeModLoader() {
        return forgeModLoader;
    }

    @Override
    public String toString() {
        return "ForgeContainer{" +
                "id='" + containerId + '\'' +
                ", mods=" + modMetadataList.size() +
                ", jars=" + modJarPaths.size() +
                '}';
    }
}
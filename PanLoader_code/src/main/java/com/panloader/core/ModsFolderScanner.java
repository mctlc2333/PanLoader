package com.panloader.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ModsFolderScanner {

    private final Path modsFolder;

    public ModsFolderScanner(Path modsFolder) {
        this.modsFolder = modsFolder;
    }

    public List<ModCandidate> scan() {
        List<ModCandidate> candidates = new ArrayList<>();

        if (!Files.exists(modsFolder)) {
            System.out.println("[PanLoader] Mods folder not found, creating: " + modsFolder);
            try {
                Files.createDirectories(modsFolder);
            } catch (IOException e) {
                System.err.println("[PanLoader] Failed to create mods folder: " + e.getMessage());
            }
            return candidates;
        }

        if (!Files.isDirectory(modsFolder)) {
            System.err.println("[PanLoader] Mods path is not a directory: " + modsFolder);
            return candidates;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsFolder, "*.jar")) {
            for (Path jarPath : stream) {
                ModCandidate candidate = detectMod(jarPath);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        } catch (IOException e) {
            System.err.println("[PanLoader] Error scanning mods folder: " + e.getMessage());
        }

        Collections.sort(candidates, (a, b) -> {
            int typeCompare = Integer.compare(a.getType().priority(), b.getType().priority());
            if (typeCompare != 0) return typeCompare;
            return a.getJarPath().getFileName().toString()
                    .compareTo(b.getJarPath().getFileName().toString());
        });

        return candidates;
    }

    private ModCandidate detectMod(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            if (jar.getJarEntry("panloader.mod.json") != null) {
                return new ModCandidate(jarPath, ModType.PANLOADER);
            }
            if (jar.getJarEntry("fabric.mod.json") != null) {
                return new ModCandidate(jarPath, ModType.FABRIC);
            }
            if (jar.getJarEntry("mods.toml") != null) {
                return new ModCandidate(jarPath, ModType.FORGE);
            }
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String fmlModId = manifest.getMainAttributes().getValue("FMLModId");
                String fabricModId = manifest.getMainAttributes().getValue("Fabric-Mod-Id");
                if (fmlModId != null) {
                    return new ModCandidate(jarPath, ModType.FORGE);
                }
                if (fabricModId != null) {
                    return new ModCandidate(jarPath, ModType.FABRIC);
                }
            }
            return new ModCandidate(jarPath, ModType.UNKNOWN);
        } catch (IOException e) {
            System.err.println("[PanLoader] Error reading JAR " + jarPath.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    public enum ModType {
        PANLOADER(0),
        FABRIC(1),
        FORGE(2),
        UNKNOWN(3);

        private final int priority;

        ModType(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    public static class ModCandidate {
        private final Path jarPath;
        private final ModType type;

        public ModCandidate(Path jarPath, ModType type) {
            this.jarPath = jarPath;
            this.type = type;
        }

        public Path getJarPath() {
            return jarPath;
        }

        public ModType getType() {
            return type;
        }
    }
}

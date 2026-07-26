package com.panloader.core;

import com.panloader.api.PanMod;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ModClassLoader extends URLClassLoader {

    private final Path jarPath;
    private final String modId;

    public ModClassLoader(Path jarPath, String modId, ClassLoader parent) {
        super(new URL[]{toUrl(jarPath)}, parent);
        this.jarPath = jarPath;
        this.modId = modId;
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert path to URL: " + path, e);
        }
    }

    public PanMod createModInstance(String entrypoint) throws Exception {
        Class<?> modClass = loadClass(entrypoint);
        if (!PanMod.class.isAssignableFrom(modClass)) {
            throw new IllegalArgumentException("Entrypoint class does not implement PanMod: " + entrypoint);
        }
        return (PanMod) modClass.getDeclaredConstructor().newInstance();
    }

    public Path getJarPath() {
        return jarPath;
    }

    public String getModId() {
        return modId;
    }

    @Override
    public String toString() {
        return "ModClassLoader{modId='" + modId + "', jarPath=" + jarPath + '}';
    }
}

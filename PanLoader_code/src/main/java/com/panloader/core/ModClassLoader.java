package com.panloader.core;

import com.panloader.api.PanMod;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModClassLoader extends URLClassLoader {

    private final Path jarPath;
    private final String modId;
    private final Map<String, WeakReference<Class<?>>> classCache = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

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

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (closed) {
            throw new ClassNotFoundException("ModClassLoader[" + modId + "] is closed, cannot load: " + name);
        }

        WeakReference<Class<?>> cachedRef = classCache.get(name);
        if (cachedRef != null) {
            Class<?> cached = cachedRef.get();
            if (cached != null) {
                return cached;
            } else {
                classCache.remove(name);
            }
        }

        Class<?> clazz = super.loadClass(name, resolve);
        classCache.put(name, new WeakReference<>(clazz));
        return clazz;
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

    public int getCachedClassCount() {
        classCache.entrySet().removeIf(e -> e.getValue().get() == null);
        return classCache.size();
    }

    public boolean isClosed() {
        return closed;
    }

    public void evictStaleCacheEntries() {
        classCache.entrySet().removeIf(e -> e.getValue().get() == null);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        classCache.clear();
        super.close();
    }

    @Override
    public String toString() {
        return "ModClassLoader{modId='" + modId + "', jarPath=" + jarPath
                + ", cachedClasses=" + classCache.size()
                + ", closed=" + closed + '}';
    }
}

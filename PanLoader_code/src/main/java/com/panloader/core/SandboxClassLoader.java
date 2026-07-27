package com.panloader.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public class SandboxClassLoader extends URLClassLoader {

    private final String sandboxId;
    private final Set<String> sharedPackages = new LinkedHashSet<>();
    private final Set<String> isolatedPackages = new LinkedHashSet<>();
    private final Map<String, WeakReference<Class<?>>> classCache = new ConcurrentHashMap<>();
    private final ClassLoader sharedClassLoader;
    private boolean isolatedMode = true;
    private volatile boolean closed = false;

    public SandboxClassLoader(String sandboxId, URL[] urls, ClassLoader sharedClassLoader) {
        super(urls, null);
        this.sandboxId = sandboxId;
        this.sharedClassLoader = sharedClassLoader;
    }

    public void addSharedPackage(String packageName) {
        sharedPackages.add(packageName);
    }

    public void addIsolatedPackage(String packageName) {
        isolatedPackages.add(packageName);
    }

    public void setIsolatedMode(boolean isolated) {
        this.isolatedMode = isolated;
    }

    public boolean isIsolatedMode() {
        return isolatedMode;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (closed) {
            throw new ClassNotFoundException("SandboxClassLoader[" + sandboxId + "] is closed, cannot load: " + name);
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

        if (shouldDelegateToShared(name)) {
            try {
                Class<?> clazz = sharedClassLoader.loadClass(name);
                classCache.put(name, new WeakReference<>(clazz));
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (ClassNotFoundException e) {
            }
        }

        if (isolatedMode && shouldBeIsolated(name)) {
            try {
                Class<?> clazz = findClass(name);
                classCache.put(name, new WeakReference<>(clazz));
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (ClassNotFoundException e) {
            }
        }

        try {
            Class<?> clazz = super.loadClass(name, resolve);
            classCache.put(name, new WeakReference<>(clazz));
            return clazz;
        } catch (ClassNotFoundException e) {
        }

        try {
            Class<?> clazz = sharedClassLoader.loadClass(name);
            classCache.put(name, new WeakReference<>(clazz));
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    private boolean shouldDelegateToShared(String className) {
        if (className.startsWith("java.")) {
            return true;
        }
        if (className.startsWith("javax.")) {
            return true;
        }
        if (className.startsWith("sun.")) {
            return true;
        }
        for (String pkg : sharedPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldBeIsolated(String className) {
        for (String pkg : isolatedPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    public void addURL(URL url) {
        super.addURL(url);
    }

    public void addJarPath(Path jarPath) {
        try {
            addURL(jarPath.toUri().toURL());
        } catch (Exception e) {
            System.err.println("[Sandbox-" + sandboxId + "] Failed to add JAR: " + jarPath + " - " + e.getMessage());
        }
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
        return "SandboxClassLoader{" +
                "id='" + sandboxId + '\'' +
                ", sharedPkgs=" + sharedPackages.size() +
                ", isolatedPkgs=" + isolatedPackages.size() +
                ", isolatedMode=" + isolatedMode +
                ", cachedClasses=" + classCache.size() +
                ", closed=" + closed +
                '}';
    }
}

package com.panloader.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DeferredRegisterShim {

    private final String modId;
    private final String registryName;
    private final List<DeferredEntry> entries = new ArrayList<>();
    private boolean registered = false;
    private final CrossContainerBus bus;

    public DeferredRegisterShim(String modId, String registryName) {
        this.modId = modId;
        this.registryName = registryName;
        this.bus = CrossContainerBus.getInstance();
    }

    public <T> DeferredEntry register(String name, Supplier<T> supplier) {
        DeferredEntry entry = new DeferredEntry(name, supplier, registryName);
        entries.add(entry);
        return entry;
    }

    public void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        System.out.println("[DeferredRegister-" + modId + "] Registering " + entries.size()
                + " entries to " + registryName);

        CrossContainerBus.RegistryProxy proxy = bus.getRegistryProxy();

        for (DeferredEntry entry : entries) {
            try {
                String fullId = modId + ":" + entry.name;
                Object created = entry.supplier.get();
                proxy.register(registryName, fullId, created, modId);

                System.out.println("[DeferredRegister-" + modId + "] Registered: " + fullId
                        + " -> " + created.getClass().getSimpleName());
            } catch (Exception e) {
                System.err.println("[DeferredRegister-" + modId + "] Failed to register "
                        + entry.name + ": " + e.getMessage());
            }
        }

        proxy.markRegistered(registryName, modId);
    }

    public List<DeferredEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isRegistered() {
        return registered;
    }

    public String getModId() {
        return modId;
    }

    public String getRegistryName() {
        return registryName;
    }

    public static class DeferredEntry {
        private final String name;
        private final Supplier<?> supplier;
        private final String registryName;
        private Object createdObject;

        DeferredEntry(String name, Supplier<?> supplier, String registryName) {
            this.name = name;
            this.supplier = supplier;
            this.registryName = registryName;
        }

        public String getName() {
            return name;
        }

        public Supplier<?> getSupplier() {
            return supplier;
        }

        public String getRegistryName() {
            return registryName;
        }

        public Object getCreatedObject() {
            return createdObject;
        }

        public void setCreatedObject(Object createdObject) {
            this.createdObject = createdObject;
        }
    }
}

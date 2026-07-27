package com.panloader.forge;

import com.panloader.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ForgeShim {

    private static final ForgeShim INSTANCE = new ForgeShim();

    private final Map<String, DeferredRegisterShim> deferredRegisters = new ConcurrentHashMap<>();
    private final ForgeEventBridge eventBridge;
    private final CrossContainerBus bus;

    private ForgeShim() {
        this.eventBridge = ForgeEventBridge.getInstance();
        this.bus = CrossContainerBus.getInstance();
    }

    public static ForgeShim getInstance() {
        return INSTANCE;
    }

    public DeferredRegisterShim createDeferredRegister(String modId, String registryName) {
        DeferredRegisterShim register = new DeferredRegisterShim(modId, registryName);
        deferredRegisters.put(modId + ":" + registryName, register);
        return register;
    }

    public DeferredRegisterShim getDeferredRegister(String modId, String registryName) {
        return deferredRegisters.get(modId + ":" + registryName);
    }

    public void registerAllForgeMods() {
        System.out.println("[ForgeShim] Registering all deferred registers...");
        for (DeferredRegisterShim register : deferredRegisters.values()) {
            try {
                register.registerAll();
            } catch (Exception e) {
                System.err.println("[ForgeShim] Error registering " + register.getRegistryName()
                        + " for " + register.getModId() + ": " + e.getMessage());
            }
        }
        System.out.println("[ForgeShim] All deferred registers processed");
    }

    public ForgeEventBridge getEventBridge() {
        return eventBridge;
    }

    public CrossContainerBus getBus() {
        return bus;
    }

    public void clear() {
        deferredRegisters.clear();
    }

    public int getTotalRegisteredCount() {
        int count = 0;
        for (DeferredRegisterShim register : deferredRegisters.values()) {
            count += register.getEntries().size();
        }
        return count;
    }
}

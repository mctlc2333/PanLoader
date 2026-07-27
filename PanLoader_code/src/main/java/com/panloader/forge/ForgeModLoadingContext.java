package com.panloader.forge;

import com.panloader.core.*;

import java.util.*;

public class ForgeModLoadingContext {

    private static final ForgeModLoadingContext INSTANCE = new ForgeModLoadingContext();

    private final ForgeEventBridge eventBridge;
    private final CrossContainerBus bus;
    private String activeModId;
    private final List<Runnable> initCallbacks = new ArrayList<>();
    private boolean initialized = false;

    private ForgeModLoadingContext() {
        this.eventBridge = ForgeEventBridge.getInstance();
        this.bus = CrossContainerBus.getInstance();
    }

    public static ForgeModLoadingContext getInstance() {
        return INSTANCE;
    }

    public void setActiveMod(String modId) {
        this.activeModId = modId;
    }

    public String getActiveModId() {
        return activeModId;
    }

    public DeferredRegisterShim createDeferredRegister(String registryName) {
        if (activeModId == null) {
            throw new IllegalStateException("No active mod context set for deferred register");
        }
        return ForgeShim.getInstance().createDeferredRegister(activeModId, registryName);
    }

    public void addInitCallback(Runnable callback) {
        initCallbacks.add(callback);
    }

    public void fireInitCallbacks() {
        if (initialized) {
            return;
        }
        initialized = true;

        System.out.println("[ForgeContext] Firing " + initCallbacks.size() + " init callbacks...");
        for (Runnable callback : initCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                System.err.println("[ForgeContext] Error in init callback: " + e.getMessage());
            }
        }
        System.out.println("[ForgeContext] Init callbacks complete");
    }

    public void fireCommonSetup() {
        eventBridge.fireForgeEvent(ForgeEventType.FML_COMMON_SETUP, new HashMap<>());
    }

    public void fireLoadComplete() {
        eventBridge.fireForgeEvent(ForgeEventType.FML_LOAD_COMPLETE, new HashMap<>());
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void clear() {
        initCallbacks.clear();
        initialized = false;
    }
}

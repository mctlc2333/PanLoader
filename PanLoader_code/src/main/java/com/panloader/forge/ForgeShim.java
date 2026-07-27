package com.panloader.forge;

import com.panloader.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ForgeShim {

    private static final ForgeShim INSTANCE = new ForgeShim();

    private final Map<String, DeferredRegisterShim> deferredRegisters = new ConcurrentHashMap<>();
    private final ForgeEventBridge eventBridge;
    private final CrossContainerBus bus;
    private final ForgeModLauncher modLauncher;
    private final ForgeCapabilitySystem capabilitySystem;
    private final ForgeConfigSystem configSystem;
    private final ForgeNetworking networking;
    private final ForgeBiomeModifier biomeModifier;

    private ForgeShim() {
        this.eventBridge = ForgeEventBridge.getInstance();
        this.bus = CrossContainerBus.getInstance();
        this.modLauncher = ForgeModLauncher.getInstance();
        this.capabilitySystem = ForgeCapabilitySystem.getInstance();
        this.configSystem = ForgeConfigSystem.getInstance();
        this.networking = ForgeNetworking.getInstance();
        this.biomeModifier = ForgeBiomeModifier.getInstance();
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

    public ForgeModLauncher getModLauncher() {
        return modLauncher;
    }

    public ForgeCapabilitySystem getCapabilitySystem() {
        return capabilitySystem;
    }

    public ForgeConfigSystem getConfigSystem() {
        return configSystem;
    }

    public ForgeNetworking getNetworking() {
        return networking;
    }

    public ForgeBiomeModifier getBiomeModifier() {
        return biomeModifier;
    }

    public void initializeAllSystems() {
        System.out.println("[ForgeShim] Initializing all Forge compatibility systems...");

        System.out.println("[ForgeShim]   ModLauncher: " + modLauncher.getLoadedCount() + " mods loaded");
        System.out.println("[ForgeShim]   CapabilitySystem: " + capabilitySystem.getRegistrationCount()
                + " capabilities, " + capabilitySystem.getAttachmentCount() + " attachments");
        System.out.println("[ForgeShim]   ConfigSystem: " + configSystem.getConfigCount() + " configs");
        System.out.println("[ForgeShim]   Networking: " + networking.getChannelCount()
                + " channels, " + networking.getPayloadCount() + " payloads");
        System.out.println("[ForgeShim]   BiomeModifier: " + biomeModifier.getModifierCount()
                + " rules, " + biomeModifier.getTotalModifications() + " modifications");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("modLauncherMods", modLauncher.getLoadedCount());
        stats.put("capabilityRegistrations", capabilitySystem.getRegistrationCount());
        stats.put("capabilityAttachments", capabilitySystem.getAttachmentCount());
        stats.put("configCount", configSystem.getConfigCount());
        stats.put("networkChannels", networking.getChannelCount());
        stats.put("networkPayloads", networking.getPayloadCount());
        stats.put("biomeModifiers", biomeModifier.getModifierCount());
        stats.put("biomeModifications", biomeModifier.getTotalModifications());

        bus.postEvent(new CrossContainerBus.RegistryEvent("forge.shim.init", "all", stats));

        System.out.println("[ForgeShim] All systems initialized successfully");
    }

    public void clear() {
        deferredRegisters.clear();
        modLauncher.reset();
        capabilitySystem.clear();
        configSystem.clear();
        networking.clearAll();
        biomeModifier.clear();
    }

    public int getTotalRegisteredCount() {
        int count = 0;
        for (DeferredRegisterShim register : deferredRegisters.values()) {
            count += register.getEntries().size();
        }
        return count;
    }

    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("version", ForgeModLoader.SHIM_VERSION);
        stats.put("totalDeferredRegisters", deferredRegisters.size());
        stats.put("totalRegisteredEntries", getTotalRegisteredCount());
        stats.put("modLauncherLoaded", modLauncher.getLoadedCount());
        stats.put("capabilityRegistrations", capabilitySystem.getRegistrationCount());
        stats.put("capabilityAttachments", capabilitySystem.getAttachmentCount());
        stats.put("configCount", configSystem.getConfigCount());
        stats.put("networkChannels", networking.getChannelCount());
        stats.put("networkPayloads", networking.getPayloadCount());
        stats.put("biomeModifiers", biomeModifier.getModifierCount());
        return stats;
    }
}

package com.panloader.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ForgeEventBridge {

    private static final ForgeEventBridge INSTANCE = new ForgeEventBridge();

    private final Map<String, List<CrossContainerBus.EventListener>> forgeEventListeners = new ConcurrentHashMap<>();
    private final Map<ForgeEventType, List<Consumer<ForgeEvent>>> directListeners = new ConcurrentHashMap<>();
    private final CrossContainerBus bus;

    private ForgeEventBridge() {
        this.bus = CrossContainerBus.getInstance();
    }

    public static ForgeEventBridge getInstance() {
        return INSTANCE;
    }

    public void registerForgeListener(ForgeEventType type, CrossContainerBus.EventListener listener) {
        String eventId = type.getEventId();
        forgeEventListeners.computeIfAbsent(eventId, k -> new ArrayList<>()).add(listener);

        bus.addEventListener(eventId, listener);

        System.out.println("[ForgeBridge] Registered listener for " + eventId);
    }

    public void registerForgeListener(ForgeEventType type, Consumer<ForgeEvent> listener) {
        directListeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);

        registerForgeListener(type, (CrossContainerBus.EventListener) event -> listener.accept((ForgeEvent) event));
    }

    public void fireForgeEvent(ForgeEventType type, Map<String, Object> data) {
        String eventId = type.getEventId();
        ForgeEvent event = new ForgeEvent(eventId, data);

        System.out.println("[ForgeBridge] Firing Forge event: " + eventId + " (data keys: "
                + (data != null ? data.keySet() : "none") + ")");

        List<CrossContainerBus.EventListener> listeners = forgeEventListeners.get(eventId);
        if (listeners != null) {
            for (CrossContainerBus.EventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    System.err.println("[ForgeBridge] Error in listener for " + eventId + ": " + e.getMessage());
                }
            }
        }

        List<Consumer<ForgeEvent>> directConsumers = directListeners.get(type);
        if (directConsumers != null) {
            for (Consumer<ForgeEvent> consumer : directConsumers) {
                try {
                    consumer.accept(event);
                } catch (Exception e) {
                    System.err.println("[ForgeBridge] Error in direct consumer for " + eventId + ": " + e.getMessage());
                }
            }
        }

        bus.postEvent(event);
    }

    public void firePreLaunchEvents() {
        System.out.println("[ForgeBridge] Firing pre-launch Forge events...");
        fireForgeEvent(ForgeEventType.FML_PRE_INIT, createPreLaunchData("pre_init"));
        fireForgeEvent(ForgeEventType.MOD_LOADING, createPreLaunchData("mod_loading"));
        fireForgeEvent(ForgeEventType.MOD_CONFIG_LOADING, createPreLaunchData("config_loading"));
        fireForgeEvent(ForgeEventType.MOD_CONFIG_LOADED, createPreLaunchData("config_loaded"));
        fireForgeEvent(ForgeEventType.MOD_LOADED, createPreLaunchData("mod_loaded"));
        fireForgeEvent(ForgeEventType.FML_COMMON_SETUP, createPreLaunchData("common_setup"));
        fireForgeEvent(ForgeEventType.FML_CLIENT_SETUP, createPreLaunchData("client_setup"));
        fireForgeEvent(ForgeEventType.REGISTER_BLOCKS, createPreLaunchData("register_blocks"));
        fireForgeEvent(ForgeEventType.REGISTER_ITEMS, createPreLaunchData("register_items"));
        fireForgeEvent(ForgeEventType.REGISTER_ENTITIES, createPreLaunchData("register_entities"));
        fireForgeEvent(ForgeEventType.REGISTER_BIOMES, createPreLaunchData("register_biomes"));
        fireForgeEvent(ForgeEventType.REGISTER_FLUIDS, createPreLaunchData("register_fluids"));
        fireForgeEvent(ForgeEventType.REGISTER_SOUNDS, createPreLaunchData("register_sounds"));
        fireForgeEvent(ForgeEventType.REGISTER_STRUCTURES, createPreLaunchData("register_structures"));
        fireForgeEvent(ForgeEventType.REGISTER_FEATURES, createPreLaunchData("register_features"));
        System.out.println("[ForgeBridge] Pre-launch Forge events completed");
    }

    public void fireGameReadyEvents() {
        System.out.println("[ForgeBridge] Firing game-ready Forge events...");
        fireForgeEvent(ForgeEventType.FML_SERVER_START, createPostLaunchData("server_start"));
        fireForgeEvent(ForgeEventType.FML_LOAD_COMPLETE, createPostLaunchData("load_complete"));
        fireForgeEvent(ForgeEventType.SERVER_STARTED, createPostLaunchData("server_started"));
        System.out.println("[ForgeBridge] Game-ready Forge events completed");
    }

    public void firePlayerLoggedIn(String playerName, String dimension) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("dimension", dimension);
        fireForgeEvent(ForgeEventType.PLAYER_LOGGED_IN, data);
    }

    public void firePlayerLoggedOut(String playerName) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        fireForgeEvent(ForgeEventType.PLAYER_LOGGED_OUT, data);
    }

    public void firePlayerDeath(String playerName, String deathMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("deathMessage", deathMessage);
        fireForgeEvent(ForgeEventType.PLAYER_DEATH, data);
    }

    public void firePlayerRespawn(String playerName, String dimension) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("dimension", dimension);
        fireForgeEvent(ForgeEventType.PLAYER_RESPAWN, data);
    }

    public void firePlayerChat(String playerName, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("message", message);
        fireForgeEvent(ForgeEventType.PLAYER_CHAT, data);
    }

    public void firePlayerCommand(String playerName, String command) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("command", command);
        fireForgeEvent(ForgeEventType.PLAYER_COMMAND, data);
    }

    public void firePlayerAdvancement(String playerName, String advancementId) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("advancementId", advancementId);
        fireForgeEvent(ForgeEventType.PLAYER_ADVANCEMENT, data);
    }

    public void fireWorldLoad(String worldName, long seed) {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        data.put("seed", seed);
        fireForgeEvent(ForgeEventType.WORLD_LOAD, data);
    }

    public void fireWorldUnload(String worldName) {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        fireForgeEvent(ForgeEventType.WORLD_UNLOAD, data);
    }

    public void fireWorldSave(String worldName) {
        Map<String, Object> data = new HashMap<>();
        data.put("worldName", worldName);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.WORLD_SAVE, data);
    }

    public void fireNetworkConnect(String playerName, String address) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        data.put("address", address);
        fireForgeEvent(ForgeEventType.NETWORK_CONNECT, data);
    }

    public void fireNetworkDisconnect(String playerName) {
        Map<String, Object> data = new HashMap<>();
        data.put("playerName", playerName);
        fireForgeEvent(ForgeEventType.NETWORK_DISCONNECT, data);
    }

    public void fireGuiOpen(String guiClassName) {
        Map<String, Object> data = new HashMap<>();
        data.put("guiClassName", guiClassName);
        fireForgeEvent(ForgeEventType.GUI_OPEN, data);
    }

    public void fireGuiClose(String guiClassName) {
        Map<String, Object> data = new HashMap<>();
        data.put("guiClassName", guiClassName);
        fireForgeEvent(ForgeEventType.GUI_CLOSE, data);
    }

    public void fireContainerOpen(String containerTitle) {
        Map<String, Object> data = new HashMap<>();
        data.put("containerTitle", containerTitle);
        fireForgeEvent(ForgeEventType.CONTAINER_OPEN, data);
    }

    public void fireContainerClose(String containerTitle) {
        Map<String, Object> data = new HashMap<>();
        data.put("containerTitle", containerTitle);
        fireForgeEvent(ForgeEventType.CONTAINER_CLOSE, data);
    }

    public void fireTick(long tickTime) {
        Map<String, Object> data = new HashMap<>();
        data.put("tickTime", tickTime);
        fireForgeEvent(ForgeEventType.TICK, data);
    }

    public void fireClick(int mouseX, int mouseY, int button) {
        Map<String, Object> data = new HashMap<>();
        data.put("mouseX", mouseX);
        data.put("mouseY", mouseY);
        data.put("button", button);
        fireForgeEvent(ForgeEventType.CLICK, data);
    }

    public void fireKeyPress(int keyCode, boolean pressed) {
        Map<String, Object> data = new HashMap<>();
        data.put("keyCode", keyCode);
        data.put("pressed", pressed);
        fireForgeEvent(ForgeEventType.KEY_PRESS, data);
    }

    public void fireServerTick(long tickTime) {
        Map<String, Object> data = new HashMap<>();
        data.put("tickTime", tickTime);
        fireForgeEvent(ForgeEventType.SERVER_TICK, data);
    }

    public void fireCapabilityAttached(String capabilityId, String targetType) {
        Map<String, Object> data = new HashMap<>();
        data.put("capabilityId", capabilityId);
        data.put("targetType", targetType);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.CAPABILITY_ATTACHED, data);
    }

    public void fireCapabilityDetached(String capabilityId, String targetType) {
        Map<String, Object> data = new HashMap<>();
        data.put("capabilityId", capabilityId);
        data.put("targetType", targetType);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.CAPABILITY_DETACHED, data);
    }

    public void fireCapabilitySynced(int attachmentCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("attachmentCount", attachmentCount);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.CAPABILITY_SYNCED, data);
    }

    public void fireConfigSaved(String modId, String fileName) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", modId);
        data.put("fileName", fileName);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.CONFIG_SAVED, data);
    }

    public void fireConfigReloaded(String modId, String fileName) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", modId);
        data.put("fileName", fileName);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.CONFIG_RELOADED, data);
    }

    public void fireNetworkChannelCreated(String channelName, String direction) {
        Map<String, Object> data = new HashMap<>();
        data.put("channelName", channelName);
        data.put("direction", direction);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.NETWORK_CHANNEL_CREATED, data);
    }

    public void fireNetworkPayloadSent(String channelName, String payloadId) {
        Map<String, Object> data = new HashMap<>();
        data.put("channelName", channelName);
        data.put("payloadId", payloadId);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.NETWORK_PAYLOAD_SENT, data);
    }

    public void fireNetworkPayloadReceived(String channelName, String payloadId) {
        Map<String, Object> data = new HashMap<>();
        data.put("channelName", channelName);
        data.put("payloadId", payloadId);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.NETWORK_PAYLOAD_RECEIVED, data);
    }

    public void fireBiomeModifierApplied(String ruleId, String biomeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleId", ruleId);
        data.put("biomeId", biomeId);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.BIOME_MODIFIER_APPLIED, data);
    }

    public void fireBiomeModifierRemoved(String ruleId, String biomeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleId", ruleId);
        data.put("biomeId", biomeId);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.BIOME_MODIFIER_REMOVED, data);
    }

    public void fireBiomeRegistered(String biomeId, String registryName) {
        Map<String, Object> data = new HashMap<>();
        data.put("biomeId", biomeId);
        data.put("registryName", registryName);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.BIOME_REGISTERED, data);
    }

    public void fireLaunchPhaseChange(String modId, String oldPhase, String newPhase) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", modId);
        data.put("oldPhase", oldPhase);
        data.put("newPhase", newPhase);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.LAUNCH_PHASE_CHANGE, data);
    }

    public void fireForgeShimInit(Map<String, Object> stats) {
        Map<String, Object> data = new HashMap<>(stats);
        data.put("timestamp", System.currentTimeMillis());
        fireForgeEvent(ForgeEventType.FORGE_SHIM_INIT, data);
    }

    private Map<String, Object> createPreLaunchData(String phase) {
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    private Map<String, Object> createPostLaunchData(String phase) {
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    public int getListenerCount(ForgeEventType type) {
        String eventId = type.getEventId();
        List<CrossContainerBus.EventListener> listeners = forgeEventListeners.get(eventId);
        return listeners != null ? listeners.size() : 0;
    }

    public void clear() {
        forgeEventListeners.clear();
        directListeners.clear();
    }

    public static class ForgeEvent implements CrossContainerBus.Event {
        private final String type;
        private final Map<String, Object> data;

        public ForgeEvent(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data != null ? data : Collections.emptyMap();
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Map<String, Object> getData() {
            return data;
        }

        public Object getDataValue(String key) {
            return data.get(key);
        }
    }
}

package com.panloader.core;

public enum ForgeEventType {
    FML_PRE_INIT("fml.pre_init"),
    FML_COMMON_SETUP("fml.common_setup"),
    FML_CLIENT_SETUP("fml.client_setup"),
    FML_LOAD_COMPLETE("fml.load_complete"),
    FML_SERVER_START("fml.server_start"),

    MOD_LOADING("mod.loading"),
    MOD_LOADED("mod.loaded"),
    MOD_CONFIG_LOADING("mod.config_loading"),
    MOD_CONFIG_LOADED("mod.config_loaded"),
    MOD_LIFECYCLE("mod.lifecycle"),

    REGISTER_BLOCKS("registry.blocks"),
    REGISTER_ITEMS("registry.items"),
    REGISTER_ENTITIES("registry.entities"),
    REGISTER_BIOMES("registry.biomes"),
    REGISTER_FLUIDS("registry.fluids"),
    REGISTER_SOUNDS("registry.sounds"),
    REGISTER_STRUCTURES("registry.structures"),
    REGISTER_FEATURES("registry.features"),
    REGISTER_CAPABILITIES("registry.capabilities"),
    REGISTER_CONFIGS("registry.configs"),
    REGISTER_NETWORK("registry.network"),

    CAPABILITY_ATTACHED("capability.attached"),
    CAPABILITY_DETACHED("capability.detached"),
    CAPABILITY_SYNCED("capability.synced"),

    CONFIG_SAVED("config.saved"),
    CONFIG_RELOADED("config.reloaded"),

    NETWORK_CHANNEL_CREATED("network.channel.created"),
    NETWORK_PAYLOAD_SENT("network.payload.sent"),
    NETWORK_PAYLOAD_RECEIVED("network.payload.received"),

    BIOME_MODIFIER_APPLIED("biome.modifier.applied"),
    BIOME_MODIFIER_REMOVED("biome.modifier.removed"),
    BIOME_REGISTERED("biome.registered"),

    PLAYER_LOGGED_IN("player.logged_in"),
    PLAYER_LOGGED_OUT("player.logged_out"),
    PLAYER_DEATH("player.death"),
    PLAYER_RESPAWN("player.respawn"),
    PLAYER_CHAT("player.chat"),
    PLAYER_COMMAND("player.command"),
    PLAYER_ADVANCEMENT("player.advancement"),

    WORLD_LOAD("world.load"),
    WORLD_UNLOAD("world.unload"),
    WORLD_SAVE("world.save"),
    WORLD_TICK("world.tick"),

    SERVER_STARTED("server.started"),
    SERVER_STOPPED("server.stopped"),
    SERVER_TICK("server.tick"),

    NETWORK_CONNECT("network.connect"),
    NETWORK_DISCONNECT("network.disconnect"),
    NETWORK_PACKET("network.packet"),

    GUI_OPEN("gui.open"),
    GUI_CLOSE("gui.close"),
    INVENTORY_OPEN("inventory.open"),
    INVENTORY_CLOSE("inventory.close"),
    CONTAINER_OPEN("container.open"),
    CONTAINER_CLOSE("container.close"),

    FORGE_SHIM_INIT("forge.shim.init"),
    LAUNCH_PHASE_CHANGE("launch.phase.change"),

    TICK("tick"),
    CLICK("click"),
    KEY_PRESS("key.press");

    private final String eventId;

    ForgeEventType(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public boolean isPreLaunch() {
        return switch (this) {
            case FML_PRE_INIT, FML_COMMON_SETUP, FML_CLIENT_SETUP, FML_SERVER_START,
                    MOD_LOADING, MOD_LOADED, MOD_CONFIG_LOADING, MOD_CONFIG_LOADED,
                    MOD_LIFECYCLE, REGISTER_BLOCKS, REGISTER_ITEMS, REGISTER_ENTITIES,
                    REGISTER_BIOMES, REGISTER_FLUIDS, REGISTER_SOUNDS, REGISTER_STRUCTURES,
                    REGISTER_FEATURES, REGISTER_CAPABILITIES, REGISTER_CONFIGS, REGISTER_NETWORK,
                    FORGE_SHIM_INIT, LAUNCH_PHASE_CHANGE -> true;
            default -> false;
        };
    }

    public boolean isGameEvent() {
        return switch (this) {
            case WORLD_LOAD, WORLD_UNLOAD, WORLD_SAVE, WORLD_TICK,
                    PLAYER_LOGGED_IN, PLAYER_LOGGED_OUT, PLAYER_DEATH, PLAYER_RESPAWN,
                    PLAYER_CHAT, PLAYER_COMMAND, PLAYER_ADVANCEMENT,
                    NETWORK_CONNECT, NETWORK_DISCONNECT, NETWORK_PACKET,
                    NETWORK_PAYLOAD_SENT, NETWORK_PAYLOAD_RECEIVED,
                    GUI_OPEN, GUI_CLOSE, INVENTORY_OPEN, INVENTORY_CLOSE,
                    CONTAINER_OPEN, CONTAINER_CLOSE,
                    CAPABILITY_ATTACHED, CAPABILITY_DETACHED, CAPABILITY_SYNCED,
                    CONFIG_SAVED, CONFIG_RELOADED,
                    BIOME_MODIFIER_APPLIED, BIOME_MODIFIER_REMOVED, BIOME_REGISTERED,
                    SERVER_TICK, TICK, CLICK, KEY_PRESS -> true;
            default -> false;
        };
    }
}

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

    REGISTER_BLOCKS("registry.blocks"),
    REGISTER_ITEMS("registry.items"),
    REGISTER_ENTITIES("registry.entities"),
    REGISTER_BIOMES("registry.biomes"),
    REGISTER_FLUIDS("registry.fluids"),
    REGISTER_SOUNDS("registry.sounds"),
    REGISTER_STRUCTURES("registry.structures"),
    REGISTER_FEATURES("registry.features"),

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
                    REGISTER_BLOCKS, REGISTER_ITEMS, REGISTER_ENTITIES, REGISTER_BIOMES,
                    REGISTER_FLUIDS, REGISTER_SOUNDS, REGISTER_STRUCTURES, REGISTER_FEATURES -> true;
            default -> false;
        };
    }

    public boolean isGameEvent() {
        return switch (this) {
            case WORLD_LOAD, WORLD_UNLOAD, WORLD_SAVE, WORLD_TICK,
                    PLAYER_LOGGED_IN, PLAYER_LOGGED_OUT, PLAYER_DEATH, PLAYER_RESPAWN,
                    PLAYER_CHAT, PLAYER_COMMAND, PLAYER_ADVANCEMENT,
                    NETWORK_CONNECT, NETWORK_DISCONNECT, NETWORK_PACKET,
                    GUI_OPEN, GUI_CLOSE, INVENTORY_OPEN, INVENTORY_CLOSE,
                    CONTAINER_OPEN, CONTAINER_CLOSE,
                    SERVER_TICK, TICK, CLICK, KEY_PRESS -> true;
            default -> false;
        };
    }
}

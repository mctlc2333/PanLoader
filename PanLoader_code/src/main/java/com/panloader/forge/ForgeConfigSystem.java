package com.panloader.forge;

import com.panloader.core.CrossContainerBus;
import com.panloader.core.ForgeEventBridge;
import com.panloader.core.ForgeEventType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ForgeConfigSystem {

    public enum ConfigType {
        CLIENT,
        SERVER,
        COMMON
    }

    public interface ConfigValue<T> {
        String getName();
        T get();
        void set(T value);
        T getDefault();
        List<T> getAcceptedValues();
        String getComment();
        boolean isChanged();
        void reset();
    }

    public static class ForgeConfigValue<T> implements ConfigValue<T> {
        private final String name;
        private T value;
        private final T defaultValue;
        private final List<T> acceptedValues;
        private final String comment;
        private boolean changed = false;

        public ForgeConfigValue(String name, T defaultValue, String comment) {
            this(name, defaultValue, Collections.emptyList(), comment);
        }

        public ForgeConfigValue(String name, T defaultValue, List<T> acceptedValues, String comment) {
            this.name = name;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.acceptedValues = acceptedValues != null ? acceptedValues : Collections.emptyList();
            this.comment = comment != null ? comment : "";
        }

        @Override
        public String getName() { return name; }

        @Override
        public T get() {
            if (!acceptedValues.isEmpty() && !acceptedValues.contains(value)) {
                return defaultValue;
            }
            return value;
        }

        @Override
        public void set(T newValue) {
            if (!acceptedValues.isEmpty() && !acceptedValues.contains(newValue)) {
                System.err.println("[ForgeConfig] Rejected invalid value for " + name
                        + ": " + newValue + " (accepted: " + acceptedValues + ")");
                return;
            }
            this.value = newValue;
            this.changed = true;
        }

        @Override
        public T getDefault() { return defaultValue; }

        @Override
        public List<T> getAcceptedValues() { return acceptedValues; }

        @Override
        public String getComment() { return comment; }

        @Override
        public boolean isChanged() { return changed; }

        @Override
        public void reset() {
            this.value = defaultValue;
            this.changed = false;
        }

        @Override
        public String toString() {
            return name + " = " + value + (changed ? " (changed)" : "");
        }
    }

    public static class ForgeConfigCategory {
        private final String name;
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
        private final List<String> comments = new ArrayList<>();
        private String headerComment = "";

        public ForgeConfigCategory(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        public <T> ConfigValue<T> define(String key, T defaultValue, String comment) {
            return define(key, defaultValue, Collections.emptyList(), comment);
        }

        @SuppressWarnings("unchecked")
        public <T> ConfigValue<T> define(String key, T defaultValue, List<T> acceptedValues, String comment) {
            ForgeConfigValue<T> value = new ForgeConfigValue<>(key, defaultValue, acceptedValues, comment);
            values.put(key, value);
            return value;
        }

        @SuppressWarnings("unchecked")
        public <T> ConfigValue<T> get(String key) {
            ConfigValue<?> value = values.get(key);
            if (value == null) {
                return null;
            }
            return (ConfigValue<T>) value;
        }

        public Map<String, ConfigValue<?>> getValues() {
            return Collections.unmodifiableMap(values);
        }

        public void addComment(String comment) {
            comments.add(comment);
        }

        public List<String> getComments() {
            return Collections.unmodifiableList(comments);
        }

        public void setHeaderComment(String comment) {
            this.headerComment = comment;
        }

        public String getHeaderComment() {
            return headerComment;
        }
    }

    public static class ForgeConfig {
        private final String modId;
        private final String fileName;
        private final ConfigType type;
        private final Path configPath;
        private final Map<String, ForgeConfigCategory> categories = new LinkedHashMap<>();
        private boolean loaded = false;
        private boolean changed = false;
        private long lastModified = 0;

        public ForgeConfig(String modId, String fileName, ConfigType type, Path configDir) {
            this.modId = modId;
            this.fileName = fileName;
            this.type = type;
            this.configPath = configDir.resolve(modId + "/" + fileName + ".toml");
        }

        public String getModId() { return modId; }
        public String getFileName() { return fileName; }
        public ConfigType getType() { return type; }
        public Path getConfigPath() { return configPath; }
        public boolean isLoaded() { return loaded; }
        public boolean isChanged() { return changed; }

        public ForgeConfigCategory defineCategory(String name) {
            return categories.computeIfAbsent(name, k -> new ForgeConfigCategory(name));
        }

        public ForgeConfigCategory getCategory(String name) {
            return categories.get(name);
        }

        public Map<String, ForgeConfigCategory> getCategories() {
            return Collections.unmodifiableMap(categories);
        }

        public void load() {
            if (!Files.exists(configPath)) {
                save();
                return;
            }

            try {
                loadFromFile();
                loaded = true;
                lastModified = Files.getLastModifiedTime(configPath).toMillis();
                System.out.println("[ForgeConfig] Loaded config: " + configPath);
            } catch (Exception e) {
                System.err.println("[ForgeConfig] Error loading config " + configPath + ": " + e.getMessage());
                loaded = true;
            }
        }

        public void save() {
            try {
                saveToFile();
                changed = false;
                lastModified = System.currentTimeMillis();
                System.out.println("[ForgeConfig] Saved config: " + configPath);
            } catch (Exception e) {
                System.err.println("[ForgeConfig] Error saving config " + configPath + ": " + e.getMessage());
            }
        }

        private void loadFromFile() throws IOException {
            Map<String, Map<String, String>> parsedValues = parseTomlFile();

            for (Map.Entry<String, Map<String, String>> categoryEntry : parsedValues.entrySet()) {
                String categoryName = categoryEntry.getKey();
                Map<String, String> values = categoryEntry.getValue();

                ForgeConfigCategory category = categories.computeIfAbsent(categoryName, k ->
                        new ForgeConfigCategory(categoryName));

                for (Map.Entry<String, String> valueEntry : values.entrySet()) {
                    String key = valueEntry.getKey();
                    String strValue = valueEntry.getValue();

                    ConfigValue<?> configValue = category.get(key);
                    if (configValue != null) {
                        Object parsed = parseValue(strValue, configValue.getDefault());
                        if (parsed != null) {
                            @SuppressWarnings("unchecked")
                            ConfigValue<Object> castValue = (ConfigValue<Object>) configValue;
                            castValue.set(parsed);
                        }
                    }
                }
            }
        }

        private Map<String, Map<String, String>> parseTomlFile() throws IOException {
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            String currentCategory = "default";
            Map<String, String> currentValues = new LinkedHashMap<>();

            List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (!currentValues.isEmpty()) {
                        result.put(currentCategory, currentValues);
                        currentValues = new LinkedHashMap<>();
                    }
                    currentCategory = trimmed.substring(1, trimmed.length() - 1).trim();
                    continue;
                }

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    String value = trimmed.substring(eqIdx + 1).trim();
                    currentValues.put(key, value);
                }
            }

            if (!currentValues.isEmpty()) {
                result.put(currentCategory, currentValues);
            }

            return result;
        }

        private Object parseValue(String strValue, Object defaultValue) {
            try {
                if (defaultValue instanceof Boolean) {
                    return Boolean.parseBoolean(strValue);
                } else if (defaultValue instanceof Integer) {
                    return Integer.parseInt(strValue);
                } else if (defaultValue instanceof Long) {
                    return Long.parseLong(strValue);
                } else if (defaultValue instanceof Float) {
                    return Float.parseFloat(strValue);
                } else if (defaultValue instanceof Double) {
                    return Double.parseDouble(strValue);
                } else if (defaultValue instanceof String) {
                    if (strValue.startsWith("\"") && strValue.endsWith("\"")) {
                        return strValue.substring(1, strValue.length() - 1);
                    }
                    return strValue;
                }
            } catch (NumberFormatException e) {
                System.out.println("[ForgeConfig] Could not parse value: " + strValue);
            }
            return null;
        }

        private void saveToFile() throws IOException {
            Files.createDirectories(configPath.getParent());

            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, ForgeConfigCategory> categoryEntry : categories.entrySet()) {
                ForgeConfigCategory category = categoryEntry.getValue();

                if (category.getHeaderComment() != null && !category.getHeaderComment().isEmpty()) {
                    sb.append("# ").append(category.getHeaderComment()).append("\n");
                }

                sb.append("[").append(category.getName()).append("]\n");

                for (String comment : category.getComments()) {
                    sb.append("# ").append(comment).append("\n");
                }

                for (Map.Entry<String, ConfigValue<?>> valueEntry : category.getValues().entrySet()) {
                    String key = valueEntry.getKey();
                    ConfigValue<?> value = valueEntry.getValue();

                    if (value.getComment() != null && !value.getComment().isEmpty()) {
                        sb.append("# ").append(value.getComment()).append("\n");
                    }

                    sb.append(key).append(" = ").append(formatValue(value.get())).append("\n");
                }

                sb.append("\n");
            }

            Files.writeString(configPath, sb.toString(), StandardCharsets.UTF_8);
        }

        private String formatValue(Object value) {
            if (value instanceof String) {
                return "\"" + value + "\"";
            }
            return String.valueOf(value);
        }

        public void markChanged() {
            this.changed = true;
        }
    }

    public interface ConfigLoadListener {
        void onConfigLoaded(ForgeConfig config);
        void onConfigSaved(ForgeConfig config);
        void onConfigError(ForgeConfig config, Exception error);
    }

    private static final ForgeConfigSystem INSTANCE = new ForgeConfigSystem();

    private final Map<String, Map<String, ForgeConfig>> modConfigs = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigLoadListener>> listeners = new ConcurrentHashMap<>();
    private final Path configDir;
    private final CrossContainerBus bus;

    private ForgeConfigSystem() {
        this.configDir = Paths.get("config");
        this.bus = CrossContainerBus.getInstance();
    }

    public static ForgeConfigSystem getInstance() {
        return INSTANCE;
    }

    public ForgeConfig registerConfig(String modId, String fileName, ConfigType type) {
        return registerConfig(modId, fileName, type, null);
    }

    public ForgeConfig registerConfig(String modId, String fileName, ConfigType type,
                                       ConfigLoadListener listener) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.computeIfAbsent(modId, k -> new ConcurrentHashMap<>());

        String configKey = fileName + "_" + type.name();
        if (modConfigMap.containsKey(configKey)) {
            return modConfigMap.get(configKey);
        }

        ForgeConfig config = new ForgeConfig(modId, fileName, type, configDir);
        modConfigMap.put(configKey, config);

        if (listener != null) {
            listeners.computeIfAbsent(modId, k -> new ArrayList<>()).add(listener);
        }

        System.out.println("[ForgeConfig] Registered config: " + modId + "/" + fileName
                + " (type: " + type.name() + ")");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("modId", modId);
        eventData.put("fileName", fileName);
        eventData.put("type", type.name());
        bus.postEvent(new CrossContainerBus.RegistryEvent("config.register", modId + ":" + fileName, eventData));

        return config;
    }

    public ForgeConfig getConfig(String modId, String fileName, ConfigType type) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return null;
        }
        return modConfigMap.get(fileName + "_" + type.name());
    }

    public List<ForgeConfig> getConfigsForMod(String modId) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(modConfigMap.values()));
    }

    public void loadConfigsForMod(String modId) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return;
        }

        System.out.println("[ForgeConfig] Loading configs for mod: " + modId);

        List<ConfigLoadListener> modListeners = listeners.get(modId);

        for (ForgeConfig config : modConfigMap.values()) {
            try {
                config.load();

                if (modListeners != null) {
                    for (ConfigLoadListener listener : modListeners) {
                        try {
                            listener.onConfigLoaded(config);
                        } catch (Exception e) {
                            System.err.println("[ForgeConfig] Error in config listener: " + e.getMessage());
                        }
                    }
                }

                ForgeEventBridge.getInstance().fireForgeEvent(
                        ForgeEventType.MOD_CONFIG_LOADED, createConfigEventData(config, "loaded"));

            } catch (Exception e) {
                System.err.println("[ForgeConfig] Error loading config "
                        + config.getFileName() + " for " + modId + ": " + e.getMessage());

                if (modListeners != null) {
                    for (ConfigLoadListener listener : modListeners) {
                        try {
                            listener.onConfigError(config, e);
                        } catch (Exception ex) {
                            System.err.println("[ForgeConfig] Error in config error listener: " + ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void saveConfigsForMod(String modId) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return;
        }

        List<ConfigLoadListener> modListeners = listeners.get(modId);

        for (ForgeConfig config : modConfigMap.values()) {
            if (config.isChanged()) {
                try {
                    config.save();

                    if (modListeners != null) {
                        for (ConfigLoadListener listener : modListeners) {
                            try {
                                listener.onConfigSaved(config);
                            } catch (Exception e) {
                                System.err.println("[ForgeConfig] Error in config save listener: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ForgeConfig] Error saving config "
                            + config.getFileName() + " for " + modId + ": " + e.getMessage());
                }
            }
        }
    }

    public void addLoadListener(String modId, ConfigLoadListener listener) {
        listeners.computeIfAbsent(modId, k -> new ArrayList<>()).add(listener);
    }

    public int getConfigCount() {
        int count = 0;
        for (Map<String, ForgeConfig> modConfigMap : modConfigs.values()) {
            count += modConfigMap.size();
        }
        return count;
    }

    public Set<String> getRegisteredModIds() {
        return Collections.unmodifiableSet(modConfigs.keySet());
    }

    public Map<String, Object> exportConfigsForMod(String modId) {
        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (ForgeConfig config : modConfigMap.values()) {
            Map<String, Object> configData = new LinkedHashMap<>();
            configData.put("fileName", config.getFileName());
            configData.put("type", config.getType().name());

            Map<String, Map<String, Object>> categoryData = new LinkedHashMap<>();
            for (Map.Entry<String, ForgeConfigCategory> categoryEntry : config.getCategories().entrySet()) {
                Map<String, Object> valuesData = new LinkedHashMap<>();
                for (Map.Entry<String, ConfigValue<?>> valueEntry : categoryEntry.getValue().getValues().entrySet()) {
                    valuesData.put(valueEntry.getKey(), String.valueOf(valueEntry.getValue().get()));
                }
                categoryData.put(categoryEntry.getKey(), valuesData);
            }
            configData.put("categories", categoryData);
            result.put(config.getFileName(), configData);
        }

        return result;
    }

    public void importConfigsForMod(String modId, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        Map<String, ForgeConfig> modConfigMap = modConfigs.get(modId);
        if (modConfigMap == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fileName = entry.getKey();
            ForgeConfig config = modConfigMap.get(fileName + "_CLIENT");
            if (config == null) {
                config = modConfigMap.get(fileName + "_SERVER");
            }
            if (config == null) {
                config = modConfigMap.get(fileName + "_COMMON");
            }

            if (config != null) {
                try {
                    Map<String, Object> configData = (Map<String, Object>) entry.getValue();
                    Map<String, Object> categories = (Map<String, Object>) configData.get("categories");

                    if (categories != null) {
                        for (Map.Entry<String, Object> categoryEntry : categories.entrySet()) {
                            String categoryName = categoryEntry.getKey();
                            ForgeConfigCategory category = config.getCategory(categoryName);
                            if (category != null) {
                                Map<String, Object> values = (Map<String, Object>) categoryEntry.getValue();
                                for (Map.Entry<String, Object> valueEntry : values.entrySet()) {
                                    ConfigValue<?> configValue = category.get(valueEntry.getKey());
                                    if (configValue != null) {
                                        Object parsed = parseValueFromString(
                                                String.valueOf(valueEntry.getValue()),
                                                configValue.getDefault());
                                        if (parsed != null) {
                                            @SuppressWarnings("unchecked")
                                            ConfigValue<Object> castValue = (ConfigValue<Object>) configValue;
                                            castValue.set(parsed);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    config.markChanged();
                    System.out.println("[ForgeConfig] Imported config for " + modId + "/" + fileName);
                } catch (Exception e) {
                    System.err.println("[ForgeConfig] Error importing config: " + e.getMessage());
                }
            }
        }
    }

    private Object parseValueFromString(String strValue, Object defaultValue) {
        try {
            if (defaultValue instanceof Boolean) {
                return Boolean.parseBoolean(strValue);
            } else if (defaultValue instanceof Integer) {
                return Integer.parseInt(strValue);
            } else if (defaultValue instanceof Long) {
                return Long.parseLong(strValue);
            } else if (defaultValue instanceof Float) {
                return Float.parseFloat(strValue);
            } else if (defaultValue instanceof Double) {
                return Double.parseDouble(strValue);
            } else if (defaultValue instanceof String) {
                return strValue;
            }
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private Map<String, Object> createConfigEventData(ForgeConfig config, String action) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", config.getModId());
        data.put("fileName", config.getFileName());
        data.put("type", config.getType().name());
        data.put("action", action);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    public void syncAllConfigs() {
        System.out.println("[ForgeConfig] Syncing all configs across containers...");

        for (String modId : modConfigs.keySet()) {
            Map<String, Object> configData = exportConfigsForMod(modId);
            if (!configData.isEmpty()) {
                bus.postEvent(new CrossContainerBus.RegistryEvent(
                        "config.sync", modId, configData));
            }
        }

        System.out.println("[ForgeConfig] Config sync complete");
    }

    public void clear() {
        modConfigs.clear();
        listeners.clear();
    }
}
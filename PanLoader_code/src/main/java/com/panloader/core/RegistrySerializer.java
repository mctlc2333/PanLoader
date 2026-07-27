package com.panloader.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegistrySerializer {

    public static final int SERIALIZATION_VERSION = 2;
    public static final String SCHEMA_VERSION_KEY = "__schema_version";
    public static final String CLASS_NAME_KEY = "className";
    public static final String TYPE_KEY = "__type";

    public static class SerializedEntry {
        private final String id;
        private final String registryName;
        private final String ownerModId;
        private final Map<String, Object> properties;
        private final long timestamp;
        private final int version;

        public SerializedEntry(String id, String registryName, String ownerModId,
                              Map<String, Object> properties, long timestamp, int version) {
            this.id = id;
            this.registryName = registryName;
            this.ownerModId = ownerModId;
            this.properties = Collections.unmodifiableMap(new ConcurrentHashMap<>(properties));
            this.timestamp = timestamp;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public String getRegistryName() {
            return registryName;
        }

        public String getOwnerModId() {
            return ownerModId;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getVersion() {
            return version;
        }

        public String getKey() {
            return registryName + ":" + id;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("registryName", registryName);
            map.put("ownerModId", ownerModId);
            map.put("properties", new HashMap<>(properties));
            map.put("timestamp", timestamp);
            map.put("version", version);
            return map;
        }

        public static SerializedEntry fromMap(Map<String, Object> map) {
            String id = (String) map.get("id");
            String registryName = (String) map.get("registryName");
            String ownerModId = (String) map.get("ownerModId");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) map.getOrDefault("properties", Collections.emptyMap());
            long timestamp = map.get("timestamp") instanceof Number
                    ? ((Number) map.get("timestamp")).longValue() : 0L;
            int version = map.get("version") instanceof Number
                    ? ((Number) map.get("version")).intValue() : 1;
            return new SerializedEntry(id, registryName, ownerModId, properties, timestamp, version);
        }
    }

    public static class SerializedRegistry {
        private final String registryName;
        private final List<SerializedEntry> entries;
        private final long syncTimestamp;
        private final String syncSource;
        private final int totalEntries;
        private final int version;

        public SerializedRegistry(String registryName, List<SerializedEntry> entries,
                                  long syncTimestamp, String syncSource, int version) {
            this.registryName = registryName;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.syncTimestamp = syncTimestamp;
            this.syncSource = syncSource;
            this.totalEntries = entries.size();
            this.version = version;
        }

        public String getRegistryName() {
            return registryName;
        }

        public List<SerializedEntry> getEntries() {
            return entries;
        }

        public long getSyncTimestamp() {
            return syncTimestamp;
        }

        public String getSyncSource() {
            return syncSource;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getVersion() {
            return version;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(SCHEMA_VERSION_KEY, SERIALIZATION_VERSION);
            map.put("registryName", registryName);
            List<Map<String, Object>> entryMaps = new ArrayList<>();
            for (SerializedEntry entry : entries) {
                entryMaps.add(entry.toMap());
            }
            map.put("entries", entryMaps);
            map.put("syncTimestamp", syncTimestamp);
            map.put("syncSource", syncSource);
            map.put("totalEntries", totalEntries);
            map.put("version", version);
            return map;
        }

        public static SerializedRegistry fromMap(Map<String, Object> map) {
            int schemaVersion = map.get(SCHEMA_VERSION_KEY) instanceof Number
                    ? ((Number) map.get(SCHEMA_VERSION_KEY)).intValue() : 1;

            if (schemaVersion > SERIALIZATION_VERSION) {
                System.out.println("[RegistrySerializer] Warning: Serialization version " + schemaVersion
                        + " is newer than current version " + SERIALIZATION_VERSION
                        + ". Some fields may not be properly deserialized.");
            }

            String registryName = (String) map.get("registryName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entryMaps = (List<Map<String, Object>>) map.getOrDefault("entries", Collections.emptyList());
            List<SerializedEntry> entries = new ArrayList<>();
            for (Map<String, Object> entryMap : entryMaps) {
                entries.add(SerializedEntry.fromMap(entryMap));
            }
            long syncTimestamp = map.get("syncTimestamp") instanceof Number
                    ? ((Number) map.get("syncTimestamp")).longValue() : 0L;
            String syncSource = (String) map.getOrDefault("syncSource", "unknown");
            int version = map.get("version") instanceof Number
                    ? ((Number) map.get("version")).intValue() : 1;
            return new SerializedRegistry(registryName, entries, syncTimestamp, syncSource, version);
        }
    }

    public static Map<String, Object> serializeEntry(String id, String registryName,
                                                     String ownerModId, Object object) {
        Map<String, Object> properties = extractPropertiesStatic(object);
        return new SerializedEntry(id, registryName, ownerModId, properties,
                System.currentTimeMillis(), 1).toMap();
    }

    public static SerializedRegistry serializeRegistry(String registryName,
                                                       Map<String, Object> entries,
                                                       String source) {
        List<SerializedEntry> serializedEntries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            String id = entry.getKey();
            Object object = entry.getValue();
            Map<String, Object> properties = extractPropertiesStatic(object);
            serializedEntries.add(new SerializedEntry(id, registryName, "", properties,
                    System.currentTimeMillis(), 1));
        }
        return new SerializedRegistry(registryName, serializedEntries,
                System.currentTimeMillis(), source, 1);
    }

    private static final int MAX_EXTRACTION_DEPTH = 3;
    private static final int MAX_MAP_SIZE = 100;
    private static final int MAX_COLLECTION_SIZE = 50;

    public static Map<String, Object> extractPropertiesStatic(Object object) {
        return extractProperties(object, 0, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractProperties(Object object, int depth, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (object == null) {
            props.put("null", true);
            return props;
        }

        if (visited.containsKey(object)) {
            props.put("circular_reference", true);
            props.put("className", object.getClass().getName());
            return props;
        }

        if (depth >= MAX_EXTRACTION_DEPTH) {
            props.put("max_depth_reached", true);
            props.put("className", object.getClass().getName());
            props.put("toString", safeToString(object));
            return props;
        }

        visited.put(object, Boolean.TRUE);
        props.put(CLASS_NAME_KEY, object.getClass().getName());

        try {
            if (object instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) object;
                int count = 0;
                int totalSize = map.size();
                if (totalSize > MAX_MAP_SIZE) {
                    props.put("map_size", totalSize);
                    props.put("map_truncated", true);
                }
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    if (count >= MAX_MAP_SIZE) {
                        break;
                    }
                    Object value = e.getValue();
                    if (isSimpleType(value)) {
                        props.put("map:" + e.getKey(), String.valueOf(value));
                    } else {
                        Map<String, Object> subProps = extractProperties(value, depth + 1, visited);
                        props.put("map:" + e.getKey(), subProps);
                    }
                    count++;
                }
            } else if (object instanceof Collection) {
                Collection<?> coll = (Collection<?>) object;
                props.put("size", coll.size());
                props.put("collection_type", coll.getClass().getSimpleName());
                int count = 0;
                for (Object item : coll) {
                    if (count >= MAX_COLLECTION_SIZE) {
                        props.put("collection_truncated", true);
                        break;
                    }
                    if (isSimpleType(item)) {
                        props.put("item:" + count, String.valueOf(item));
                    } else {
                        Map<String, Object> subProps = extractProperties(item, depth + 1, visited);
                        props.put("item:" + count, subProps);
                    }
                    count++;
                }
            } else if (object instanceof CharSequence || object instanceof Number || object instanceof Boolean) {
                props.put("value", object.toString());
            } else {
                props.put("toString", safeToString(object));
                try {
                    java.lang.reflect.Method getId = object.getClass().getMethod("getId");
                    props.put("id", String.valueOf(getId.invoke(object)));
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                    props.put("id_error", e.getMessage());
                }
                try {
                    java.lang.reflect.Method getName = object.getClass().getMethod("getName");
                    props.put("name", String.valueOf(getName.invoke(object)));
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                    props.put("name_error", e.getMessage());
                }
                try {
                    java.lang.reflect.Method getRegistryName = object.getClass().getMethod("getRegistryName");
                    props.put("registryName", String.valueOf(getRegistryName.invoke(object)));
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            props.put("extraction_error", e.getMessage());
            props.put("error_class", e.getClass().getName());
        }

        return props;
    }

    private static boolean isSimpleType(Object obj) {
        if (obj == null) return true;
        return obj instanceof CharSequence
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Character
                || obj.getClass().isPrimitive()
                || obj.getClass().isEnum();
    }

    private static String safeToString(Object obj) {
        try {
            String result = obj.toString();
            if (result.length() > 500) {
                return result.substring(0, 500) + "...(truncated)";
            }
            return result;
        } catch (Exception e) {
            return "<toString failed: " + e.getMessage() + ">";
        }
    }

    public static boolean validateEntryConsistency(SerializedEntry a, SerializedEntry b) {
        if (!a.getId().equals(b.getId())) return false;
        if (!a.getRegistryName().equals(b.getRegistryName())) return false;
        if (a.getVersion() != b.getVersion()) return false;
        return a.getProperties().equals(b.getProperties());
    }

    public static List<String> findInconsistencies(SerializedRegistry a, SerializedRegistry b) {
        List<String> issues = new ArrayList<>();
        if (!a.getRegistryName().equals(b.getRegistryName())) {
            issues.add("Registry name mismatch: " + a.getRegistryName() + " vs " + b.getRegistryName());
            return issues;
        }
        Map<String, SerializedEntry> bEntries = new HashMap<>();
        for (SerializedEntry entry : b.getEntries()) {
            bEntries.put(entry.getId(), entry);
        }
        for (SerializedEntry entryA : a.getEntries()) {
            SerializedEntry entryB = bEntries.get(entryA.getId());
            if (entryB == null) {
                issues.add("Entry '" + entryA.getKey() + "' missing in second registry");
            } else if (!validateEntryConsistency(entryA, entryB)) {
                issues.add("Entry '" + entryA.getKey() + "' inconsistent between registries");
            }
        }
        for (SerializedEntry entryB : b.getEntries()) {
            if (!a.getEntries().stream().anyMatch(e -> e.getId().equals(entryB.getId()))) {
                issues.add("Entry '" + entryB.getKey() + "' extra in second registry");
            }
        }
        return issues;
    }
}
package com.panloader.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class CrossContainerBus {

    public interface Event {
        String getType();

        default Map<String, Object> getData() {
            return Collections.emptyMap();
        }
    }

    public interface EventListener {
        void onEvent(Event event);
    }

    public interface RegistrySync {
        void onPreLaunch();

        void onPostLaunch();
    }

    public static class RegistryEvent implements Event {
        private final String action;
        private final String targetId;
        private final Object data;

        public RegistryEvent(String action, String targetId, Object data) {
            this.action = action;
            this.targetId = targetId;
            this.data = data;
        }

        @Override
        public String getType() {
            return "registry." + action;
        }

        public String getAction() {
            return action;
        }

        public String getTargetId() {
            return targetId;
        }

        @Override
        public Map<String, Object> getData() {
            Map<String, Object> map = new HashMap<>();
            map.put("action", action);
            map.put("targetId", targetId);
            map.put("data", data);
            return map;
        }
    }

    public interface RegistryProxy {
        void register(String registryName, String id, Object object);

        void register(String registryName, String id, Object object, String ownerModId);

        Object get(String registryName, String id);

        boolean contains(String registryName, String id);

        Set<String> getKeys(String registryName);

        void markRegistered(String registryName, String modId);

        List<String> getRegisteredMods(String registryName);

        Map<String, Object> getRegistrySnapshot(String registryName);

        void syncAllRegistries();

        void syncRegistry(String registryName);

        ConflictResolutionStrategy getConflictStrategy();

        void setConflictStrategy(ConflictResolutionStrategy strategy);

        RegistrySerializer.SerializedRegistry serializeRegistry(String registryName);

        void deserializeAndMerge(RegistrySerializer.SerializedRegistry serialized);

        List<String> validateConsistency(String registryName);
    }

    public static class RegistryEntry {
        private final String id;
        private final Object object;
        private final String registryName;
        private final String ownerModId;

        public RegistryEntry(String id, Object object, String registryName, String ownerModId) {
            this.id = id;
            this.object = object;
            this.registryName = registryName;
            this.ownerModId = ownerModId;
        }

        public String getId() {
            return id;
        }

        public Object getObject() {
            return object;
        }

        public String getRegistryName() {
            return registryName;
        }

        public String getOwnerModId() {
            return ownerModId;
        }
    }

    private static final CrossContainerBus INSTANCE = new CrossContainerBus();

    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final List<RegistrySync> registrySyncs = new CopyOnWriteArrayList<>();
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    private final List<ModContainer> containers = new CopyOnWriteArrayList<>();

    private final Map<String, Map<String, Object>> registries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, RegistryMetadata>> registryMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registryModOwners = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> registryLocks = new ConcurrentHashMap<>();
    private final List<RegistryEntry> allRegistryEntries = new CopyOnWriteArrayList<>();
    private final RegistryProxy registryProxy;
    private volatile ConflictResolutionStrategy conflictStrategy = ConflictResolutionStrategy.FAIL_ON_CONFLICT;

    private CrossContainerBus() {
        this.registryProxy = new RegistryProxyImpl();
    }

    private ReentrantLock getRegistryLock(String registryName) {
        return registryLocks.computeIfAbsent(registryName, k -> new ReentrantLock());
    }

    public static class RegistryMetadata {
        private final String ownerModId;
        private final long timestamp;
        private final int version;
        private final long syncTimestamp;

        public RegistryMetadata(String ownerModId, long timestamp, int version, long syncTimestamp) {
            this.ownerModId = ownerModId;
            this.timestamp = timestamp;
            this.version = version;
            this.syncTimestamp = syncTimestamp;
        }

        public String getOwnerModId() {
            return ownerModId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getVersion() {
            return version;
        }

        public long getSyncTimestamp() {
            return syncTimestamp;
        }

        public RegistryMetadata withSyncTimestamp(long newSyncTimestamp) {
            return new RegistryMetadata(ownerModId, timestamp, version, newSyncTimestamp);
        }

        public RegistryMetadata withVersion(int newVersion) {
            return new RegistryMetadata(ownerModId, timestamp, newVersion, syncTimestamp);
        }
    }

    private class RegistryProxyImpl implements RegistryProxy {

        @Override
        public void register(String registryName, String id, Object object) {
            register(registryName, id, object, "unknown");
        }

        @Override
        public void register(String registryName, String id, Object object, String ownerModId) {
            String fullKey = registryName + ":" + id;
            ReentrantLock lock = getRegistryLock(registryName);
            lock.lock();
            try {
                Map<String, Object> registry = registries.computeIfAbsent(registryName, k -> new ConcurrentHashMap<>());
                Map<String, RegistryMetadata> metadataMap = registryMetadata.computeIfAbsent(registryName, k -> new ConcurrentHashMap<>());

                if (registry.containsKey(id)) {
                    if (conflictStrategy == ConflictResolutionStrategy.FAIL_ON_CONFLICT) {
                        System.err.println("[CrossBus] CONFLICT: Duplicate registration for " + fullKey);
                        throw new IllegalStateException("Duplicate registration: " + fullKey);
                    }
                    resolveConflict(registryName, id, object, ownerModId, registry, metadataMap);
                    return;
                }

                long now = System.currentTimeMillis();
                registry.put(id, object);
                metadataMap.put(id, new RegistryMetadata(ownerModId, now, 1, now));

                RegistryEntry entry = new RegistryEntry(id, object, registryName, ownerModId);
                allRegistryEntries.add(entry);

                System.out.println("[CrossBus] Registered: " + fullKey + " -> " + object.getClass().getSimpleName() + " (by " + ownerModId + ")");

                postEvent(new RegistryEvent("register", fullKey, object));
            } finally {
                lock.unlock();
            }
        }

        private void resolveConflict(String registryName, String id, Object newObject, String newOwnerModId,
                                     Map<String, Object> registry, Map<String, RegistryMetadata> metadataMap) {
            String fullKey = registryName + ":" + id;
            RegistryMetadata existingMeta = metadataMap.get(id);
            long now = System.currentTimeMillis();

            switch (conflictStrategy) {
                case KEEP_FIRST:
                    System.out.println("[CrossBus] CONFLICT RESOLVED (KEEP_FIRST): Keeping existing " + fullKey);
                    postEvent(new RegistryEvent("conflict_keep_existing", fullKey, registry.get(id)));
                    break;

                case KEEP_LAST:
                    System.out.println("[CrossBus] CONFLICT RESOLVED (KEEP_LAST): Overwriting " + fullKey);
                    registry.put(id, newObject);
                    metadataMap.put(id, new RegistryMetadata(newOwnerModId, now,
                            existingMeta != null ? existingMeta.getVersion() + 1 : 2, now));
                    postEvent(new RegistryEvent("conflict_overwrite", fullKey, newObject));
                    break;

                case KEEP_BY_PRIORITY:
                    int existingPriority = getContainerPriority(existingMeta != null ? existingMeta.getOwnerModId() : "");
                    int newPriority = getContainerPriority(newOwnerModId);
                    if (newPriority <= existingPriority) {
                        System.out.println("[CrossBus] CONFLICT RESOLVED (KEEP_BY_PRIORITY): Keeping higher priority for " + fullKey);
                        postEvent(new RegistryEvent("conflict_keep_existing", fullKey, registry.get(id)));
                    } else {
                        System.out.println("[CrossBus] CONFLICT RESOLVED (KEEP_BY_PRIORITY): Lower priority overrides for " + fullKey);
                        registry.put(id, newObject);
                        metadataMap.put(id, new RegistryMetadata(newOwnerModId, now,
                                existingMeta != null ? existingMeta.getVersion() + 1 : 2, now));
                        postEvent(new RegistryEvent("conflict_overwrite", fullKey, newObject));
                    }
                    break;

                case MERGE:
                    System.out.println("[CrossBus] CONFLICT RESOLVED (MERGE): Merging " + fullKey);
                    Object merged = mergeObjects(registry.get(id), newObject);
                    registry.put(id, merged);
                    metadataMap.put(id, new RegistryMetadata(newOwnerModId, now,
                            existingMeta != null ? existingMeta.getVersion() + 1 : 2, now));
                    postEvent(new RegistryEvent("conflict_merged", fullKey, merged));
                    break;

                default:
                    System.err.println("[CrossBus] CONFLICT: Unresolved duplicate registration for " + fullKey);
                    throw new IllegalStateException("Duplicate registration (unresolved): " + fullKey);
            }
        }

        private int getContainerPriority(String modId) {
            for (ModContainer container : containers) {
                if (container.getContainerId().equals(modId)) {
                    return container.getMixinPriority().getValue();
                }
            }
            return Integer.MAX_VALUE;
        }

        @SuppressWarnings("unchecked")
        private Object mergeObjects(Object existing, Object newObj) {
            if (existing instanceof Map && newObj instanceof Map) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existing);
                merged.putAll((Map<String, Object>) newObj);
                return merged;
            }
            return newObj;
        }

        @Override
        public Object get(String registryName, String id) {
            Map<String, Object> registry = registries.get(registryName);
            if (registry == null) {
                return null;
            }
            return registry.get(id);
        }

        @Override
        public boolean contains(String registryName, String id) {
            Map<String, Object> registry = registries.get(registryName);
            return registry != null && registry.containsKey(id);
        }

        @Override
        public Set<String> getKeys(String registryName) {
            Map<String, Object> registry = registries.get(registryName);
            if (registry == null) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(registry.keySet());
        }

        @Override
        public void markRegistered(String registryName, String modId) {
            List<String> owners = registryModOwners.computeIfAbsent(registryName, k -> new CopyOnWriteArrayList<>());
            if (!owners.contains(modId)) {
                owners.add(modId);
            }
            System.out.println("[CrossBus] Registry " + registryName + " marked as registered by " + modId);
        }

        @Override
        public List<String> getRegisteredMods(String registryName) {
            List<String> owners = registryModOwners.get(registryName);
            return owners != null ? Collections.unmodifiableList(owners) : Collections.emptyList();
        }

        @Override
        public Map<String, Object> getRegistrySnapshot(String registryName) {
            Map<String, Object> registry = registries.get(registryName);
            if (registry == null) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new HashMap<>(registry));
        }

        @Override
        public void syncAllRegistries() {
            long syncStart = System.currentTimeMillis();
            System.out.println("[CrossBus] Syncing all registries...");
            int totalEntries = 0;

            for (String registryName : registries.keySet()) {
                ReentrantLock lock = getRegistryLock(registryName);
                lock.lock();
                try {
                    Map<String, Object> registry = registries.get(registryName);
                    if (registry == null) continue;

                    totalEntries += registry.size();
                    System.out.println("[CrossBus] Registry: " + registryName + " (" + registry.size() + " entries)");

                    for (Map.Entry<String, Object> item : registry.entrySet()) {
                        postEvent(new RegistryEvent("sync", registryName + ":" + item.getKey(), item.getValue()));
                    }

                    updateSyncTimestamp(registryName, syncStart);
                } finally {
                    lock.unlock();
                }
            }

            System.out.println("[CrossBus] Registry sync complete: " + totalEntries + " entries across "
                    + registries.size() + " registries in " + (System.currentTimeMillis() - syncStart) + "ms");
        }

        @Override
        public void syncRegistry(String registryName) {
            ReentrantLock lock = getRegistryLock(registryName);
            lock.lock();
            try {
                Map<String, Object> registry = registries.get(registryName);
                if (registry == null) {
                    System.out.println("[CrossBus] Registry " + registryName + " not found, skipping sync");
                    return;
                }

                long syncStart = System.currentTimeMillis();
                System.out.println("[CrossBus] Syncing registry: " + registryName + " (" + registry.size() + " entries)");

                for (Map.Entry<String, Object> item : registry.entrySet()) {
                    postEvent(new RegistryEvent("sync", registryName + ":" + item.getKey(), item.getValue()));
                }

                updateSyncTimestamp(registryName, syncStart);
                System.out.println("[CrossBus] Registry " + registryName + " sync complete in "
                        + (System.currentTimeMillis() - syncStart) + "ms");
            } finally {
                lock.unlock();
            }
        }

        private void updateSyncTimestamp(String registryName, long timestamp) {
            Map<String, RegistryMetadata> metadataMap = registryMetadata.get(registryName);
            if (metadataMap != null) {
                for (Map.Entry<String, RegistryMetadata> entry : metadataMap.entrySet()) {
                    entry.setValue(entry.getValue().withSyncTimestamp(timestamp));
                }
            }
        }

        @Override
        public ConflictResolutionStrategy getConflictStrategy() {
            return conflictStrategy;
        }

        @Override
        public void setConflictStrategy(ConflictResolutionStrategy strategy) {
            if (strategy != null) {
                conflictStrategy = strategy;
                System.out.println("[CrossBus] Conflict resolution strategy set to: " + strategy.getDisplayName());
            }
        }

        @Override
        public RegistrySerializer.SerializedRegistry serializeRegistry(String registryName) {
            Map<String, Object> registry = registries.get(registryName);
            if (registry == null) {
                return new RegistrySerializer.SerializedRegistry(registryName, Collections.emptyList(),
                        System.currentTimeMillis(), "crossbus", 1);
            }

            List<RegistrySerializer.SerializedEntry> entries = new ArrayList<>();
            Map<String, RegistryMetadata> metadataMap = registryMetadata.getOrDefault(registryName, Collections.emptyMap());

            for (Map.Entry<String, Object> item : registry.entrySet()) {
                String id = item.getKey();
                Object obj = item.getValue();
                RegistryMetadata meta = metadataMap.getOrDefault(id, new RegistryMetadata("unknown", 0, 1, 0));

                Map<String, Object> props = RegistrySerializer.extractPropertiesStatic(obj);
                entries.add(new RegistrySerializer.SerializedEntry(id, registryName, meta.getOwnerModId(),
                        props, meta.getTimestamp(), meta.getVersion()));
            }

            return new RegistrySerializer.SerializedRegistry(registryName, entries,
                    System.currentTimeMillis(), "crossbus", 1);
        }

        @Override
        public void deserializeAndMerge(RegistrySerializer.SerializedRegistry serialized) {
            String registryName = serialized.getRegistryName();
            ReentrantLock lock = getRegistryLock(registryName);
            lock.lock();
            try {
                Map<String, Object> registry = registries.computeIfAbsent(registryName, k -> new ConcurrentHashMap<>());
                Map<String, RegistryMetadata> metadataMap = registryMetadata.computeIfAbsent(registryName, k -> new ConcurrentHashMap<>());

                System.out.println("[CrossBus] Deserializing and merging registry: " + registryName
                        + " (" + serialized.getEntries().size() + " entries from " + serialized.getSyncSource() + ")");

                int added = 0, updated = 0, conflicts = 0;
                long now = System.currentTimeMillis();

                for (RegistrySerializer.SerializedEntry entry : serialized.getEntries()) {
                    String id = entry.getId();
                    if (registry.containsKey(id)) {
                        conflicts++;
                        if (conflictStrategy.canResolve()) {
                            Object existing = registry.get(id);
                            Object merged = mergeObjects(existing, entry.getProperties());
                            registry.put(id, merged);
                            metadataMap.put(id, new RegistryMetadata(entry.getOwnerModId(), now,
                                    metadataMap.getOrDefault(id, new RegistryMetadata("", 0, 1, 0)).getVersion() + 1, now));
                            updated++;
                            System.out.println("[CrossBus] Merged existing entry: " + registryName + ":" + id);
                        } else {
                            System.err.println("[CrossBus] CONFLICT on deserialization: " + registryName + ":" + id + " already exists");
                        }
                    } else {
                        registry.put(id, entry.getProperties());
                        metadataMap.put(id, new RegistryMetadata(entry.getOwnerModId(), now, entry.getVersion(), now));
                        added++;
                    }
                }

                System.out.println("[CrossBus] Deserialization complete: " + added + " added, " + updated + " updated, " + conflicts + " conflicts");
                postEvent(new RegistryEvent("deserialize_complete", registryName, serialized));
            } finally {
                lock.unlock();
            }
        }

        @Override
        public List<String> validateConsistency(String registryName) {
            List<String> issues = new ArrayList<>();
            Map<String, Object> registry = registries.get(registryName);
            Map<String, RegistryMetadata> metadataMap = registryMetadata.get(registryName);

            if (registry == null) {
                issues.add("Registry '" + registryName + "' does not exist");
                return issues;
            }

            if (metadataMap == null) {
                issues.add("Registry '" + registryName + "' has no metadata tracking");
                metadataMap = Collections.emptyMap();
            }

            for (Map.Entry<String, Object> entry : registry.entrySet()) {
                String id = entry.getKey();
                Object obj = entry.getValue();
                if (obj == null) {
                    issues.add("Entry '" + registryName + ":" + id + "' has null value");
                }
                RegistryMetadata meta = metadataMap.get(id);
                if (meta == null) {
                    issues.add("Entry '" + registryName + ":" + id + "' missing metadata");
                } else {
                    if (meta.getOwnerModId() == null || meta.getOwnerModId().isEmpty()) {
                        issues.add("Entry '" + registryName + ":" + id + "' has no owner mod ID");
                    }
                    if (meta.getSyncTimestamp() < meta.getTimestamp()) {
                        issues.add("Entry '" + registryName + ":" + id + "' has invalid sync timestamp");
                    }
                }
            }

            for (String metaId : metadataMap.keySet()) {
                if (!registry.containsKey(metaId)) {
                    issues.add("Metadata for '" + registryName + ":" + metaId + "' exists but entry is missing");
                }
            }

            if (issues.isEmpty()) {
                System.out.println("[CrossBus] Registry '" + registryName + "' consistency check PASSED (" + registry.size() + " entries)");
            } else {
                System.err.println("[CrossBus] Registry '" + registryName + "' consistency check FAILED: " + issues.size() + " issues");
                for (String issue : issues) {
                    System.err.println("[CrossBus]   - " + issue);
                }
            }

            return issues;
        }
    }

    public void setConflictStrategy(ConflictResolutionStrategy strategy) {
        if (strategy != null) {
            ConflictResolutionStrategy old = this.conflictStrategy;
            this.conflictStrategy = strategy;
            System.out.println("[CrossBus] Global conflict strategy changed: " + old.getDisplayName() + " -> " + strategy.getDisplayName());
        }
    }

    public ConflictResolutionStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public List<String> validateAllRegistries() {
        List<String> allIssues = new ArrayList<>();
        for (String registryName : registries.keySet()) {
            allIssues.addAll(registryProxy.validateConsistency(registryName));
        }
        return allIssues;
    }

    public Map<String, RegistrySerializer.SerializedRegistry> serializeAllRegistries() {
        Map<String, RegistrySerializer.SerializedRegistry> result = new LinkedHashMap<>();
        for (String registryName : registries.keySet()) {
            result.put(registryName, registryProxy.serializeRegistry(registryName));
        }
        return result;
    }

    public void deserializeAndMergeAll(Map<String, RegistrySerializer.SerializedRegistry> serialized) {
        for (Map.Entry<String, RegistrySerializer.SerializedRegistry> entry : serialized.entrySet()) {
            registryProxy.deserializeAndMerge(entry.getValue());
        }
    }

    public static CrossContainerBus getInstance() {
        return INSTANCE;
    }

    public void registerContainer(ModContainer container) {
        containers.add(container);
        System.out.println("[CrossBus] Registered container: " + container.getContainerId()
                + " (" + container.getContainerType().getDisplayName() + ")");
    }

    public void unregisterContainer(ModContainer container) {
        containers.remove(container);
    }

    public List<ModContainer> getRegisteredContainers() {
        return Collections.unmodifiableList(containers);
    }

    public void addEventListener(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeEventListener(String eventType, EventListener listener) {
        List<EventListener> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void postEvent(Event event) {
        String type = event.getType();
        List<EventListener> list = listeners.get(type);
        if (list != null) {
            for (EventListener listener : list) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    System.err.println("[CrossBus] Error in event listener for " + type + ": " + e.getMessage());
                }
            }
        }
    }

    public void addRegistrySync(RegistrySync sync) {
        registrySyncs.add(sync);
    }

    public void removeRegistrySync(RegistrySync sync) {
        registrySyncs.remove(sync);
    }

    public void firePreLaunch() {
        System.out.println("[CrossBus] Firing pre-launch to " + containers.size() + " containers...");
        for (ModContainer container : containers) {
            try {
                container.notifyPreLaunch();
            } catch (Exception e) {
                System.err.println("[CrossBus] Error in pre-launch for " + container.getContainerId() + ": " + e.getMessage());
            }
        }
        for (RegistrySync sync : registrySyncs) {
            try {
                sync.onPreLaunch();
            } catch (Exception e) {
                System.err.println("[CrossBus] Error in registry sync pre-launch: " + e.getMessage());
            }
        }
    }

    public void fireGameReady() {
        System.out.println("[CrossBus] Firing game-ready to " + containers.size() + " containers...");
        for (ModContainer container : containers) {
            try {
                container.notifyGameReady();
            } catch (Exception e) {
                System.err.println("[CrossBus] Error in game-ready for " + container.getContainerId() + ": " + e.getMessage());
            }
        }
    }

    public void firePostLaunch() {
        for (RegistrySync sync : registrySyncs) {
            try {
                sync.onPostLaunch();
            } catch (Exception e) {
                System.err.println("[CrossBus] Error in registry sync post-launch: " + e.getMessage());
            }
        }
    }

    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) sharedData.get(key);
    }

    public void removeSharedData(String key) {
        sharedData.remove(key);
    }

    public Map<String, Object> getSharedDataSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(sharedData));
    }

    public RegistryProxy getRegistryProxy() {
        return registryProxy;
    }

    public Map<String, Object> getRegistry(String registryName) {
        return registries.getOrDefault(registryName, Collections.emptyMap());
    }

    public Set<String> getAllRegistryNames() {
        return Collections.unmodifiableSet(registries.keySet());
    }

    public void clearRegistries() {
        registries.clear();
        registryModOwners.clear();
        allRegistryEntries.clear();
    }

    public void clear() {
        listeners.clear();
        registrySyncs.clear();
        sharedData.clear();
        containers.clear();
        clearRegistries();
    }
}

package com.panloader.forge;

import com.panloader.core.CrossContainerBus;
import com.panloader.core.ForgeEventBridge;
import com.panloader.core.ForgeEventType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class ForgeCapabilitySystem {

    public interface ForgeCapability {
        ForgeCapabilityKey<?> getKey();

        void onAttach(Object target);

        void onDetach(Object target);

        default Map<String, Object> serialize() {
            return new HashMap<>();
        }

        default void deserialize(Map<String, Object> data) {
        }

        default boolean isDirty() {
            return false;
        }

        default void markClean() {
        }
    }

    public static class ForgeCapabilityKey<T extends ForgeCapability> {
        private final String id;
        private final Class<T> capabilityClass;
        private final Supplier<T> factory;
        private final List<Class<?>> validTargets = new ArrayList<>();

        private ForgeCapabilityKey(String id, Class<T> capabilityClass, Supplier<T> factory) {
            this.id = id;
            this.capabilityClass = capabilityClass;
            this.factory = factory;
        }

        public static <T extends ForgeCapability> ForgeCapabilityKey<T> create(
                String id, Class<T> capabilityClass, Supplier<T> factory) {
            return new ForgeCapabilityKey<>(id, capabilityClass, factory);
        }

        public ForgeCapabilityKey<T> addValidTarget(Class<?> targetClass) {
            validTargets.add(targetClass);
            return this;
        }

        public String getId() {
            return id;
        }

        public Class<T> getCapabilityClass() {
            return capabilityClass;
        }

        public T createInstance() {
            return factory.get();
        }

        public boolean isValidTarget(Object target) {
            if (validTargets.isEmpty()) {
                return true;
            }
            for (Class<?> targetClass : validTargets) {
                if (targetClass.isInstance(target)) {
                    return true;
                }
            }
            return false;
        }
    }

    public interface ForgeCapabilityProvider {
        String getId();

        <T extends ForgeCapability> ForgeCapabilityKey<T> getKey();

        default void onCapabilityAttached(Object target, ForgeCapability capability) {
        }

        default void onCapabilityDetached(Object target, ForgeCapability capability) {
        }

        default List<ForgeCapabilityKey<?>> getProvidedKeys() {
            return Collections.emptyList();
        }
    }

    public static class CapabilityAttachment {
        private final ForgeCapabilityKey<?> key;
        private final ForgeCapability capability;
        private final Object target;
        private final long attachedAt;

        public CapabilityAttachment(ForgeCapabilityKey<?> key, ForgeCapability capability, Object target) {
            this.key = key;
            this.capability = capability;
            this.target = target;
            this.attachedAt = System.currentTimeMillis();
        }

        public ForgeCapabilityKey<?> getKey() { return key; }
        public ForgeCapability getCapability() { return capability; }
        public Object getTarget() { return target; }
        public long getAttachedAt() { return attachedAt; }
    }

    private static final ForgeCapabilitySystem INSTANCE = new ForgeCapabilitySystem();

    private final Map<String, ForgeCapabilityKey<?>> registeredKeys = new ConcurrentHashMap<>();
    private final Map<String, ForgeCapabilityProvider> providers = new ConcurrentHashMap<>();
    private final Map<Object, Map<String, CapabilityAttachment>> targetCapabilities = new ConcurrentHashMap<>();
    private final Map<String, List<CapabilityAttachment>> keyAttachments = new ConcurrentHashMap<>();
    private final List<CapabilityAttachment> allAttachments = Collections.synchronizedList(new ArrayList<>());
    private final CrossContainerBus bus;

    private ForgeCapabilitySystem() {
        this.bus = CrossContainerBus.getInstance();
    }

    public static ForgeCapabilitySystem getInstance() {
        return INSTANCE;
    }

    public <T extends ForgeCapability> ForgeCapabilityKey<T> registerCapability(
            String modId, String name, Class<T> capabilityClass, Supplier<T> factory) {
        String fullId = modId + ":" + name;

        @SuppressWarnings("unchecked")
        ForgeCapabilityKey<T> key = (ForgeCapabilityKey<T>) registeredKeys.get(fullId);
        if (key != null) {
            return key;
        }

        key = ForgeCapabilityKey.create(fullId, capabilityClass, factory);
        registeredKeys.put(fullId, key);

        System.out.println("[CapabilitySystem] Registered capability: " + fullId
                + " (class: " + capabilityClass.getSimpleName() + ", by: " + modId + ")");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("capabilityId", fullId);
        eventData.put("modId", modId);
        eventData.put("capabilityClass", capabilityClass.getName());
        bus.postEvent(new CrossContainerBus.RegistryEvent("capability.register", fullId, eventData));

        return key;
    }

    public void registerProvider(ForgeCapabilityProvider provider) {
        String id = provider.getId();
        providers.put(id, provider);
        System.out.println("[CapabilitySystem] Registered provider: " + id);

        for (ForgeCapabilityKey<?> key : provider.getProvidedKeys()) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("providerId", id);
            eventData.put("capabilityId", key.getId());
            bus.postEvent(new CrossContainerBus.RegistryEvent("capability.provider", id, eventData));
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends ForgeCapability> T getCapability(Object target, ForgeCapabilityKey<T> key) {
        if (target == null || key == null) {
            return null;
        }

        Map<String, CapabilityAttachment> targetMap = targetCapabilities.get(target);
        if (targetMap == null) {
            return null;
        }

        CapabilityAttachment attachment = targetMap.get(key.getId());
        if (attachment == null) {
            return null;
        }

        return (T) attachment.getCapability();
    }

    public <T extends ForgeCapability> T attachCapability(Object target, ForgeCapabilityKey<T> key) {
        if (target == null || key == null) {
            return null;
        }

        if (!key.isValidTarget(target)) {
            System.err.println("[CapabilitySystem] Target " + target.getClass().getSimpleName()
                    + " is not valid for capability " + key.getId());
            return null;
        }

        T existing = getCapability(target, key);
        if (existing != null) {
            return existing;
        }

        T capability = key.createInstance();
        if (capability == null) {
            System.err.println("[CapabilitySystem] Factory returned null for " + key.getId());
            return null;
        }

        capability.onAttach(target);

        CapabilityAttachment attachment = new CapabilityAttachment(key, capability, target);
        Map<String, CapabilityAttachment> targetMap = targetCapabilities
                .computeIfAbsent(target, k -> new ConcurrentHashMap<>());
        targetMap.put(key.getId(), attachment);

        List<CapabilityAttachment> keyList = keyAttachments
                .computeIfAbsent(key.getId(), k -> Collections.synchronizedList(new ArrayList<>()));
        keyList.add(attachment);

        allAttachments.add(attachment);

        System.out.println("[CapabilitySystem] Attached " + key.getId()
                + " to " + target.getClass().getSimpleName()
                + " (total attachments: " + allAttachments.size() + ")");

        String providerId = findProviderForKey(key);
        ForgeCapabilityProvider provider = providerId != null ? providers.get(providerId) : null;
        if (provider != null) {
            provider.onCapabilityAttached(target, capability);
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("targetType", target.getClass().getName());
        eventData.put("capabilityId", key.getId());
        eventData.put("attachedAt", attachment.getAttachedAt());
        bus.postEvent(new CrossContainerBus.RegistryEvent("capability.attach", key.getId(), eventData));

        ForgeEventBridge.getInstance().fireForgeEvent(
                ForgeEventType.MOD_LOADED, createCapabilityEventData(target, key, "attached"));

        return capability;
    }

    public boolean detachCapability(Object target, ForgeCapabilityKey<?> key) {
        if (target == null || key == null) {
            return false;
        }

        Map<String, CapabilityAttachment> targetMap = targetCapabilities.get(target);
        if (targetMap == null) {
            return false;
        }

        CapabilityAttachment attachment = targetMap.remove(key.getId());
        if (attachment == null) {
            return false;
        }

        attachment.getCapability().onDetach(target);

        List<CapabilityAttachment> keyList = keyAttachments.get(key.getId());
        if (keyList != null) {
            keyList.remove(attachment);
        }
        allAttachments.remove(attachment);

        String providerId = findProviderForKey(key);
        ForgeCapabilityProvider provider = providerId != null ? providers.get(providerId) : null;
        if (provider != null) {
            provider.onCapabilityDetached(target, attachment.getCapability());
        }

        System.out.println("[CapabilitySystem] Detached " + key.getId()
                + " from " + target.getClass().getSimpleName());

        bus.postEvent(new CrossContainerBus.RegistryEvent("capability.detach", key.getId(),
                createCapabilityEventData(target, key, "detached")));

        return true;
    }

    public boolean hasCapability(Object target, ForgeCapabilityKey<?> key) {
        return getCapability(target, key) != null;
    }

    public List<CapabilityAttachment> getAttachmentsForTarget(Object target) {
        Map<String, CapabilityAttachment> targetMap = targetCapabilities.get(target);
        if (targetMap == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(targetMap.values()));
    }

    public List<CapabilityAttachment> getAttachmentsForKey(String keyId) {
        List<CapabilityAttachment> list = keyAttachments.get(keyId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public List<CapabilityAttachment> getAllAttachments() {
        return Collections.unmodifiableList(new ArrayList<>(allAttachments));
    }

    public Set<String> getRegisteredCapabilityIds() {
        return Collections.unmodifiableSet(registeredKeys.keySet());
    }

    public <T extends ForgeCapability> ForgeCapabilityKey<T> getKey(String id) {
        @SuppressWarnings("unchecked")
        ForgeCapabilityKey<T> key = (ForgeCapabilityKey<T>) registeredKeys.get(id);
        return key;
    }

    public int getRegistrationCount() {
        return registeredKeys.size();
    }

    public int getAttachmentCount() {
        return allAttachments.size();
    }

    public Map<String, Object> serializeCapability(String keyId) {
        ForgeCapabilityKey<?> key = registeredKeys.get(keyId);
        if (key == null) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> serializedAttachments = new ArrayList<>();
        List<CapabilityAttachment> attachments = keyAttachments.getOrDefault(keyId, Collections.emptyList());

        for (CapabilityAttachment attachment : attachments) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("targetType", attachment.getTarget().getClass().getName());
            entry.put("data", attachment.getCapability().serialize());
            entry.put("attachedAt", attachment.getAttachedAt());
            serializedAttachments.add(entry);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("keyId", keyId);
        result.put("capabilityClass", key.getCapabilityClass().getName());
        result.put("attachments", serializedAttachments);
        return result;
    }

    public void attachCapabilitiesForMod(String modId, Object instance) {
        System.out.println("[CapabilitySystem] Attaching capabilities for mod: " + modId);

        int attached = 0;
        for (Map.Entry<String, ForgeCapabilityKey<?>> entry : registeredKeys.entrySet()) {
            String keyId = entry.getKey();
            ForgeCapabilityKey<?> key = entry.getValue();

            if (keyId.startsWith(modId + ":")) {
                if (key.isValidTarget(instance)) {
                    attachCapability(instance, key);
                    attached++;
                }
            }
        }

        System.out.println("[CapabilitySystem] Attached " + attached + " capability/capabilities for " + modId);
    }

    private String findProviderForKey(ForgeCapabilityKey<?> key) {
        for (Map.Entry<String, ForgeCapabilityProvider> entry : providers.entrySet()) {
            List<ForgeCapabilityKey<?>> providedKeys = entry.getValue().getProvidedKeys();
            for (ForgeCapabilityKey<?> providedKey : providedKeys) {
                if (providedKey.getId().equals(key.getId())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Map<String, Object> createCapabilityEventData(Object target, ForgeCapabilityKey<?> key, String action) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetType", target != null ? target.getClass().getName() : "null");
        data.put("capabilityId", key.getId());
        data.put("action", action);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    public void syncCapabilities() {
        System.out.println("[CapabilitySystem] Syncing " + allAttachments.size() + " capability attachments...");

        int synced = 0;
        for (CapabilityAttachment attachment : allAttachments) {
            try {
                Map<String, Object> data = attachment.getCapability().serialize();
                if (!data.isEmpty()) {
                    bus.postEvent(new CrossContainerBus.RegistryEvent(
                            "capability.sync", attachment.getKey().getId(), data));
                    synced++;
                }
            } catch (Exception e) {
                System.err.println("[CapabilitySystem] Error syncing capability "
                        + attachment.getKey().getId() + ": " + e.getMessage());
            }
        }

        System.out.println("[CapabilitySystem] Synced " + synced + "/" + allAttachments.size() + " capabilities");
    }

    public void clear() {
        registeredKeys.clear();
        providers.clear();
        targetCapabilities.clear();
        keyAttachments.clear();
        allAttachments.clear();
    }
}
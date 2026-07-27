package com.panloader.forge;

import com.panloader.core.CrossContainerBus;
import com.panloader.core.ForgeEventBridge;
import com.panloader.core.ForgeEventType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ForgeBiomeModifier {

    public enum ModifierType {
        ADD,
        REMOVE,
        REPLACE,
        WEIGHT
    }

    public interface BiomeSelector {
        boolean test(String biomeId, Map<String, Object> properties);
    }

    public static class BiomeEntry {
        private final String id;
        private final String registryName;
        private final Map<String, Object> properties;
        private double weight;
        private final String modId;

        public BiomeEntry(String id, String registryName, Map<String, Object> properties, String modId) {
            this.id = id;
            this.registryName = registryName;
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
            this.weight = 1.0;
            this.modId = modId;
        }

        public String getId() { return id; }
        public String getRegistryName() { return registryName; }
        public Map<String, Object> getProperties() { return Collections.unmodifiableMap(properties); }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public String getModId() { return modId; }

        public void setProperty(String key, Object value) {
            properties.put(key, value);
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("registryName", registryName);
            map.put("weight", weight);
            map.put("modId", modId);
            map.put("properties", new HashMap<>(properties));
            return map;
        }

        public static BiomeEntry fromMap(Map<String, Object> map) {
            String id = (String) map.get("id");
            String registryName = (String) map.get("registryName");
            double weight = map.get("weight") instanceof Number ? ((Number) map.get("weight")).doubleValue() : 1.0;
            String modId = (String) map.get("modId");

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) map.getOrDefault("properties", new HashMap<>());

            BiomeEntry entry = new BiomeEntry(id, registryName, properties, modId);
            entry.setWeight(weight);
            return entry;
        }
    }

    public static class BiomeModifierRule {
        private final String id;
        private final String modId;
        private final ModifierType type;
        private final BiomeSelector selector;
        private final BiomeEntry biomeEntry;
        private final Map<String, Object> extraData;
        private int priority = 0;
        private boolean active = true;

        public BiomeModifierRule(String id, String modId, ModifierType type,
                                  BiomeSelector selector, BiomeEntry biomeEntry) {
            this.id = id;
            this.modId = modId;
            this.type = type;
            this.selector = selector;
            this.biomeEntry = biomeEntry;
            this.extraData = new HashMap<>();
        }

        public String getId() { return id; }
        public String getModId() { return modId; }
        public ModifierType getType() { return type; }
        public BiomeSelector getSelector() { return selector; }
        public BiomeEntry getBiomeEntry() { return biomeEntry; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Map<String, Object> getExtraData() { return extraData; }

        public boolean matches(String biomeId, Map<String, Object> properties) {
            return active && selector.test(biomeId, properties);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("modId", modId);
            map.put("type", type.name());
            map.put("priority", priority);
            map.put("active", active);
            map.put("biomeEntry", biomeEntry != null ? biomeEntry.toMap() : null);
            map.put("extraData", new HashMap<>(extraData));
            return map;
        }
    }

    public interface BiomeModifierProcessor {
        void apply(BiomeModifierRule rule, List<BiomeEntry> currentBiomes);
    }

    public static class AddBiomeProcessor implements BiomeModifierProcessor {
        @Override
        public void apply(BiomeModifierRule rule, List<BiomeEntry> currentBiomes) {
            BiomeEntry newBiome = rule.getBiomeEntry();
            if (newBiome != null) {
                currentBiomes.add(newBiome);
                System.out.println("[BiomeModifier] Added biome: " + newBiome.getId()
                        + " (weight: " + newBiome.getWeight() + ")");
            }
        }
    }

    public static class RemoveBiomeProcessor implements BiomeModifierProcessor {
        @Override
        public void apply(BiomeModifierRule rule, List<BiomeEntry> currentBiomes) {
            Iterator<BiomeEntry> it = currentBiomes.iterator();
            int removed = 0;
            while (it.hasNext()) {
                BiomeEntry entry = it.next();
                if (rule.matches(entry.getId(), entry.getProperties())) {
                    it.remove();
                    removed++;
                    System.out.println("[BiomeModifier] Removed biome: " + entry.getId());
                }
            }
            if (removed == 0) {
                System.out.println("[BiomeModifier] No biomes matched removal rule: " + rule.getId());
            }
        }
    }

    public static class ReplaceBiomeProcessor implements BiomeModifierProcessor {
        @Override
        public void apply(BiomeModifierRule rule, List<BiomeEntry> currentBiomes) {
            BiomeEntry replacement = rule.getBiomeEntry();
            if (replacement == null) return;

            int replaced = 0;
            for (int i = 0; i < currentBiomes.size(); i++) {
                BiomeEntry entry = currentBiomes.get(i);
                if (rule.matches(entry.getId(), entry.getProperties())) {
                    currentBiomes.set(i, replacement);
                    replaced++;
                    System.out.println("[BiomeModifier] Replaced biome: " + entry.getId()
                            + " -> " + replacement.getId());
                }
            }
            if (replaced == 0) {
                System.out.println("[BiomeModifier] No biomes matched replacement rule: " + rule.getId());
            }
        }
    }

    public static class WeightBiomeProcessor implements BiomeModifierProcessor {
        @Override
        public void apply(BiomeModifierRule rule, List<BiomeEntry> currentBiomes) {
            Double newWeight = (Double) rule.getExtraData().get("newWeight");
            if (newWeight == null) return;

            int modified = 0;
            for (BiomeEntry entry : currentBiomes) {
                if (rule.matches(entry.getId(), entry.getProperties())) {
                    double oldWeight = entry.getWeight();
                    entry.setWeight(newWeight);
                    modified++;
                    System.out.println("[BiomeModifier] Adjusted weight: " + entry.getId()
                            + " " + oldWeight + " -> " + newWeight);
                }
            }
            if (modified == 0) {
                System.out.println("[BiomeModifier] No biomes matched weight rule: " + rule.getId());
            }
        }
    }

    private static final ForgeBiomeModifier INSTANCE = new ForgeBiomeModifier();

    private final Map<String, List<BiomeEntry>> biomesByRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<BiomeModifierRule>> rulesByMod = new ConcurrentHashMap<>();
    private final List<BiomeModifierRule> allRules = Collections.synchronizedList(new ArrayList<>());
    private final Map<ModifierType, BiomeModifierProcessor> processors = new EnumMap<>(ModifierType.class);
    private final CrossContainerBus bus;
    private final ForgeEventBridge eventBridge;
    private int totalModifications = 0;

    private ForgeBiomeModifier() {
        this.bus = CrossContainerBus.getInstance();
        this.eventBridge = ForgeEventBridge.getInstance();
        initProcessors();
        registerDefaultBiomes();
    }

    private void initProcessors() {
        processors.put(ModifierType.ADD, new AddBiomeProcessor());
        processors.put(ModifierType.REMOVE, new RemoveBiomeProcessor());
        processors.put(ModifierType.REPLACE, new ReplaceBiomeProcessor());
        processors.put(ModifierType.WEIGHT, new WeightBiomeProcessor());
    }

    private void registerDefaultBiomes() {
        String[] defaultBiomes = {
                "minecraft:plains", "minecraft:desert", "minecraft:mountains",
                "minecraft:forest", "minecraft:taiga", "minecraft:swamp",
                "minecraft:river", "minecraft:ocean", "minecraft:nether_wastes",
                "minecraft:the_end", "minecraft:sunflower_plains", "minecraft:flower_forest",
                "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:cherry_grove",
                "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:savanna",
                "minecraft:badlands", "minecraft:jungle", "minecraft:mushroom_fields"
        };

        for (String biomeId : defaultBiomes) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("temperature", getDefaultTemperature(biomeId));
            properties.put("downfall", getDefaultDownfall(biomeId));
            properties.put("category", getCategoryFromId(biomeId));

            BiomeEntry entry = new BiomeEntry(biomeId, "forge:biomes", properties, "minecraft");
            registerBiome(entry);
        }

        System.out.println("[BiomeModifier] Registered " + defaultBiomes.length + " default biomes");
    }

    private double getDefaultTemperature(String biomeId) {
        if (biomeId.contains("desert") || biomeId.contains("savanna")) return 2.0;
        if (biomeId.contains("snowy") || biomeId.contains("ice") || biomeId.contains("taiga")) return -0.5;
        if (biomeId.contains("mountain")) return 0.2;
        if (biomeId.contains("nether")) return 2.0;
        if (biomeId.contains("end")) return 0.5;
        if (biomeId.contains("cherry")) return 0.5;
        return 0.7;
    }

    private double getDefaultDownfall(String biomeId) {
        if (biomeId.contains("desert")) return 0.0;
        if (biomeId.contains("savanna")) return 0.0;
        if (biomeId.contains("nether")) return 0.0;
        if (biomeId.contains("cherry") || biomeId.contains("flower")) return 0.8;
        return 0.5;
    }

    private String getCategoryFromId(String biomeId) {
        if (biomeId.contains("ocean") || biomeId.contains("river")) return "ocean";
        if (biomeId.contains("mountain")) return "mountain";
        if (biomeId.contains("forest") || biomeId.contains("taiga")) return "forest";
        if (biomeId.contains("desert") || biomeId.contains("savanna") || biomeId.contains("badlands")) return "desert";
        if (biomeId.contains("nether")) return "nether";
        if (biomeId.contains("end")) return "the_end";
        if (biomeId.contains("snowy") || biomeId.contains("ice")) return "icy";
        if (biomeId.contains("swamp") || biomeId.contains("mushroom")) return "swamp";
        return "plains";
    }

    public static ForgeBiomeModifier getInstance() {
        return INSTANCE;
    }

    public void registerBiome(BiomeEntry entry) {
        String registryName = entry.getRegistryName();
        List<BiomeEntry> registry = biomesByRegistry.computeIfAbsent(
                registryName, k -> Collections.synchronizedList(new ArrayList<>()));

        registry.add(entry);

        bus.postEvent(new CrossContainerBus.RegistryEvent(
                "biome.register", entry.getId(), entry.toMap()));
    }

    public void registerBiome(String modId, String id, String registryName,
                               Map<String, Object> properties) {
        BiomeEntry entry = new BiomeEntry(modId + ":" + id, registryName, properties, modId);
        registerBiome(entry);
    }

    public BiomeModifierRule addModifierRule(String modId, String ruleId,
                                              ModifierType type, BiomeSelector selector,
                                              BiomeEntry biomeEntry) {
        BiomeModifierRule rule = new BiomeModifierRule(ruleId, modId, type, selector, biomeEntry);
        rulesByMod.computeIfAbsent(modId, k -> Collections.synchronizedList(new ArrayList<>())).add(rule);
        allRules.add(rule);

        System.out.println("[BiomeModifier] Added modifier rule: " + ruleId
                + " (mod: " + modId + ", type: " + type.name() + ")");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("modId", modId);
        eventData.put("ruleId", ruleId);
        eventData.put("type", type.name());
        bus.postEvent(new CrossContainerBus.RegistryEvent("biome.modifier.add", ruleId, eventData));

        return rule;
    }

    public void removeModifierRule(String ruleId) {
        allRules.removeIf(rule -> rule.getId().equals(ruleId));
        for (List<BiomeModifierRule> rules : rulesByMod.values()) {
            rules.removeIf(rule -> rule.getId().equals(ruleId));
        }

        System.out.println("[BiomeModifier] Removed modifier rule: " + ruleId);
    }

    public void setRuleActive(String ruleId, boolean active) {
        for (BiomeModifierRule rule : allRules) {
            if (rule.getId().equals(ruleId)) {
                rule.setActive(active);
                break;
            }
        }
    }

    public void applyModifications(String registryName) {
        List<BiomeEntry> biomes = biomesByRegistry.getOrDefault(
                registryName, Collections.synchronizedList(new ArrayList<>()));

        System.out.println("[BiomeModifier] Applying " + allRules.size()
                + " modifier rules to registry " + registryName);

        int rulesApplied = 0;
        for (BiomeModifierRule rule : allRules) {
            if (!rule.isActive()) continue;

            BiomeModifierProcessor processor = processors.get(rule.getType());
            if (processor != null) {
                try {
                    processor.apply(rule, biomes);
                    rulesApplied++;
                    totalModifications++;

                    Map<String, Object> eventData = rule.toMap();
                    bus.postEvent(new CrossContainerBus.RegistryEvent(
                            "biome.modifier.apply", rule.getId(), eventData));
                } catch (Exception e) {
                    System.err.println("[BiomeModifier] Error applying rule "
                            + rule.getId() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[BiomeModifier] Applied " + rulesApplied
                + "/" + allRules.size() + " rules. Total biomes in "
                + registryName + ": " + biomes.size());
    }

    public void applyAllModifications() {
        for (String registryName : biomesByRegistry.keySet()) {
            applyModifications(registryName);
        }
    }

    public List<BiomeEntry> getBiomes(String registryName) {
        List<BiomeEntry> biomes = biomesByRegistry.get(registryName);
        return biomes != null ? Collections.unmodifiableList(new ArrayList<>(biomes)) : Collections.emptyList();
    }

    public List<BiomeEntry> getBiomesForMod(String modId) {
        List<BiomeEntry> result = new ArrayList<>();
        for (List<BiomeEntry> biomes : biomesByRegistry.values()) {
            for (BiomeEntry entry : biomes) {
                if (entry.getModId().equals(modId)) {
                    result.add(entry);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public BiomeEntry getBiome(String registryName, String biomeId) {
        List<BiomeEntry> biomes = biomesByRegistry.get(registryName);
        if (biomes != null) {
            for (BiomeEntry entry : biomes) {
                if (entry.getId().equals(biomeId)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public List<BiomeModifierRule> getRulesForMod(String modId) {
        List<BiomeModifierRule> rules = rulesByMod.get(modId);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }

    public List<BiomeModifierRule> getAllRules() {
        return Collections.unmodifiableList(new ArrayList<>(allRules));
    }

    public int getModifierCount() {
        return allRules.size();
    }

    public int getBiomeCount(String registryName) {
        List<BiomeEntry> biomes = biomesByRegistry.get(registryName);
        return biomes != null ? biomes.size() : 0;
    }

    public Set<String> getRegistryNames() {
        return Collections.unmodifiableSet(biomesByRegistry.keySet());
    }

    public int getTotalModifications() {
        return totalModifications;
    }

    public void syncBiomeModifiers(String modId) {
        List<BiomeModifierRule> rules = rulesByMod.get(modId);
        if (rules == null) {
            return;
        }

        System.out.println("[BiomeModifier] Syncing " + rules.size()
                + " biome modifier rules for " + modId);

        for (BiomeModifierRule rule : rules) {
            Map<String, Object> eventData = rule.toMap();
            bus.postEvent(new CrossContainerBus.RegistryEvent(
                    "biome.sync", rule.getId(), eventData));
        }

        applyAllModifications();

        eventBridge.fireForgeEvent(ForgeEventType.REGISTER_BIOMES,
                createBiomeSyncEventData(modId));
    }

    public Map<String, Object> exportBiomeData() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<BiomeEntry>> entry : biomesByRegistry.entrySet()) {
            List<Map<String, Object>> biomeMaps = new ArrayList<>();
            for (BiomeEntry biome : entry.getValue()) {
                biomeMaps.add(biome.toMap());
            }
            result.put(entry.getKey(), biomeMaps);
        }

        Map<String, Object> rulesData = new LinkedHashMap<>();
        for (Map.Entry<String, List<BiomeModifierRule>> entry : rulesByMod.entrySet()) {
            List<Map<String, Object>> ruleMaps = new ArrayList<>();
            for (BiomeModifierRule rule : entry.getValue()) {
                ruleMaps.add(rule.toMap());
            }
            rulesData.put(entry.getKey(), ruleMaps);
        }
        result.put("_rules", rulesData);

        return result;
    }

    public void importBiomeData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key.equals("_rules")) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> biomeMaps = (List<Map<String, Object>>) entry.getValue();

            if (biomeMaps != null) {
                List<BiomeEntry> biomes = biomesByRegistry.computeIfAbsent(
                        key, k -> Collections.synchronizedList(new ArrayList<>()));

                for (Map<String, Object> biomeMap : biomeMaps) {
                    BiomeEntry biome = BiomeEntry.fromMap(biomeMap);
                    if (biome != null) {
                        biomes.add(biome);
                    }
                }
            }
        }

        Map<String, Object> rulesData = (Map<String, Object>) data.get("_rules");
        if (rulesData != null) {
            for (Map.Entry<String, Object> entry : rulesData.entrySet()) {
                String modId = entry.getKey();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) entry.getValue();

                if (ruleMaps != null) {
                    List<BiomeModifierRule> rules = rulesByMod.computeIfAbsent(
                            modId, k -> Collections.synchronizedList(new ArrayList<>()));

                    for (Map<String, Object> ruleMap : ruleMaps) {
                        System.out.println("[BiomeModifier] Imported rule for " + modId + ": " + ruleMap.get("id"));
                    }
                }
            }
        }

        System.out.println("[BiomeModifier] Imported biome data from " + data.size() + " registries");
    }

    private Map<String, Object> createBiomeSyncEventData(String modId) {
        Map<String, Object> data = new HashMap<>();
        data.put("modId", modId);
        data.put("timestamp", System.currentTimeMillis());
        data.put("totalRules", allRules.size());
        data.put("totalBiomes", biomesByRegistry.values().stream()
                .mapToInt(List::size).sum());
        return data;
    }

    public void clear() {
        biomesByRegistry.clear();
        rulesByMod.clear();
        allRules.clear();
        totalModifications = 0;
    }
}
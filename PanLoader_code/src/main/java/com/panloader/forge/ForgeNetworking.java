package com.panloader.forge;

import com.panloader.core.CrossContainerBus;
import com.panloader.core.ForgeEventBridge;
import com.panloader.core.ForgeEventType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ForgeNetworking {

    public enum NetworkDirection {
        PLAY_TO_SERVER,
        PLAY_TO_CLIENT,
        LOGIN_TO_SERVER,
        LOGIN_TO_CLIENT
    }

    public interface CustomPayload {
        String getId();
        Map<String, Object> writeData();
        void readData(Map<String, Object> data);
    }

    public interface PayloadHandler<T extends CustomPayload> {
        void handle(T payload, NetworkContext context);
    }

    public static class NetworkContext {
        private final String channelName;
        private final String senderModId;
        private final NetworkDirection direction;
        private final long timestamp;

        public NetworkContext(String channelName, String senderModId, NetworkDirection direction) {
            this.channelName = channelName;
            this.senderModId = senderModId;
            this.direction = direction;
            this.timestamp = System.currentTimeMillis();
        }

        public String getChannelName() { return channelName; }
        public String getSenderModId() { return senderModId; }
        public NetworkDirection getDirection() { return direction; }
        public long getTimestamp() { return timestamp; }
    }

    public interface NetworkChannel {
        String getName();
        NetworkDirection getDirection();
        <T extends CustomPayload> void registerPayload(
                String payloadId, Class<T> payloadClass,
                Function<T, Map<String, Object>> encoder,
                BiConsumer<Map<String, Object>, T> decoder,
                PayloadHandler<T> handler);
        void send(CustomPayload payload);
        int getPayloadCount();
    }

    public static class ForgeNetworkChannel implements NetworkChannel {
        private final String name;
        private final NetworkDirection direction;
        private final Map<String, PayloadRegistration<?>> payloads = new LinkedHashMap<>();
        private final List<CustomPayload> sentPayloads = Collections.synchronizedList(new ArrayList<>());
        private final List<CustomPayload> receivedPayloads = Collections.synchronizedList(new ArrayList<>());

        public ForgeNetworkChannel(String name, NetworkDirection direction) {
            this.name = name;
            this.direction = direction;
        }

        @Override
        public String getName() { return name; }

        @Override
        public NetworkDirection getDirection() { return direction; }

        @Override
        public <T extends CustomPayload> void registerPayload(
                String payloadId, Class<T> payloadClass,
                Function<T, Map<String, Object>> encoder,
                BiConsumer<Map<String, Object>, T> decoder,
                PayloadHandler<T> handler) {

            PayloadRegistration<T> registration = new PayloadRegistration<>(
                    payloadId, payloadClass, encoder, decoder, handler);
            payloads.put(payloadId, registration);

            System.out.println("[ForgeNetwork] Registered payload: " + payloadId
                    + " on channel " + name + " (" + direction.name() + ")");
        }

        @Override
        public void send(CustomPayload payload) {
            sentPayloads.add(payload);
            System.out.println("[ForgeNetwork] Sent payload: " + payload.getId()
                    + " on channel " + name + " (" + direction.name() + ")");
        }

        public void receive(CustomPayload payload) {
            receivedPayloads.add(payload);
            System.out.println("[ForgeNetwork] Received payload: " + payload.getId()
                    + " on channel " + name);

            PayloadRegistration<?> registration = payloads.get(payload.getId());
            if (registration != null) {
                registration.dispatch(payload);
            } else {
                System.out.println("[ForgeNetwork] No handler for payload: " + payload.getId());
            }
        }

        @Override
        public int getPayloadCount() { return payloads.size(); }

        @SuppressWarnings("unchecked")
        public <T extends CustomPayload> PayloadRegistration<T> getRegistration(String payloadId) {
            return (PayloadRegistration<T>) payloads.get(payloadId);
        }

        public List<CustomPayload> getSentPayloads() {
            return Collections.unmodifiableList(new ArrayList<>(sentPayloads));
        }

        public List<CustomPayload> getReceivedPayloads() {
            return Collections.unmodifiableList(new ArrayList<>(receivedPayloads));
        }

        public void clearHistory() {
            sentPayloads.clear();
            receivedPayloads.clear();
        }
    }

    public static class PayloadRegistration<T extends CustomPayload> {
        private final String payloadId;
        private final Class<T> payloadClass;
        private final Function<T, Map<String, Object>> encoder;
        private final BiConsumer<Map<String, Object>, T> decoder;
        private final PayloadHandler<T> handler;

        public PayloadRegistration(String payloadId, Class<T> payloadClass,
                                   Function<T, Map<String, Object>> encoder,
                                   BiConsumer<Map<String, Object>, T> decoder,
                                   PayloadHandler<T> handler) {
            this.payloadId = payloadId;
            this.payloadClass = payloadClass;
            this.encoder = encoder;
            this.decoder = decoder;
            this.handler = handler;
        }

        public String getPayloadId() { return payloadId; }
        public Class<T> getPayloadClass() { return payloadClass; }

        public Map<String, Object> encode(T payload) {
            if (encoder != null) {
                return encoder.apply(payload);
            }
            return payload.writeData();
        }

        @SuppressWarnings("unchecked")
        public T decode(Map<String, Object> data) {
            try {
                T payload = payloadClass.getDeclaredConstructor().newInstance();
                if (decoder != null) {
                    decoder.accept(data, payload);
                } else {
                    payload.readData(data);
                }
                return payload;
            } catch (Exception e) {
                System.err.println("[ForgeNetwork] Error decoding payload " + payloadId + ": " + e.getMessage());
                return null;
            }
        }

        public void dispatch(CustomPayload payload) {
            if (handler != null) {
                try {
                    T typedPayload = payloadClass.cast(payload);
                    NetworkContext context = new NetworkContext("", "", NetworkDirection.PLAY_TO_CLIENT);
                    handler.handle(typedPayload, context);
                } catch (Exception e) {
                    System.err.println("[ForgeNetwork] Error dispatching payload " + payloadId + ": " + e.getMessage());
                }
            }
        }
    }

    public static class NetworkEvent {
        private final String type;
        private final Map<String, Object> data;
        private final long timestamp;

        public NetworkEvent(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    private static final ForgeNetworking INSTANCE = new ForgeNetworking();

    private final Map<String, ForgeNetworkChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, List<NetworkEvent>> eventLog = new ConcurrentHashMap<>();
    private final CrossContainerBus bus;
    private final ForgeEventBridge eventBridge;
    private int totalPayloadsSent = 0;
    private int totalPayloadsReceived = 0;

    private ForgeNetworking() {
        this.bus = CrossContainerBus.getInstance();
        this.eventBridge = ForgeEventBridge.getInstance();
    }

    public static ForgeNetworking getInstance() {
        return INSTANCE;
    }

    public ForgeNetworkChannel createChannel(String modId, String channelName, NetworkDirection direction) {
        String fullName = modId + ":" + channelName;
        ForgeNetworkChannel channel = channels.get(fullName);
        if (channel != null) {
            return channel;
        }

        channel = new ForgeNetworkChannel(fullName, direction);
        channels.put(fullName, channel);

        System.out.println("[ForgeNetwork] Created channel: " + fullName
                + " (" + direction.name() + ")");

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("modId", modId);
        eventData.put("channelName", channelName);
        eventData.put("direction", direction.name());
        bus.postEvent(new CrossContainerBus.RegistryEvent("network.channel.create", fullName, eventData));

        return channel;
    }

    public ForgeNetworkChannel getChannel(String modId, String channelName) {
        return channels.get(modId + ":" + channelName);
    }

    public ForgeNetworkChannel getChannel(String fullName) {
        return channels.get(fullName);
    }

    public List<ForgeNetworkChannel> getChannelsForMod(String modId) {
        List<ForgeNetworkChannel> result = new ArrayList<>();
        String prefix = modId + ":";
        for (Map.Entry<String, ForgeNetworkChannel> entry : channels.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void sendPayload(String modId, String channelName, CustomPayload payload) {
        String fullName = modId + ":" + channelName;
        ForgeNetworkChannel channel = channels.get(fullName);

        if (channel == null) {
            System.err.println("[ForgeNetwork] Channel not found: " + fullName);
            return;
        }

        channel.send(payload);
        totalPayloadsSent++;

        Map<String, Object> payloadData = payload.writeData();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("modId", modId);
        eventData.put("channelName", channelName);
        eventData.put("payloadId", payload.getId());
        eventData.put("payloadData", payloadData);
        eventData.put("timestamp", System.currentTimeMillis());

        NetworkEvent event = new NetworkEvent("send", eventData);
        eventLog.computeIfAbsent(fullName, k -> Collections.synchronizedList(new ArrayList<>())).add(event);

        bus.postEvent(new CrossContainerBus.RegistryEvent("network.send", fullName, eventData));
        eventBridge.fireForgeEvent(ForgeEventType.NETWORK_PACKET, eventData);
    }

    public void receivePayload(String modId, String channelName, CustomPayload payload) {
        String fullName = modId + ":" + channelName;
        ForgeNetworkChannel channel = channels.get(fullName);

        if (channel == null) {
            System.err.println("[ForgeNetwork] Channel not found for receive: " + fullName);
            return;
        }

        channel.receive(payload);
        totalPayloadsReceived++;

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("modId", modId);
        eventData.put("channelName", channelName);
        eventData.put("payloadId", payload.getId());
        eventData.put("timestamp", System.currentTimeMillis());

        NetworkEvent event = new NetworkEvent("receive", eventData);
        eventLog.computeIfAbsent(fullName, k -> Collections.synchronizedList(new ArrayList<>())).add(event);

        bus.postEvent(new CrossContainerBus.RegistryEvent("network.receive", fullName, eventData));
    }

    public void registerNetworkPayloadsForMod(String modId) {
        System.out.println("[ForgeNetwork] Registering network payloads for mod: " + modId);

        List<ForgeNetworkChannel> modChannels = getChannelsForMod(modId);
        int totalPayloads = 0;

        for (ForgeNetworkChannel channel : modChannels) {
            totalPayloads += channel.getPayloadCount();
        }

        System.out.println("[ForgeNetwork] " + modId + " has " + modChannels.size()
                + " channels with " + totalPayloads + " total payloads");
    }

    public int getPayloadCount() {
        int count = 0;
        for (ForgeNetworkChannel channel : channels.values()) {
            count += channel.getPayloadCount();
        }
        return count;
    }

    public int getChannelCount() {
        return channels.size();
    }

    public int getTotalPayloadsSent() {
        return totalPayloadsSent;
    }

    public int getTotalPayloadsReceived() {
        return totalPayloadsReceived;
    }

    public Map<String, Object> getNetworkStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalChannels", channels.size());
        stats.put("totalPayloads", getPayloadCount());
        stats.put("payloadsSent", totalPayloadsSent);
        stats.put("payloadsReceived", totalPayloadsReceived);

        Map<String, Object> channelStats = new LinkedHashMap<>();
        for (Map.Entry<String, ForgeNetworkChannel> entry : channels.entrySet()) {
            ForgeNetworkChannel channel = entry.getValue();
            Map<String, Object> channelData = new LinkedHashMap<>();
            channelData.put("payloads", channel.getPayloadCount());
            channelData.put("direction", channel.getDirection().name());
            channelData.put("sent", channel.getSentPayloads().size());
            channelData.put("received", channel.getReceivedPayloads().size());
            channelStats.put(entry.getKey(), channelData);
        }
        stats.put("channels", channelStats);

        return stats;
    }

    public List<NetworkEvent> getEventLog(String channelName) {
        List<NetworkEvent> log = eventLog.get(channelName);
        return log != null ? Collections.unmodifiableList(log) : Collections.emptyList();
    }

    public void clearAll() {
        for (ForgeNetworkChannel channel : channels.values()) {
            channel.clearHistory();
        }
        channels.clear();
        eventLog.clear();
        totalPayloadsSent = 0;
        totalPayloadsReceived = 0;
    }
}
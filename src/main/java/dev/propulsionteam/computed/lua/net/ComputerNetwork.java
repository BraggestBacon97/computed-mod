package dev.propulsionteam.computed.lua.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Cross-computer message network addressed by numeric computer IDs (rednet-style). Pure Java —
 * unit tests never load Minecraft classes; the endpoint glue lives in {@code NetEndpoints}.
 *
 * <p>A station counts as online while it is touched (endpoint calls and runtime lifecycle ticks)
 * and for {@link #ONLINE_WINDOW_MS} afterwards. Its queue survives {@link #STATION_TTL_MS} without
 * activity; anything idle longer is dropped entirely, which also releases the waiting receives of
 * computers that were unloaded in the meantime.
 */
public final class ComputerNetwork {
    /** Messages kept per computer; the oldest are dropped when the queue is full. */
    public static final int MAX_QUEUE = 64;
    /** Maximum characters per message. */
    public static final int MAX_MESSAGE_CHARS = 1024;
    /** How long a computer counts as online after its last activity. */
    public static final long ONLINE_WINDOW_MS = 30_000L;
    /** How long messages and waiters of an idle computer are kept. */
    public static final long STATION_TTL_MS = 120_000L;

    /** One waiting receive; stale sinks are dropped instead of being delivered to. */
    public interface MessageSink {
        /**
         * Hands the message to the waiter.
         *
         * @return false when the sink already completed and must be dropped
         */
        boolean deliver(int sender, String text);

        /** @return true when the sink can no longer receive (completed or cancelled) */
        boolean stale();
    }

    public record Message(int sender, String text) {}

    private static final class Station {
        private final ArrayDeque<Message> inbox = new ArrayDeque<>();
        private final List<MessageSink> waiters = new ArrayList<>();
        private long lastSeen;
        private boolean touched;

        private Station(long now) {
            lastSeen = now;
        }
    }

    private final LongSupplier clock;
    private final Map<Integer, Station> stations = new HashMap<>();

    public ComputerNetwork() {
        this(System::currentTimeMillis);
    }

    public ComputerNetwork(LongSupplier clock) {
        this.clock = clock;
    }

    /** Marks the computer as active so it counts as online for broadcasts. */
    public synchronized void touch(int computerId) {
        long now = clock.getAsLong();
        Station station = station(computerId, now);
        station.lastSeen = now;
        station.touched = true;
    }

    /** @return whether the computer had activity within {@link #ONLINE_WINDOW_MS} */
    public synchronized boolean isOnline(int computerId) {
        Station station = stations.get(computerId);
        return station != null && isOnline(station, clock.getAsLong());
    }

    /**
     * Queues a message for the target computer — directly to its waiting receive when one is
     * pending — and reports whether the target counted as online.
     */
    public synchronized boolean send(int sender, int target, String text) {
        prune();
        long now = clock.getAsLong();
        Station existing = stations.get(target);
        boolean online = existing != null && isOnline(existing, now);
        deliverOrQueue(station(target, now), new Message(sender, text));
        return online;
    }

    /**
     * Queues a message for every online computer except the sender.
     *
     * @return how many computers were reached
     */
    public synchronized int broadcast(int sender, String text) {
        prune();
        long now = clock.getAsLong();
        int reached = 0;
        for (Map.Entry<Integer, Station> entry : stations.entrySet()) {
            if (entry.getKey() == sender || !isOnline(entry.getValue(), now)) {
                continue;
            }
            deliverOrQueue(entry.getValue(), new Message(sender, text));
            reached++;
        }
        return reached;
    }

    /** Removes and returns the oldest queued message for the computer, or {@code null}. */
    public synchronized Message poll(int computerId) {
        prune();
        Station station = stations.get(computerId);
        if (station == null) {
            return null;
        }
        station.lastSeen = clock.getAsLong();
        station.touched = true;
        return station.inbox.poll();
    }

    /** Registers a waiting receive; the caller polls first, so the inbox is empty here. */
    public synchronized void await(int computerId, MessageSink sink) {
        prune();
        station(computerId, clock.getAsLong()).waiters.add(sink);
    }

    private boolean isOnline(Station station, long now) {
        return station.touched && now - station.lastSeen <= ONLINE_WINDOW_MS;
    }

    private Station station(int computerId, long now) {
        return stations.computeIfAbsent(computerId, id -> new Station(now));
    }

    private void deliverOrQueue(Station station, Message message) {
        Iterator<MessageSink> waiting = station.waiters.iterator();
        while (waiting.hasNext()) {
            MessageSink sink = waiting.next();
            boolean accepted = sink.deliver(message.sender(), message.text());
            waiting.remove();
            if (accepted) {
                return;
            }
        }
        station.inbox.addLast(message);
        while (station.inbox.size() > MAX_QUEUE) {
            station.inbox.removeFirst();
        }
    }

    private void prune() {
        long now = clock.getAsLong();
        stations.entrySet().removeIf(entry -> {
            Station station = entry.getValue();
            station.waiters.removeIf(MessageSink::stale);
            return now - station.lastSeen > STATION_TTL_MS;
        });
    }
}

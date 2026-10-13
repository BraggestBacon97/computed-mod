package dev.propulsionteam.computed.lua.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.net.ComputerNetwork.Message;
import dev.propulsionteam.computed.lua.net.ComputerNetwork.MessageSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ComputerNetworkTest {
    private final AtomicLong now = new AtomicLong(1_000);

    private ComputerNetwork network() {
        return new ComputerNetwork(now::get);
    }

    private void advance(long millis) {
        now.addAndGet(millis);
    }

    @Test
    void queuesMessagesForOfflineComputers() {
        ComputerNetwork network = network();

        assertFalse(network.send(1, 7, "hello"));
        assertFalse(network.isOnline(7));

        Message message = network.poll(7);
        assertNotNull(message);
        assertEquals(1, message.sender());
        assertEquals("hello", message.text());
        assertNull(network.poll(7));
    }

    @Test
    void touchesKeepAComputerOnlineOnlyWithinTheWindow() {
        ComputerNetwork network = network();
        network.touch(3);

        assertTrue(network.isOnline(3));
        advance(ComputerNetwork.ONLINE_WINDOW_MS + 1);
        assertFalse(network.isOnline(3));

        network.touch(3);
        assertTrue(network.isOnline(3));
    }

    @Test
    void sendReportsWhetherTheTargetIsOnline() {
        ComputerNetwork network = network();
        network.touch(7);

        assertTrue(network.send(1, 7, "hi"));
        assertEquals("hi", network.poll(7).text());
    }

    @Test
    void pollCountsAsActivity() {
        ComputerNetwork network = network();
        network.touch(4);
        network.send(1, 4, "first");
        advance(ComputerNetwork.ONLINE_WINDOW_MS + 1);
        network.poll(4);

        assertTrue(network.isOnline(4));
    }

    @Test
    void broadcastReachesOnlineComputersExceptTheSender() {
        ComputerNetwork network = network();
        network.touch(1);
        network.touch(2);
        network.touch(3);

        int reached = network.broadcast(1, "ping");

        assertEquals(2, reached);
        assertEquals("ping", network.poll(2).text());
        assertEquals("ping", network.poll(3).text());
        assertNull(network.poll(1));
    }

    @Test
    void waitingReceiversGetMessagesDirectly() {
        ComputerNetwork network = network();
        network.touch(5);
        AtomicReference<Message> delivered = new AtomicReference<>();
        network.await(5, recordingSink(delivered, false));

        assertTrue(network.send(9, 5, "direct"));

        assertNotNull(delivered.get());
        assertEquals(9, delivered.get().sender());
        assertEquals("direct", delivered.get().text());
        assertNull(network.poll(5));
    }

    @Test
    void staleWaitersAreSkippedAndRemoved() {
        ComputerNetwork network = network();
        AtomicReference<Message> staleDelivered = new AtomicReference<>();
        AtomicReference<Message> freshDelivered = new AtomicReference<>();
        network.await(5, recordingSink(staleDelivered, true));
        network.await(5, recordingSink(freshDelivered, false));

        network.send(9, 5, "x");

        assertNull(staleDelivered.get());
        assertNotNull(freshDelivered.get());
        assertEquals("x", freshDelivered.get().text());
        assertNull(network.poll(5));
    }

    @Test
    void queueOverflowDropsTheOldestMessage() {
        ComputerNetwork network = network();
        for (int index = 0; index <= ComputerNetwork.MAX_QUEUE; index++) {
            network.send(1, 4, "m" + index);
        }

        assertEquals("m1", network.poll(4).text());
    }

    @Test
    void idleStationsArePrunedAfterTheTtl() {
        ComputerNetwork network = network();
        network.send(1, 8, "held");

        advance(ComputerNetwork.STATION_TTL_MS + 1);
        network.send(1, 9, "trigger");

        assertNull(network.poll(8));
    }

    @Test
    void activeStationsSurviveTheTtl() {
        ComputerNetwork network = network();
        network.touch(8);
        network.send(1, 8, "held");

        advance(ComputerNetwork.STATION_TTL_MS + 1);
        network.touch(8);
        network.send(1, 9, "trigger");

        assertNotNull(network.poll(8));
    }

    @Test
    void waitingReceivesSurviveWhileTheComputerTicks() {
        ComputerNetwork network = network();
        AtomicReference<Message> delivered = new AtomicReference<>();
        network.await(6, recordingSink(delivered, false));
        network.touch(6);

        for (int tick = 0; tick < 5; tick++) {
            advance(ComputerNetwork.STATION_TTL_MS);
            network.touch(6);
        }
        network.send(1, 6, "late");

        assertNotNull(delivered.get());
        assertEquals("late", delivered.get().text());
    }

    @Test
    void waitingReceivesAreReleasedWhenTheStationGoesIdle() {
        ComputerNetwork network = network();
        AtomicReference<Message> delivered = new AtomicReference<>();
        network.await(6, recordingSink(delivered, false));
        network.touch(6);

        advance(ComputerNetwork.STATION_TTL_MS + 1);
        network.send(1, 9, "trigger");
        network.send(1, 6, "after unload");

        assertNull(delivered.get());
        assertEquals("after unload", network.poll(6).text());
    }

    private static MessageSink recordingSink(AtomicReference<Message> delivered, boolean stale) {
        return new MessageSink() {
            @Override
            public boolean deliver(int sender, String text) {
                if (stale) {
                    return false;
                }
                delivered.set(new Message(sender, text));
                return true;
            }

            @Override
            public boolean stale() {
                return stale;
            }
        };
    }
}

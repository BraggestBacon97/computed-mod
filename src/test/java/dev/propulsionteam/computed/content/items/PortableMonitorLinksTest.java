package dev.propulsionteam.computed.content.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PortableMonitorLinksTest {
    private static PortableLinkData link(int x) {
        return new PortableLinkData(x, 64, -x, "minecraft:overworld");
    }

    @Test
    void writeAndReadRoundTrips() {
        CompoundTag tag = new CompoundTag();
        PortableMonitorLinks.writeList(tag, List.of(link(1), link(2)), 1);

        assertEquals(List.of(link(1), link(2)), PortableMonitorLinks.readList(tag));
        assertEquals(1, PortableMonitorLinks.readActive(tag));
    }

    @Test
    void legacySingleLinkMigrates() {
        CompoundTag tag = new CompoundTag();
        tag.put(PortableMonitorLinks.LEGACY_KEY, link(5).toTag());

        assertEquals(List.of(link(5)), PortableMonitorLinks.readList(tag));
        assertEquals(0, PortableMonitorLinks.readActive(tag));

        PortableMonitorLinks.writeList(tag, PortableMonitorLinks.readList(tag), 0);
        assertFalse(tag.contains(PortableMonitorLinks.LEGACY_KEY), "legacy key removed after write");
    }

    @Test
    void activeIndexIsClamped() {
        CompoundTag tag = new CompoundTag();
        PortableMonitorLinks.writeList(tag, List.of(link(1), link(2)), 99);
        assertEquals(1, PortableMonitorLinks.readActive(tag));

        PortableMonitorLinks.writeList(tag, List.of(link(1)), -4);
        assertEquals(0, PortableMonitorLinks.readActive(tag));
    }

    @Test
    void addAppendsAndReusesDuplicates() {
        PortableMonitorLinks.AddResult first = PortableMonitorLinks.add(List.of(), link(1));
        assertEquals(0, first.index());

        PortableMonitorLinks.AddResult second = PortableMonitorLinks.add(first.links(), link(2));
        assertEquals(1, second.index());

        PortableMonitorLinks.AddResult again = PortableMonitorLinks.add(second.links(), link(1));
        assertEquals(0, again.index(), "identical link reuses its channel");
        assertEquals(2, again.links().size(), "no duplicate entry");
    }

    @Test
    void addRejectsBeyondMaxChannels() {
        List<PortableLinkData> full = java.util.stream.IntStream.range(0, PortableMonitorLinks.MAX_CHANNELS)
                .mapToObj(PortableMonitorLinksTest::link)
                .toList();
        PortableMonitorLinks.AddResult result = PortableMonitorLinks.add(full, link(99));
        assertEquals(-1, result.index());
        assertEquals(PortableMonitorLinks.MAX_CHANNELS, result.links().size());
    }

    @Test
    void removeDropsOnlyThatChannel() {
        List<PortableLinkData> links = List.of(link(1), link(2), link(3));
        List<PortableLinkData> next = PortableMonitorLinks.remove(links, link(2));
        assertEquals(List.of(link(1), link(3)), next);
        assertEquals(next, PortableMonitorLinks.remove(next, link(7)),
                "removing an absent link keeps the list unchanged");
    }

    @Test
    void emptyTagReadsEmptyList() {
        assertTrue(PortableMonitorLinks.readList(new CompoundTag()).isEmpty());
        assertEquals(0, PortableMonitorLinks.readActive(new CompoundTag()));
    }
}

package dev.propulsionteam.computed.persistence;

import dev.propulsionteam.computed.graph.*;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.io.*;
import java.util.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;
import net.minecraft.nbt.NbtAccounter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestNbtRoundTrip {
    @Test
    public void testRoundTripViaReflection() throws Exception {
        UUID graphId = new UUID(0L, 1L);
        UUID tickId = new UUID(0L, 2L);
        UUID counterId = new UUID(0L, 3L);
        UUID displayId = new UUID(0L, 4L);
        UUID conn1Id = new UUID(0L, 5L);
        UUID conn2Id = new UUID(0L, 6L);

        GraphNode tick = new GraphNode(
            tickId, "computed:tick", "bundled-hash", -120, 0,
            List.of(
                new PortSnapshot("tick", PortDirection.OUTPUT, ConnectionType.NUMBER, "Tick"),
                new PortSnapshot("delta", PortDirection.OUTPUT, ConnectionType.NUMBER, "Delta Time")
            ),
            Map.of("rate", tagged("type", "number", "value", 20.0))
        );
        GraphNode counter = new GraphNode(
            counterId, "computed:counter", "bundled-hash", 0, 0,
            List.of(
                new PortSnapshot("increment", PortDirection.INPUT, ConnectionType.NUMBER, "Increment"),
                new PortSnapshot("count", PortDirection.OUTPUT, ConnectionType.NUMBER, "Count")
            ),
            Map.of("step", tagged("type", "number", "value", 1.0))
        );
        GraphNode display = new GraphNode(
            displayId, "computed:display", "bundled-hash", 120, 0,
            List.of(new PortSnapshot("value", PortDirection.INPUT, ConnectionType.NUMBER, "Value")),
            Map.of()
        );

        ComputedProgramV3 program = new ComputedProgramV3(
            1L,
            new ComputedGraph(graphId, List.of(tick, counter, display),
                List.of(
                    new GraphConnection(conn1Id, tickId, "tick", counterId, "increment", List.of()),
                    new GraphConnection(conn2Id, counterId, "count", displayId, "value", List.of())
                )),
            Map.of(),
            Map.of(),
            new CompoundTag()
        );

        // Encode using the mod's codec
        CompoundTag encoded = ProgramV3Codec.encode(program);

        // Binary NBT IO is only available when the real Minecraft classes are on the test classpath.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                classExists("net.minecraft.nbt.NbtIo"),
                "NbtIo is unavailable in stubbed unit tests");

        // Use reflection to access NbtIo
        Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
        java.lang.reflect.Method writeMethod = nbtIoClass.getMethod("write", CompoundTag.class, DataOutput.class);
        java.lang.reflect.Method readMethod = nbtIoClass.getMethod("read", DataInput.class, NbtAccounter.class);
        
        // Encode to bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        writeMethod.invoke(null, encoded, dos);
        byte[] nbtBytes = baos.toByteArray();
        
        System.out.println("=== Serialized NBT (" + nbtBytes.length + " bytes) ===");
        for (int i = 0; i < nbtBytes.length; i++) {
            if (i % 16 == 0) System.out.printf("\n%04x: ", i);
            System.out.printf("%02x ", nbtBytes[i]);
        }
        System.out.println();
        
        // Now read it back
        ByteArrayInputStream bais = new ByteArrayInputStream(nbtBytes);
        DataInputStream dis = new DataInputStream(bais);
        CompoundTag decoded = (CompoundTag) readMethod.invoke(null, dis, NbtAccounter.unlimitedHeap());
        
        // Verify round-trip
        ProgramV3Codec.LoadResult result = ProgramV3Codec.decode(decoded, "test", null);
        assertNotNull(result);
        assertFalse(result.discardedLegacy());
        assertEquals(3, decoded.getInt("formatVersion"));
        assertEquals(1, result.program().revision());
        assertEquals(3, result.program().rootGraph().nodes().size());
        assertEquals(2, result.program().rootGraph().connections().size());
        
        System.out.println("\n=== Round-trip successful! ===");
        System.out.println("Nodes: " + result.program().rootGraph().nodes().size());
        System.out.println("Connections: " + result.program().rootGraph().connections().size());
        
        // Dump NBT structure
        for (String key : decoded.getAllKeys()) {
            Tag value = decoded.get(key);
            System.out.println(key + ": " + value.getClass().getSimpleName() + " (id=" + value.getId() + ")");
        }
        
        CompoundTag graph = decoded.getCompound("graph");
        System.out.println("graph.id UUID: " + graph.getUUID("id"));
        
        ListTag nodes = graph.getList("nodes", Tag.TAG_COMPOUND);
        System.out.println("graph.nodes size: " + nodes.size());
        
        ListTag connections = graph.getList("connections", Tag.TAG_COMPOUND);
        System.out.println("graph.connections size: " + connections.size());
        
        CompoundTag conn0 = connections.getCompound(0);
        ListTag waypoints = conn0.getList("waypoints", Tag.TAG_COMPOUND);
        System.out.println("conn0.waypoints size: " + waypoints.size());
        System.out.println("conn0.waypoints elementType: " + getElementTypeViaReflection(waypoints));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    private static byte getElementTypeViaReflection(ListTag list) {
        try {
            java.lang.reflect.Field typeField = ListTag.class.getDeclaredField("type");
            typeField.setAccessible(true);
            return typeField.getByte(list);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field typeField = ListTag.class.getDeclaredField("f_7982_");
                typeField.setAccessible(true);
                return typeField.getByte(list);
            } catch (Exception e2) {
                return -1;
            }
        }
    }

    private static CompoundTag tagged(String k1, String v1, String k2, double v2) {
        CompoundTag t = new CompoundTag();
        t.putString(k1, v1);
        t.putDouble(k2, v2);
        return t;
    }
}

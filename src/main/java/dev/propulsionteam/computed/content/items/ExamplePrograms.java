package dev.propulsionteam.computed.content.items;

import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import dev.propulsionteam.computed.persistence.ComputedPackageStorage;
import dev.propulsionteam.computed.persistence.ComputedProgramPackage;
import dev.propulsionteam.computed.persistence.PackageTransferService;
import dev.propulsionteam.computed.persistence.ProgramDiskData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;

/**
 * Pre-built demo program offered as a written disk in the creative tab so players can load a
 * working widget showcase without building the graph by hand.
 */
public final class ExamplePrograms {
    private static final LuaStateCodec CODEC = new LuaStateCodec();
    private static final String DEMO_NAME = "widget_demo.computed";

    private ExamplePrograms() {}

    /** Creative-tab stack holding the widget demo graph package. */
    public static ItemStack widgetDemoDisk() {
        ItemStack stack = new ItemStack(ComputedRegistries.PROGRAM_DISK_ITEM.get());
        ProgramDiskItem.write(stack, widgetDemoPackage());
        return stack;
    }

    /** Encoded demo package (panel + clock + button + checkbox + text input + text/terminal). */
    public static ProgramDiskData widgetDemoPackage() {
        Map<String, LuaDefinitionSource> bundled = BundledLuaLibrary.load();
        LuaDefinitionSource textSource = require(bundled, "computed:text_source");
        LuaDefinitionSource panel = require(bundled, "computed:panel_widget");
        LuaDefinitionSource clock = require(bundled, "computed:clock_widget");
        LuaDefinitionSource button = require(bundled, "computed:button_widget");
        LuaDefinitionSource checkbox = require(bundled, "computed:checkbox_widget");
        LuaDefinitionSource textInput = require(bundled, "computed:text_input_widget");
        LuaDefinitionSource textBlock = require(bundled, "computed:text_block_widget");
        LuaDefinitionSource monitor = require(bundled, "computed:peripheral");

        UUID titleTextId = uuid("title-text");
        UUID panelId = uuid("panel");
        UUID clockId = uuid("clock");
        UUID buttonId = uuid("button");
        UUID checkboxId = uuid("checkbox");
        UUID textInputId = uuid("text-input");
        UUID blockId = uuid("text-block");
        UUID monitorId = uuid("monitor");

        GraphNode titleText = new GraphNode(
                titleTextId,
                textSource.id(),
                textSource.hash(),
                -80,
                -80,
                List.of(out("text", ConnectionType.STRING)),
                Map.of("text", CODEC.encode(LuaValue.valueOf("Winnie's widget demo"))));
        GraphNode panelNode = new GraphNode(
                panelId,
                panel.id(),
                panel.hash(),
                160,
                -160,
                List.of(
                        in("color", ConnectionType.NUMBER),
                        in("border", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(1))));
        GraphNode clockNode = new GraphNode(
                clockId,
                clock.id(),
                clock.hash(),
                160,
                -100,
                List.of(
                        in("color", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(2))));
        GraphNode buttonNode = new GraphNode(
                buttonId,
                button.id(),
                button.hash(),
                160,
                -40,
                List.of(
                        in("label", ConnectionType.STRING),
                        in("color", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET),
                        out("clicked", ConnectionType.BOOLEAN)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(3))));
        GraphNode checkboxNode = new GraphNode(
                checkboxId,
                checkbox.id(),
                checkbox.hash(),
                160,
                20,
                List.of(
                        in("label", ConnectionType.STRING),
                        in("color", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET),
                        out("checked", ConnectionType.BOOLEAN)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(4))));
        GraphNode textInputNode = new GraphNode(
                textInputId,
                textInput.id(),
                textInput.hash(),
                160,
                80,
                List.of(
                        in("placeholder", ConnectionType.STRING),
                        in("color", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET),
                        out("value", ConnectionType.STRING),
                        out("submitted", ConnectionType.BOOLEAN)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(5))));
        GraphNode blockNode = new GraphNode(
                blockId,
                textBlock.id(),
                textBlock.hash(),
                160,
                140,
                List.of(
                        in("text", ConnectionType.STRING),
                        in("fg", ConnectionType.NUMBER),
                        in("bg", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", CODEC.encode(LuaValue.valueOf(6))));
        GraphNode monitorNode = new GraphNode(
                monitorId,
                monitor.id(),
                monitor.hash(),
                400,
                0,
                List.of(
                        widgetIn(1),
                        widgetIn(2),
                        widgetIn(3),
                        widgetIn(4),
                        widgetIn(5),
                        widgetIn(6)),
                Map.of());

        List<GraphConnection> connections = List.of(
                edge(panelId, "widget", monitorId, "widget_1"),
                edge(clockId, "widget", monitorId, "widget_2"),
                edge(buttonId, "widget", monitorId, "widget_3"),
                edge(checkboxId, "widget", monitorId, "widget_4"),
                edge(textInputId, "widget", monitorId, "widget_5"),
                edge(blockId, "widget", monitorId, "widget_6"));

        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(
                        uuid("graph"),
                        List.of(titleText, panelNode, clockNode, buttonNode, checkboxNode, textInputNode, blockNode, monitorNode),
                        connections),
                Map.of(),
                Map.of(),
                new CompoundTag());

        byte[] archive = ComputedProgramPackage.exportArchive(program);
        return new ProgramDiskData(
                PackageTransferService.id(ComputedPackageStorage.Kind.GRAPHS),
                DEMO_NAME,
                archive);
    }

    private static LuaDefinitionSource require(Map<String, LuaDefinitionSource> bundled, String id) {
        LuaDefinitionSource source = bundled.get(id);
        if (source == null) {
            throw new IllegalStateException("Missing bundled definition " + id);
        }
        return source;
    }

    private static PortSnapshot in(String id, ConnectionType type) {
        return new PortSnapshot(id, PortDirection.INPUT, type, id);
    }

    private static PortSnapshot out(String id, ConnectionType type) {
        return new PortSnapshot(id, PortDirection.OUTPUT, type, id);
    }

    private static PortSnapshot widgetIn(int index) {
        return new PortSnapshot(
                "widget_" + index, PortDirection.INPUT, ConnectionType.WIDGET, "Widget " + index);
    }

    private static GraphConnection edge(UUID source, String sourcePort, UUID target, String targetPort) {
        return new GraphConnection(uuid(source + "/" + sourcePort), source, sourcePort, target, targetPort, List.of());
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(("computed:widget-demo:" + seed).getBytes(StandardCharsets.UTF_8));
    }
}

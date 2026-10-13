package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.LuaGraphScheduler;
import dev.propulsionteam.computed.graph.LuaGraphTickResult;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinWidget;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

/**
 * Builds the widget demo graph shipped for in-game testing, exports it as a {@code .computed}
 * package, re-imports it, and runs it headless to prove the graph delivers panel, clock, terminal,
 * and text-block widgets to a monitor.
 */
class WidgetDemoGraphPackageTest {
    private final LuaStateCodec codec = new LuaStateCodec();

    @Test
    void exportsRunnableWidgetDemoGraph() throws IOException {
        Map<String, LuaDefinitionSource> bundled = BundledLuaLibrary.load();
        LuaDefinitionSource textSource = require(bundled, "computed:text_source");
        LuaDefinitionSource panel = require(bundled, "computed:panel_widget");
        LuaDefinitionSource clock = require(bundled, "computed:clock_widget");
        LuaDefinitionSource terminal = require(bundled, "computed:terminal_widget");
        LuaDefinitionSource textBlock = require(bundled, "computed:text_block_widget");
        LuaDefinitionSource monitor = require(bundled, "computed:peripheral");

        UUID terminalTextId = uuid("terminal-text");
        UUID blockTextId = uuid("block-text");
        UUID panelId = uuid("panel");
        UUID clockId = uuid("clock");
        UUID terminalId = uuid("terminal");
        UUID blockId = uuid("text-block");
        UUID monitorId = uuid("monitor");

        GraphNode terminalText = new GraphNode(
                terminalTextId,
                textSource.id(),
                textSource.hash(),
                -60,
                -60,
                List.of(out("text", ConnectionType.STRING)),
                Map.of("text", codec.encode(LuaValue.valueOf("Line 3: terminal says hi"))));
        GraphNode blockText = new GraphNode(
                blockTextId,
                textSource.id(),
                textSource.hash(),
                -60,
                20,
                List.of(out("text", ConnectionType.STRING)),
                Map.of("text", codec.encode(LuaValue.valueOf("Line 4: text block works"))));
        GraphNode panelNode = new GraphNode(
                panelId,
                panel.id(),
                panel.hash(),
                160,
                -140,
                List.of(
                        in("color", ConnectionType.NUMBER),
                        in("border", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", codec.encode(LuaValue.valueOf(1))));
        GraphNode clockNode = new GraphNode(
                clockId,
                clock.id(),
                clock.hash(),
                160,
                -60,
                List.of(
                        in("color", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", codec.encode(LuaValue.valueOf(2))));
        GraphNode terminalNode = new GraphNode(
                terminalId,
                terminal.id(),
                terminal.hash(),
                160,
                20,
                List.of(
                        in("text", ConnectionType.STRING),
                        in("fg", ConnectionType.NUMBER),
                        in("bg", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", codec.encode(LuaValue.valueOf(3))));
        GraphNode blockNode = new GraphNode(
                blockId,
                textBlock.id(),
                textBlock.hash(),
                160,
                100,
                List.of(
                        in("text", ConnectionType.STRING),
                        in("fg", ConnectionType.NUMBER),
                        in("bg", ConnectionType.NUMBER),
                        out("widget", ConnectionType.WIDGET)),
                Map.of("line", codec.encode(LuaValue.valueOf(4))));
        GraphNode monitorNode = new GraphNode(
                monitorId,
                monitor.id(),
                monitor.hash(),
                380,
                -60,
                List.of(
                        widgetIn(1),
                        widgetIn(2),
                        widgetIn(3),
                        widgetIn(4)),
                Map.of());

        List<GraphConnection> connections = List.of(
                edge(terminalTextId, "text", terminalId, "text"),
                edge(blockTextId, "text", blockId, "text"),
                edge(panelId, "widget", monitorId, "widget_1"),
                edge(clockId, "widget", monitorId, "widget_2"),
                edge(terminalId, "widget", monitorId, "widget_3"),
                edge(blockId, "widget", monitorId, "widget_4"));

        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(
                        uuid("graph"),
                        List.of(terminalText, blockText, panelNode, clockNode, terminalNode, blockNode, monitorNode),
                        connections),
                Map.of(),
                Map.of(),
                new CompoundTag());

        byte[] archive = ComputedProgramPackage.exportArchive(program);
        Path output = Path.of("build", "test-graphs", "widget_test.computed");
        Files.createDirectories(output.getParent());
        Files.write(output, archive);

        ComputedProgramV3 imported = ComputedProgramPackage.importArchive(Files.readAllBytes(output));
        assertEquals(7, imported.rootGraph().nodes().size());
        assertEquals(6, imported.rootGraph().connections().size());

        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(imported, uuid("scheduler"), host);
        LuaGraphTickResult first = scheduler.tick(false);
        assertTrue(first.diagnostics().isEmpty(), () -> first.diagnostics().toString());
        assertTrue(host.hasTasks());
        host.runNext();

        assertEquals(List.of("front"), host.monitorTargets);
        List<BuiltinWidget> widgets = host.monitorWidgets.getFirst();
        assertEquals(4, widgets.size());
        assertEquals("panel", widgets.get(0).type());
        assertEquals("clock", widgets.get(1).type());
        assertEquals("terminal", widgets.get(2).type());
        assertEquals("text_block", widgets.get(3).type());
        assertEquals(panelId, widgets.get(0).id());
        assertEquals(clockId, widgets.get(1).id());
        assertEquals(terminalId, widgets.get(2).id());
        assertEquals(blockId, widgets.get(3).id());
        Object rows = widgets.get(2).properties().get("rows");
        assertEquals(1, ((List<?>) rows).size());
    }

    private static LuaDefinitionSource require(Map<String, LuaDefinitionSource> bundled, String id) {
        LuaDefinitionSource source = bundled.get(id);
        assertNotNull(source, "Missing bundled definition " + id);
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

    private static final class Host implements BuiltinEndpointHost, ServerEndpointExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final List<String> monitorTargets = new ArrayList<>();
        private final List<List<BuiltinWidget>> monitorWidgets = new ArrayList<>();

        @Override
        public double worldTime() {
            return 6000;
        }

        @Override
        public void runCommand(String command) {}

        @Override
        public void showWidgets(String target, List<BuiltinWidget> widgets) {
            monitorTargets.add(target);
            monitorWidgets.add(widgets);
        }

        @Override
        public CompletionStage<EndpointResult> submitServerEndpoint(Callable<EndpointResult> endpointCall) {
            CompletableFuture<EndpointResult> result = new CompletableFuture<>();
            synchronized (tasks) {
                tasks.addLast(() -> {
                    try {
                        result.complete(endpointCall.call());
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    }
                });
            }
            return result;
        }

        boolean hasTasks() {
            synchronized (tasks) {
                return !tasks.isEmpty();
            }
        }

        void runNext() {
            Runnable task;
            synchronized (tasks) {
                task = tasks.pollFirst();
            }
            task.run();
        }
    }
}

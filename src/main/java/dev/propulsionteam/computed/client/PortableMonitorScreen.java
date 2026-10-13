package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.client.renderer.MonitorWidgetPainter;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import dev.propulsionteam.computed.network.MonitorClickPayload;
import dev.propulsionteam.computed.network.PullPortableDisplayPayload;
import dev.propulsionteam.computed.network.SetPortableSourcePayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Windowed overlay for the portable monitor: a large centered 16:9 panel with a live mirror of the
 * selected channel's computer widgets. Multiple linked computers show as channel tabs above the
 * display; clicking a tab (or arrow keys) switches the source. No container menu — frames are
 * pulled from the server while open, and ESC closes it while the game keeps running.
 */
public class PortableMonitorScreen extends Screen {
    private static final int TAB_W = 24;
    private static final int TAB_H = 12;
    private static final int TAB_GAP = 2;

    private final List<BlockPos> sources;
    private int activeIndex;
    private BlockPos computerPos;
    private String computerName = "";

    private WidgetDrawList drawList = WidgetDrawList.EMPTY;
    private long lastSentHash;
    private long lastPullTick = Long.MIN_VALUE;

    private int dispX;
    private int dispY;
    private int dispW;
    private int dispH;
    private int tabsX;
    private int tabsY;
    private int hoveredTab = -1;

    public PortableMonitorScreen(List<BlockPos> sources, int activeIndex) {
        super(Component.translatable("item.computed.portable_monitor"));
        this.sources = sources.isEmpty() ? List.of() : List.copyOf(sources);
        this.activeIndex = clampActive(this.sources, activeIndex);
        this.computerPos = this.sources.isEmpty() ? BlockPos.ZERO : this.sources.get(this.activeIndex);
    }

    /** Backward-compatible single-source constructor. */
    public PortableMonitorScreen(BlockPos computerPos) {
        this(List.of(computerPos), 0);
    }

    private static int clampActive(List<BlockPos> sources, int active) {
        if (sources.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(active, sources.size() - 1));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int maxW = width - 40;
        int maxH = height - 90;
        dispW = Math.max(178, Math.min(maxW, maxH * 16 / 9));
        dispH = Math.max(100, dispW * 9 / 16);
        dispX = (width - dispW) / 2;
        dispY = (height - dispH) / 2 - 8;
        int totalTabsW = sources.size() * TAB_W + (sources.size() - 1) * TAB_GAP;
        tabsX = (width - totalTabsW) / 2;
        tabsY = dispY - 20;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || sources.isEmpty()) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (lastPullTick != Long.MIN_VALUE && gameTime - lastPullTick < 15L) {
            return;
        }
        lastPullTick = gameTime;
        PacketDistributor.sendToServer(new PullPortableDisplayPayload(computerPos, lastSentHash));
    }

    /** Applies a frame pushed by {@code PortableDisplayBridge} for this screen's active computer. */
    public void onDisplay(BlockPos pos, byte[] bytes, String name) {
        if (!computerPos.equals(pos)) {
            return;
        }
        computerName = name == null ? "" : name;
        try {
            drawList = WidgetDrawList.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
            lastSentHash = drawList.hash();
            Computed.LOGGER.info(
                    "Portable display client: frame received ({} widgets, hash={})",
                    drawList.widgets().size(), drawList.hash());
        } catch (RuntimeException exception) {
            Computed.LOGGER.warn("Discarding damaged portable display frame", exception);
            drawList = WidgetDrawList.EMPTY;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sources.size() > 1) {
            if (keyCode == 263) { // GLFW_KEY_LEFT
                switchTo((activeIndex + sources.size() - 1) % sources.size());
                return true;
            }
            if (keyCode == 262) { // GLFW_KEY_RIGHT
                switchTo((activeIndex + 1) % sources.size());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (sources.size() > 1) {
                int tab = tabAt(mouseX, mouseY);
                if (tab >= 0) {
                    switchTo(tab);
                    return true;
                }
            }
            if (!drawList.isEmpty() && clickDisplay(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Maps a click inside the letterboxed display to canvas (u, v) and forwards it to the server
     * widget dispatch (same flow as wall-monitor clicks).
     */
    private boolean clickDisplay(double mouseX, double mouseY) {
        if (mouseX < dispX || mouseX >= dispX + dispW || mouseY < dispY || mouseY >= dispY + dispH) {
            return false;
        }
        double scale = Math.min(
                dispW / (double) PortableMonitorItem.CANVAS_W,
                dispH / (double) PortableMonitorItem.CANVAS_H);
        double offX = dispX + (dispW - PortableMonitorItem.CANVAS_W * scale) / 2.0;
        double offY = dispY + (dispH - PortableMonitorItem.CANVAS_H * scale) / 2.0;
        double px = (mouseX - offX) / scale;
        double py = (mouseY - offY) / scale;
        if (px < 0 || px >= PortableMonitorItem.CANVAS_W || py < 0 || py >= PortableMonitorItem.CANVAS_H) {
            return false;
        }
        PacketDistributor.sendToServer(new MonitorClickPayload(
                computerPos,
                px / PortableMonitorItem.CANVAS_W,
                py / PortableMonitorItem.CANVAS_H));
        return true;
    }

    /** Switches the displayed channel, resets the frame cache, and persists the selection. */
    private void switchTo(int index) {
        if (index < 0 || index >= sources.size() || index == activeIndex) {
            return;
        }
        activeIndex = index;
        computerPos = sources.get(index);
        computerName = "";
        drawList = WidgetDrawList.EMPTY;
        lastSentHash = 0;
        lastPullTick = Long.MIN_VALUE;
        PacketDistributor.sendToServer(new SetPortableSourcePayload(index));
    }

    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < tabsY || mouseY >= tabsY + TAB_H) {
            return -1;
        }
        for (int index = 0; index < sources.size(); index++) {
            int x0 = tabsX + index * (TAB_W + TAB_GAP);
            if (mouseX >= x0 && mouseX < x0 + TAB_W) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        hoveredTab = tabAt(mouseX, mouseY);

        graphics.fill(dispX - 4, dispY - 4, dispX + dispW + 4, dispY + dispH + 4, 0xF0141418);
        graphics.fill(dispX - 2, dispY - 2, dispX + dispW + 2, dispY + dispH + 2, 0xFF3E3E46);
        graphics.fill(dispX, dispY, dispX + dispW, dispY + dispH, 0xFF000000);
        renderDisplay(graphics, dispX, dispY, dispW, dispH);

        if (sources.size() > 1) {
            renderTabs(graphics, mouseX, mouseY);
        }

        Component status = statusText();
        graphics.drawString(font, status, (width - font.width(status)) / 2, dispY + dispH + 12,
                statusColor(), false);
        Component signalHint = signalHint();
        if (signalHint != null) {
            graphics.drawString(font, signalHint, (width - font.width(signalHint)) / 2,
                    dispY + dispH + 24, 0x909098, false);
        }
        Component switchHint = sources.size() > 1
                ? Component.translatable("item.computed.portable_monitor.switch_hint")
                : null;
        if (switchHint != null) {
            graphics.drawString(font, switchHint, (width - font.width(switchHint)) / 2,
                    height - 30, 0x808088, false);
        }
        Component closeHint = Component.translatable("item.computed.portable_monitor.close_hint");
        graphics.drawString(font, closeHint, (width - font.width(closeHint)) / 2, height - 20,
                0x808088, false);

        if (hoveredTab >= 0) {
            MutableComponent label = computerName.isEmpty()
                    ? coordsComponent(sources.get(hoveredTab))
                    : Component.literal(computerName).append(" — ").append(coordsComponent(sources.get(hoveredTab)));
            MutableComponent tooltip = Component.translatable(
                    "item.computed.portable_monitor.channel_tooltip", hoveredTab + 1, label);
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** Draws the channel tab strip above the display. */
    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < sources.size(); index++) {
            int x0 = tabsX + index * (TAB_W + TAB_GAP);
            boolean active = index == activeIndex;
            boolean hover = index == hoveredTab;
            int background = active ? 0xFF3A6A48 : hover ? 0xFF35353C : 0xFF2A2A30;
            graphics.fill(x0, tabsY, x0 + TAB_W, tabsY + TAB_H, background);
            graphics.fill(x0, tabsY, x0 + TAB_W, tabsY + 1, active ? 0xFF60C060 : 0xFF505058);
            graphics.fill(x0, tabsY + TAB_H - 1, x0 + TAB_W, tabsY + TAB_H, 0xFF1A1A1E);
            graphics.fill(x0, tabsY, x0 + 1, tabsY + TAB_H, 0xFF505058);
            graphics.fill(x0 + TAB_W - 1, tabsY, x0 + TAB_W, tabsY + TAB_H, 0xFF1A1A1E);
            Component label = Component.literal(String.valueOf(index + 1));
            graphics.drawString(font, label, x0 + (TAB_W - font.width(label)) / 2,
                    tabsY + (TAB_H - 8) / 2, active ? 0xE0FFE0 : 0xB0B0B8, false);
        }
    }

    /**
     * Renders the current frame, letterboxed into the 16:9 area at x/y. The GUI projection has no
     * world depth, so all widget layers share one matrix (vanilla GUI draws via render order only).
     */
    private void renderDisplay(GuiGraphics graphics, int x, int y, int w, int h) {
        if (drawList.isEmpty()) {
            return;
        }
        float scale = Math.min(
                w / (float) PortableMonitorItem.CANVAS_W, h / (float) PortableMonitorItem.CANVAS_H);
        int offX = x + Math.round((w - PortableMonitorItem.CANVAS_W * scale) / 2f);
        int offY = y + Math.round((h - PortableMonitorItem.CANVAS_H * scale) / 2f);
        graphics.pose().pushPose();
        graphics.pose().translate(offX, offY, 0f);
        Matrix4f base = new Matrix4f(graphics.pose().last().pose()).scale(scale, scale, 1f);
        MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        try {
            for (Widget widget : drawList.widgets()) {
                MonitorWidgetPainter.renderWidget(widget, base, base, base, base, base,
                        bufferSource, font);
            }
        } finally {
            graphics.flush();
            graphics.pose().popPose();
        }
    }

    private static MutableComponent coordsComponent(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** Channel badge + computer label (name, or coordinates when unnamed). */
    private MutableComponent channelLabel() {
        MutableComponent base = computerName.isEmpty()
                ? coordsComponent(computerPos)
                : Component.literal(computerName + " ").append(coordsComponent(computerPos));
        if (sources.size() > 1) {
            base = Component.literal("[" + (activeIndex + 1) + "/" + sources.size() + "] ").append(base);
        }
        return base;
    }

    private Component statusText() {
        if (drawList.isEmpty()) {
            return Component.translatable("item.computed.portable_monitor.no_signal", channelLabel());
        }
        return Component.translatable("item.computed.portable_monitor.live", channelLabel())
                .append(Component.literal(" (" + drawList.widgets().size() + " widgets)"));
    }

    /** Extra hint line under the status, or {@code {@code null}}. */
    private Component signalHint() {
        return drawList.isEmpty()
                ? Component.translatable("item.computed.portable_monitor.no_signal.hint")
                : null;
    }

    private int statusColor() {
        return drawList.isEmpty() ? 0xD0A040 : 0x60C060;
    }
}

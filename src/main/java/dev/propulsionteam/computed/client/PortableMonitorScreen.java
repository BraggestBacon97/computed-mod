package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.client.renderer.MonitorWidgetPainter;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import dev.propulsionteam.computed.network.PullPortableDisplayPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Windowed overlay for the portable monitor: a large centered 16:9 panel with a live mirror of the
 * linked computer's widgets. No container menu — frames are pulled from the server while open, and
 * ESC closes it while the game keeps running.
 */
public class PortableMonitorScreen extends Screen {
    private final BlockPos computerPos;

    private WidgetDrawList drawList = WidgetDrawList.EMPTY;
    private long lastSentHash;
    private long lastPullTick = Long.MIN_VALUE;

    private int dispX;
    private int dispY;
    private int dispW;
    private int dispH;

    public PortableMonitorScreen(BlockPos computerPos) {
        super(Component.translatable("item.computed.portable_monitor"));
        this.computerPos = computerPos;
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
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (lastPullTick != Long.MIN_VALUE && gameTime - lastPullTick < 15L) {
            return;
        }
        lastPullTick = gameTime;
        PacketDistributor.sendToServer(new PullPortableDisplayPayload(computerPos, lastSentHash));
    }

    /** Applies a frame pushed by {@code PortableDisplayBridge} for this screen's computer. */
    public void onDisplay(BlockPos pos, byte[] bytes) {
        if (!computerPos.equals(pos)) {
            return;
        }
        try {
            drawList = WidgetDrawList.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
            lastSentHash = drawList.hash();
        } catch (RuntimeException exception) {
            Computed.LOGGER.warn("Discarding damaged portable display frame", exception);
            drawList = WidgetDrawList.EMPTY;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(dispX - 4, dispY - 4, dispX + dispW + 4, dispY + dispH + 4, 0xF0141418);
        graphics.fill(dispX - 2, dispY - 2, dispX + dispW + 2, dispY + dispH + 2, 0xFF3E3E46);
        graphics.fill(dispX, dispY, dispX + dispW, dispY + dispH, 0xFF000000);
        renderDisplay(graphics, dispX, dispY, dispW, dispH);

        Component status = statusText();
        graphics.drawString(font, status, (width - font.width(status)) / 2, dispY + dispH + 12,
                statusColor(), false);
        Component signalHint = signalHint();
        if (signalHint != null) {
            graphics.drawString(font, signalHint, (width - font.width(signalHint)) / 2,
                    dispY + dispH + 24, 0x909098, false);
        }
        Component closeHint = Component.translatable("item.computed.portable_monitor.close_hint");
        graphics.drawString(font, closeHint, (width - font.width(closeHint)) / 2, height - 20,
                0x808088, false);
    }

    /** Renders the current frame, letterboxed into the 16:9 area at x/y. */
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
        Matrix4f underfillMat = MonitorWidgetPainter.layer(base, 0.0025f);
        Matrix4f fillMat = MonitorWidgetPainter.layer(base, 0.0030f);
        Matrix4f edgeMat = MonitorWidgetPainter.layer(base, 0.0040f);
        Matrix4f detailMat = MonitorWidgetPainter.layer(base, 0.0045f);
        Matrix4f textMat = MonitorWidgetPainter.layer(base, 0.0050f);
        MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        for (Widget widget : drawList.widgets()) {
            MonitorWidgetPainter.renderWidget(widget, underfillMat, fillMat, edgeMat, detailMat, textMat,
                    bufferSource, font);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private Component statusText() {
        if (drawList.isEmpty()) {
            return Component.translatable("item.computed.portable_monitor.no_signal",
                    computerPos.getX(), computerPos.getY(), computerPos.getZ());
        }
        return Component.translatable("item.computed.portable_monitor.live",
                computerPos.getX(), computerPos.getY(), computerPos.getZ());
    }

    /** Extra hint line under the status, or {@code null}. */
    private Component signalHint() {
        return drawList.isEmpty()
                ? Component.translatable("item.computed.portable_monitor.no_signal.hint")
                : null;
    }

    private int statusColor() {
        return drawList.isEmpty() ? 0xD0A040 : 0x60C060;
    }
}

package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** Flat rectangle widget used as panel/background decoration on a monitor. */
public record PanelWidget(UUID id, int x, int y, int w, int h, int colorArgb, int borderArgb)
        implements Widget {

    @Override
    public WidgetType type() {
        return WidgetType.PANEL;
    }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeInt(colorArgb);
        buf.writeInt(borderArgb);
    }

    public static PanelWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        int color = buf.readInt();
        int border = buf.readInt();
        return new PanelWidget(h.id(), h.x(), h.y(), h.w(), h.h(), color, border);
    }
}

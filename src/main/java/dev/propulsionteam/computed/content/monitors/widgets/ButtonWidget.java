package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ButtonWidget(UUID id, int x, int y, int w, int h, String label, int colorArgb) implements Widget {
    @Override public WidgetType type() { return WidgetType.BUTTON; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeUtf(label == null ? "" : label, 256);
        buf.writeInt(colorArgb);
    }

    public static ButtonWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        String label = buf.readUtf(256);
        int color = buf.readInt();
        return new ButtonWidget(h.id(), h.x(), h.y(), h.w(), h.h(), label, color);
    }
}

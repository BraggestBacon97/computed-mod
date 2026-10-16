package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record CheckboxWidget(
        UUID id, int x, int y, int w, int h, String label, int colorArgb, boolean checked)
        implements Widget {
    @Override public WidgetType type() { return WidgetType.CHECKBOX; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeUtf(label == null ? "" : label, 256);
        buf.writeInt(colorArgb);
        buf.writeBoolean(checked);
    }

    public static CheckboxWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        String label = buf.readUtf(256);
        int color = buf.readInt();
        boolean checked = buf.readBoolean();
        return new CheckboxWidget(h.id(), h.x(), h.y(), h.w(), h.h(), label, color, checked);
    }
}

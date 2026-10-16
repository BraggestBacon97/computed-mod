package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record NumberWidget(UUID id, int x, int y, int w, int h, double value, String text,
                           int colorArgb, TextAlignment alignment) implements Widget {
    public NumberWidget {
        alignment = alignment == null ? TextAlignment.RIGHT : alignment;
    }

    @Override public WidgetType type() { return WidgetType.NUMBER; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeDouble(value);
        buf.writeUtf(text == null ? "" : text, 4096);
        buf.writeInt(colorArgb);
        buf.writeEnum(alignment);
    }

    public static NumberWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        double value = buf.readDouble();
        String text = buf.readUtf(4096);
        int color = buf.readInt();
        TextAlignment alignment = buf.readEnum(TextAlignment.class);
        return new NumberWidget(h.id(), h.x(), h.y(), h.w(), h.h(), value, text, color, alignment);
    }
}

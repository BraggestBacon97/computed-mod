package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record TextWidget(UUID id, int x, int y, int w, int h, String text, int colorArgb,
                         TextAlignment alignment) implements Widget {
    public TextWidget {
        alignment = alignment == null ? TextAlignment.CENTER : alignment;
    }

    @Override public WidgetType type() { return WidgetType.TEXT; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeUtf(text == null ? "" : text, 4096);
        buf.writeInt(colorArgb);
        buf.writeEnum(alignment);
    }

    public static TextWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        String text = buf.readUtf(4096);
        int color = buf.readInt();
        TextAlignment alignment = buf.readEnum(TextAlignment.class);
        return new TextWidget(h.id(), h.x(), h.y(), h.w(), h.h(), text, color, alignment);
    }
}

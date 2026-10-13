package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Multiline text widget with its own background. Lines are separated by {@code \n}; each line is
 * aligned individually and the whole block auto-shrinks to fit the widget bounds.
 */
public record TextBlockWidget(UUID id, int x, int y, int w, int h, String text, int fgArgb, int bgArgb,
                              TextAlignment alignment)
        implements Widget {

    public TextBlockWidget {
        text = text == null ? "" : text;
        alignment = alignment == null ? TextAlignment.LEFT : alignment;
    }

    @Override
    public WidgetType type() {
        return WidgetType.TEXT_BLOCK;
    }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeUtf(text, 4096);
        buf.writeInt(fgArgb);
        buf.writeInt(bgArgb);
        buf.writeEnum(alignment);
    }

    public static TextBlockWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        String text = buf.readUtf(4096);
        int fg = buf.readInt();
        int bg = buf.readInt();
        TextAlignment alignment = buf.readEnum(TextAlignment.class);
        return new TextBlockWidget(h.id(), h.x(), h.y(), h.w(), h.h(), text, fg, bg, alignment);
    }
}

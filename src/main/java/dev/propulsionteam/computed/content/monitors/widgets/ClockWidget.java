package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** Renders the in-world time of day. {@code showSeconds} chooses HH:MM vs HH:MM:SS. */
public record ClockWidget(UUID id, int x, int y, int w, int h, int colorArgb, boolean showSeconds,
                          TextAlignment alignment) implements Widget {
    public ClockWidget {
        alignment = alignment == null ? TextAlignment.CENTER : alignment;
    }

    @Override public WidgetType type() { return WidgetType.CLOCK; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeInt(colorArgb);
        buf.writeBoolean(showSeconds);
        buf.writeEnum(alignment);
    }

    public static ClockWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        int color = buf.readInt();
        boolean showSec = buf.readBoolean();
        TextAlignment alignment = buf.readEnum(TextAlignment.class);
        return new ClockWidget(h.id(), h.x(), h.y(), h.w(), h.h(), color, showSec, alignment);
    }
}

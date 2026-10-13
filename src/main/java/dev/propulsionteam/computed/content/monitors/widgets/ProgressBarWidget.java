package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ProgressBarWidget(
        UUID id, int x, int y, int w, int h,
        double value, double max, int colorArgb, int segments) implements Widget {
    @Override public WidgetType type() { return WidgetType.PROGRESS_BAR; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeDouble(value);
        buf.writeDouble(max);
        buf.writeInt(colorArgb);
        buf.writeInt(segments);
    }

    public static ProgressBarWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        double v = buf.readDouble();
        double m = buf.readDouble();
        int color = buf.readInt();
        int segs = buf.readInt();
        return new ProgressBarWidget(h.id(), h.x(), h.y(), h.w(), h.h(), v, m, color, segs);
    }
}

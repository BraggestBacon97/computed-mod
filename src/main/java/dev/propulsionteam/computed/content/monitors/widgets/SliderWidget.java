package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record SliderWidget(
        UUID id, int x, int y, int w, int h,
        double value, double min, double max, int colorArgb, double step) implements Widget {
    @Override public WidgetType type() { return WidgetType.SLIDER; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeDouble(value);
        buf.writeDouble(min);
        buf.writeDouble(max);
        buf.writeInt(colorArgb);
        buf.writeDouble(step);
    }

    public static SliderWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        double v = buf.readDouble();
        double lo = buf.readDouble();
        double hi = buf.readDouble();
        int color = buf.readInt();
        double step = buf.readDouble();
        return new SliderWidget(h.id(), h.x(), h.y(), h.w(), h.h(), v, lo, hi, color, step);
    }
}

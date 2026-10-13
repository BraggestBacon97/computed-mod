package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Widget plus authoring-time layout metadata. Peripheral nodes resolve this to a raw widget before sync.
 */
public record LayoutManagedWidget(Widget raw, LayoutMode mode, int line, double scale, Fit fit) implements Widget {
    public enum LayoutMode {
        MANUAL,
        LINE
    }

    public enum Fit {
        AUTO,
        FILL
    }

    public LayoutManagedWidget {
        if (raw instanceof LayoutManagedWidget managed) {
            raw = managed.raw();
        }
        mode = mode == null ? LayoutMode.MANUAL : mode;
        line = Math.max(1, line);
        scale = Math.max(1.0, Math.round(scale));
        fit = fit == null ? Fit.AUTO : fit;
    }

    public boolean isLineLayout() {
        return mode == LayoutMode.LINE;
    }

    @Override public UUID id() { return raw.id(); }
    @Override public int x() { return raw.x(); }
    @Override public int y() { return raw.y(); }
    @Override public int w() { return raw.w(); }
    @Override public int h() { return raw.h(); }
    @Override public WidgetType type() { return raw.type(); }
    @Override public void encodeBody(FriendlyByteBuf buf) { raw.encodeBody(buf); }
}

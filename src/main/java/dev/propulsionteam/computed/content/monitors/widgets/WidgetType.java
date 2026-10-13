package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

/** Enumerated widget kinds plus per-kind ByteBuf decoder. Order is wire-stable; only append. */
public enum WidgetType {
    TEXT(TextWidget::decode),
    CLOCK(ClockWidget::decode),
    BUTTON(ButtonWidget::decode),
    SLIDER(SliderWidget::decode),
    PROGRESS_BAR(ProgressBarWidget::decode),
    TERMINAL(TerminalWidget::decode),
    PANEL(PanelWidget::decode),
    TEXT_BLOCK(TextBlockWidget::decode),
    TABLE(TableWidget::decode);

    private final Decoder decoder;

    WidgetType(Decoder decoder) {
        this.decoder = decoder;
    }

    public Widget decode(FriendlyByteBuf buf) {
        return decoder.decode(buf);
    }

    public static WidgetType byOrdinal(int ord) {
        WidgetType[] vals = values();
        if (ord < 0 || ord >= vals.length) {
            throw new IllegalArgumentException("Unknown widget type ordinal: " + ord);
        }
        return vals[ord];
    }

    @FunctionalInterface
    private interface Decoder {
        Widget decode(FriendlyByteBuf buf);
    }
}

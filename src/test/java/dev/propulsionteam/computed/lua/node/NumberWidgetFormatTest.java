package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.client.editor.preview.LuaLivePreview;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

/**
 * The number widget must render integral values (colours, ticks, time, positions) exactly —
 * {@code %.4g} would show them as {@code 4.295e+09}. Fractional values use the format field.
 */
class NumberWidgetFormatTest {
    private static LuaLivePreview numberWidget(double value) {
        BuiltinEndpoints.register();
        String source = BundledLuaLibrary.load().get("computed:number_widget").source();
        LuaLivePreview preview = new LuaLivePreview(source);
        preview.setInput("value", LuaValue.valueOf(value));
        return preview;
    }

    private static String renderedText(LuaLivePreview preview) {
        var result = preview.run();
        assertEquals(
                dev.propulsionteam.computed.lua.runtime.LuaNodeStatus.IDLE,
                result.status(),
                () -> "number widget failed: " + result.diagnostics());
        return result.outputs().get("widget").checktable().get("text").tojstring();
    }

    @Test
    void rendersArgbColoursExactly() {
        assertEquals("4294967295", renderedText(numberWidget(4294967295.0)));
    }

    @Test
    void rendersWorldTimeWithoutScientificNotation() {
        assertEquals("18000", renderedText(numberWidget(18000.0)));
    }

    @Test
    void rendersNegativeIntegersExactly() {
        assertEquals("-64", renderedText(numberWidget(-64.0)));
    }

    @Test
    void formatsFractionsWithFullPrecision() {
        // The Lua runtime ignores printf precision, so fractions render unrounded —
        // same as the previous string.format behaviour, just without scientific notation.
        assertEquals("0.123456", renderedText(numberWidget(0.123456)));
    }

    @Test
    void honorsAnExplicitFormatEvenForIntegralValues() {
        LuaLivePreview preview = numberWidget(5.0);
        preview.setField("format", LuaValue.valueOf("%.2f"));
        // Explicit choice wins over the exact-integer path (precision itself is
        // ignored by the Lua runtime, matching string.format behaviour).
        assertEquals("5.0", renderedText(preview));
    }
}

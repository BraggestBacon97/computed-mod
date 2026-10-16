package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.client.editor.preview.LuaLivePreview;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.runtime.LuaInvocationResult;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

/**
 * The number input keeps the last valid value, ignores non-numeric submissions and raises a
 * one-tick {@code submitted} pulse only for valid numbers — on both monitor kinds, since it
 * renders through the shared text-input widget.
 */
class NumberInputWidgetTest {
    private static LuaLivePreview numberInput() {
        BuiltinEndpoints.register();
        String source = BundledLuaLibrary.load().get("computed:number_input_widget").source();
        return new LuaLivePreview(source);
    }

    @Test
    void startsAtZeroWithoutSubmit() {
        LuaLivePreview preview = numberInput();
        LuaInvocationResult result = preview.run();

        assertEquals(0.0, result.outputs().get("value").todouble());
        assertFalse(result.outputs().get("submitted").toboolean());
        assertEquals("0", result.outputs().get("widget").checktable().get("value").tojstring());
    }

    @Test
    void acceptsValidSubmissionsWithOneTickPulse() {
        LuaLivePreview preview = numberInput();

        LuaInvocationResult submitted = preview.event("input", LuaValue.valueOf("42"));
        assertEquals(42.0, submitted.outputs().get("value").todouble());
        assertTrue(submitted.outputs().get("submitted").toboolean());

        LuaInvocationResult next = preview.run();
        assertEquals(42.0, next.outputs().get("value").todouble());
        assertFalse(next.outputs().get("submitted").toboolean());
    }

    @Test
    void ignoresNonNumericSubmissions() {
        LuaLivePreview preview = numberInput();
        preview.event("input", LuaValue.valueOf("7"));

        LuaInvocationResult result = preview.event("input", LuaValue.valueOf("abc"));
        assertEquals(7.0, result.outputs().get("value").todouble());
        assertFalse(result.outputs().get("submitted").toboolean());
    }

    @Test
    void acceptsSignedDecimals() {
        LuaLivePreview preview = numberInput();

        LuaInvocationResult result = preview.event("input", LuaValue.valueOf("-3.5"));
        assertEquals(-3.5, result.outputs().get("value").todouble());
        assertTrue(result.outputs().get("submitted").toboolean());
    }
}

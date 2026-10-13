package dev.propulsionteam.computed.content.monitors.widgets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.content.monitors.widgets.TerminalWidget.TerminalRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TerminalWidgetTest {
    @Test
    void resolvesPlainStringRowsWithWidgetDefaults() {
        List<TerminalRow> rows = TerminalWidget.resolveRows(
                List.of("ab"), 0xFF112233, 0xFF445566);

        assertEquals(1, rows.size());
        assertEquals("ab", rows.getFirst().text());
        assertArrayEquals(new int[] {0xFF112233, 0xFF112233}, rows.getFirst().fg());
        assertArrayEquals(new int[] {0xFF445566, 0xFF445566}, rows.getFirst().bg());
    }

    @Test
    void resolvesStyledRowsAndBlitColorStrings() {
        Map<String, Object> styled = new LinkedHashMap<>();
        styled.put("text", "xy");
        styled.put("fg", 0xFF00FF00);
        styled.put("bg", 0xFF010203);
        Map<String, Object> blit = new LinkedHashMap<>();
        blit.put("text", "ab");
        blit.put("fg", "f0");
        blit.put("bg", "5");

        List<TerminalRow> rows = TerminalWidget.resolveRows(
                List.of(styled, blit), 0xFFFFFFFF, 0xFF000000);

        assertEquals(2, rows.size());
        assertArrayEquals(new int[] {0xFF00FF00, 0xFF00FF00}, rows.getFirst().fg());
        assertArrayEquals(new int[] {0xFF010203, 0xFF010203}, rows.getFirst().bg());
        assertArrayEquals(
                new int[] {TerminalWidget.CC_PALETTE[15], TerminalWidget.CC_PALETTE[0]},
                rows.get(1).fg());
        assertArrayEquals(
                new int[] {TerminalWidget.CC_PALETTE[5], 0xFF000000},
                rows.get(1).bg());
    }

    @Test
    void clipsRowTextAndRowCounts() {
        List<Object> raw = new ArrayList<>();
        raw.add("x".repeat(TerminalWidget.MAX_COLUMNS + 10));
        for (int index = 0; index < TerminalWidget.MAX_ROWS + 5; index++) {
            raw.add("row");
        }

        List<TerminalRow> rows = TerminalWidget.resolveRows(raw, 0xFFFFFFFF, 0xFF000000);

        assertEquals(TerminalWidget.MAX_ROWS, rows.size());
        assertEquals(TerminalWidget.MAX_COLUMNS, rows.getFirst().text().length());
        assertEquals(TerminalWidget.MAX_COLUMNS, rows.getFirst().fg().length);
    }

    @Test
    void padsShortAndMissingColorArrays() {
        TerminalRow padded = new TerminalRow("abc", new int[] {0xFF00FF00}, null);

        assertArrayEquals(new int[] {0xFF00FF00, 0xFF00FF00, 0xFF00FF00}, padded.fg());
        assertArrayEquals(new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, padded.bg());
    }
}

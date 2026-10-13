package dev.propulsionteam.computed.content.monitors.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableWidgetTest {
    @Test
    void padsAndTrimsRowsToTheColumnCount() {
        TableWidget table = new TableWidget(
                UUID.randomUUID(), 0, 0, 100, 50,
                List.of("a", "b"),
                List.of(List.of("1"), List.of("2", "3", "ignored")),
                0xFFFFFFFF, 0xFF202020, 0xFFFFFFFF, 0xFF000000);

        assertEquals(List.of("1", ""), table.rows().getFirst());
        assertEquals(List.of("2", "3"), table.rows().get(1));
    }

    @Test
    void clipsColumnsRowsAndCellsToLimits() {
        java.util.ArrayList<String> columns = new java.util.ArrayList<>();
        for (int index = 0; index < TableWidget.MAX_TABLE_COLUMNS + 4; index++) {
            columns.add("c" + index);
        }
        java.util.ArrayList<List<String>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < TableWidget.MAX_TABLE_ROWS + 4; index++) {
            rows.add(List.of("x".repeat(TableWidget.MAX_CELL_CHARS + 10)));
        }

        TableWidget table = new TableWidget(
                UUID.randomUUID(), 0, 0, 100, 50, columns, rows,
                0xFFFFFFFF, 0xFF202020, 0xFFFFFFFF, 0xFF000000);

        assertEquals(TableWidget.MAX_TABLE_COLUMNS, table.columns().size());
        assertEquals(TableWidget.MAX_TABLE_ROWS, table.rows().size());
        assertEquals(TableWidget.MAX_CELL_CHARS, table.rows().getFirst().getFirst().length());
    }

    @Test
    void dropsRowsWhenNoColumnsAreDeclared() {
        TableWidget table = new TableWidget(
                UUID.randomUUID(), 0, 0, 100, 50,
                List.of(),
                List.of(List.of("1")),
                0xFFFFFFFF, 0xFF202020, 0xFFFFFFFF, 0xFF000000);

        assertEquals(List.of(), table.columns());
        assertEquals(List.of(), table.rows());
    }
}

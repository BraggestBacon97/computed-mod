package dev.propulsionteam.computed.content.monitors.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Table widget with a header row and data rows. Cells are plain strings; the renderer lays them
 * out on an equal-width grid and clips content to the widget bounds.
 */
public record TableWidget(
        UUID id,
        int x,
        int y,
        int w,
        int h,
        List<String> columns,
        List<List<String>> rows,
        int headerFg,
        int headerBg,
        int cellFg,
        int cellBg)
        implements Widget {

    public static final int MAX_TABLE_COLUMNS = 16;
    public static final int MAX_TABLE_ROWS = 64;
    public static final int MAX_CELL_CHARS = 64;

    public TableWidget {
        columns = normalizeColumns(columns);
        rows = normalizeRows(columns, rows);
    }

    @Override
    public WidgetType type() {
        return WidgetType.TABLE;
    }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeInt(headerFg);
        buf.writeInt(headerBg);
        buf.writeInt(cellFg);
        buf.writeInt(cellBg);
        buf.writeVarInt(columns.size());
        for (String column : columns) {
            buf.writeUtf(column, MAX_CELL_CHARS);
        }
        buf.writeVarInt(rows.size());
        for (List<String> row : rows) {
            buf.writeVarInt(row.size());
            for (String cell : row) {
                buf.writeUtf(cell, MAX_CELL_CHARS);
            }
        }
    }

    public static TableWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        int headerFg = buf.readInt();
        int headerBg = buf.readInt();
        int cellFg = buf.readInt();
        int cellBg = buf.readInt();
        int columnCount = buf.readVarInt();
        List<String> columns = new ArrayList<>(Math.min(columnCount, MAX_TABLE_COLUMNS));
        for (int index = 0; index < columnCount; index++) {
            String value = buf.readUtf(MAX_CELL_CHARS);
            if (columns.size() < MAX_TABLE_COLUMNS) {
                columns.add(value);
            }
        }
        int rowCount = buf.readVarInt();
        List<List<String>> rows = new ArrayList<>(Math.min(rowCount, MAX_TABLE_ROWS));
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int cellCount = buf.readVarInt();
            List<String> row = new ArrayList<>(Math.min(cellCount, MAX_TABLE_COLUMNS));
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                String value = buf.readUtf(MAX_CELL_CHARS);
                if (row.size() < MAX_TABLE_COLUMNS) {
                    row.add(value);
                }
            }
            if (rows.size() < MAX_TABLE_ROWS) {
                rows.add(List.copyOf(row));
            }
        }
        return new TableWidget(
                h.id(), h.x(), h.y(), h.w(), h.h(), columns, rows, headerFg, headerBg, cellFg, cellBg);
    }

    private static List<String> normalizeColumns(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int count = Math.min(source.size(), MAX_TABLE_COLUMNS);
        List<String> columns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            columns.add(clip(source.get(index)));
        }
        return List.copyOf(columns);
    }

    private static List<List<String>> normalizeRows(List<String> columns, List<List<String>> source) {
        if (source == null || source.isEmpty() || columns.isEmpty()) {
            return List.of();
        }
        int count = Math.min(source.size(), MAX_TABLE_ROWS);
        List<List<String>> rows = new ArrayList<>(count);
        for (int rowIndex = 0; rowIndex < count; rowIndex++) {
            List<String> sourceRow = source.get(rowIndex);
            List<String> row = new ArrayList<>(columns.size());
            for (int cellIndex = 0; cellIndex < columns.size(); cellIndex++) {
                row.add(cellIndex < sourceRow.size() ? clip(sourceRow.get(cellIndex)) : "");
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CELL_CHARS ? text.substring(0, MAX_CELL_CHARS) : text;
    }
}

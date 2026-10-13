package dev.propulsionteam.computed.content.monitors.widgets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Fixed-pitch terminal block for monitors. Rows carry fully resolved per-character ARGB colors so
 * rendering never interprets CC color strings, and the widget stays a plain stateless value like
 * every other widget in the draw list.
 */
public record TerminalWidget(
        UUID id,
        int x,
        int y,
        int w,
        int h,
        List<TerminalRow> rows,
        int defaultFg,
        int defaultBg,
        float textScale,
        int cursorX,
        int cursorY)
        implements Widget {

    public static final int MAX_ROWS = 64;
    public static final int MAX_COLUMNS = 256;

    /** ComputerCraft's basic 16-colour palette indexed by a {@code 0}–{@code f} blit character. */
    public static final int[] CC_PALETTE = {
        0xFF000000, 0xFFAA0000, 0xFF00AA00, 0xFFAAAA00,
        0xFF0000AA, 0xFFAA00AA, 0xFF00AAAA, 0xFFAAAAAA,
        0xFF555555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55,
        0xFF5555FF, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF
    };

    public TerminalWidget {
        rows = rows == null ? List.of() : List.copyOf(rows);
        if (rows.size() > MAX_ROWS) {
            rows = List.copyOf(rows.subList(0, MAX_ROWS));
        }
        if (!(textScale > 0f)) {
            textScale = 1f;
        }
    }

    @Override
    public WidgetType type() {
        return WidgetType.TERMINAL;
    }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeFloat(textScale);
        buf.writeInt(defaultFg);
        buf.writeInt(defaultBg);
        buf.writeVarInt(cursorX);
        buf.writeVarInt(cursorY);
        buf.writeVarInt(rows.size());
        for (TerminalRow row : rows) {
            buf.writeUtf(row.text(), MAX_COLUMNS);
            writeColorRuns(buf, row);
        }
    }

    public static TerminalWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        float scale = buf.readFloat();
        int defaultFg = buf.readInt();
        int defaultBg = buf.readInt();
        int cursorX = buf.readVarInt();
        int cursorY = buf.readVarInt();
        int rowCount = buf.readVarInt();
        List<TerminalRow> rows = new ArrayList<>(Math.min(rowCount, MAX_ROWS));
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            String text = buf.readUtf(MAX_COLUMNS);
            int[] fg = new int[text.length()];
            int[] bg = new int[text.length()];
            int column = 0;
            while (column < fg.length) {
                int run = buf.readVarInt();
                int runFg = buf.readInt();
                int runBg = buf.readInt();
                for (int i = 0; i < run && column < fg.length; i++) {
                    fg[column] = runFg;
                    bg[column] = runBg;
                    column++;
                }
            }
            if (rows.size() < MAX_ROWS) {
                rows.add(new TerminalRow(text, fg, bg));
            }
        }
        return new TerminalWidget(
                h.id(), h.x(), h.y(), h.w(), h.h(), rows, defaultFg, defaultBg, scale, cursorX, cursorY);
    }

    private static void writeColorRuns(FriendlyByteBuf buf, TerminalRow row) {
        int[] fg = row.fg();
        int[] bg = row.bg();
        int column = 0;
        while (column < fg.length) {
            int runFg = fg[column];
            int runBg = bg[column];
            int end = column + 1;
            while (end < fg.length && fg[end] == runFg && bg[end] == runBg) {
                end++;
            }
            buf.writeVarInt(end - column);
            buf.writeInt(runFg);
            buf.writeInt(runBg);
            column = end;
        }
    }

    /**
     * Resolves raw row values from the endpoint into per-character color arrays. Entries may be
     * plain strings (widget default colors), styled row tables with numeric ARGB colors, or styled
     * row tables with CC blit hex strings (one {@code 0}–{@code f} character per text column).
     */
    public static List<TerminalRow> resolveRows(Object rawRows, int defaultFg, int defaultBg) {
        if (!(rawRows instanceof List<?> entries)) {
            return List.of();
        }
        List<TerminalRow> rows = new ArrayList<>();
        for (Object entry : entries) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            String text;
            Integer rowFg = null;
            Integer rowBg = null;
            String blitFg = null;
            String blitBg = null;
            if (entry instanceof String line) {
                text = line;
            } else if (entry instanceof Map<?, ?> map) {
                Object value = map.get("text");
                text = value instanceof String line ? line : "";
                Object fg = map.get("fg");
                Object bg = map.get("bg");
                if (fg instanceof String hex) {
                    blitFg = hex;
                } else if (fg instanceof Integer color) {
                    rowFg = color;
                }
                if (bg instanceof String hex) {
                    blitBg = hex;
                } else if (bg instanceof Integer color) {
                    rowBg = color;
                }
            } else {
                continue;
            }
            if (text.length() > MAX_COLUMNS) {
                text = text.substring(0, MAX_COLUMNS);
            }
            int[] fg = blitFg != null
                    ? blitColors(blitFg, text, defaultFg)
                    : filledColors(text.length(), rowFg != null ? rowFg : defaultFg);
            int[] bg = blitBg != null
                    ? blitColors(blitBg, text, defaultBg)
                    : filledColors(text.length(), rowBg != null ? rowBg : defaultBg);
            rows.add(new TerminalRow(text, fg, bg));
        }
        return List.copyOf(rows);
    }

    private static int[] filledColors(int length, int color) {
        int[] colors = new int[length];
        Arrays.fill(colors, color);
        return colors;
    }

    private static int[] blitColors(String blit, String text, int fallback) {
        int[] colors = new int[text.length()];
        for (int column = 0; column < colors.length; column++) {
            colors[column] = column < blit.length() ? blitColor(blit.charAt(column), fallback) : fallback;
        }
        return colors;
    }

    private static int blitColor(char hex, int fallback) {
        int digit = switch (hex) {
            case '0' -> 0;
            case '1' -> 1;
            case '2' -> 2;
            case '3' -> 3;
            case '4' -> 4;
            case '5' -> 5;
            case '6' -> 6;
            case '7' -> 7;
            case '8' -> 8;
            case '9' -> 9;
            case 'a', 'A' -> 10;
            case 'b', 'B' -> 11;
            case 'c', 'C' -> 12;
            case 'd', 'D' -> 13;
            case 'e', 'E' -> 14;
            case 'f', 'F' -> 15;
            default -> -1;
        };
        return digit < 0 ? fallback : CC_PALETTE[digit];
    }

    /** One terminal line with resolved per-character ARGB color arrays. */
    public record TerminalRow(String text, int[] fg, int[] bg) {
        public TerminalRow {
            text = text == null ? "" : text;
            fg = normalize(fg, text.length());
            bg = normalize(bg, text.length());
        }

        private static int[] normalize(int[] colors, int length) {
            if (colors != null && colors.length == length) {
                return colors;
            }
            int[] resolved = new int[length];
            if (colors == null || colors.length == 0) {
                Arrays.fill(resolved, 0xFFFFFFFF);
                return resolved;
            }
            int copy = Math.min(colors.length, length);
            System.arraycopy(colors, 0, resolved, 0, copy);
            Arrays.fill(resolved, copy, length, colors[colors.length - 1]);
            return resolved;
        }
    }
}

package dev.propulsionteam.computed.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.propulsionteam.computed.content.monitors.widgets.ButtonWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ClockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.PanelWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ProgressBarWidget;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TableWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TerminalWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextAlignment;
import dev.propulsionteam.computed.content.monitors.widgets.TextBlockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Shared widget painter for monitor block rendering and the portable monitor screen. All
 * coordinates are in widget pixels ({@code 64 px = one monitor block}); callers supply the five
 * depth-layer matrices so fill/edge/detail/text quads never z-fight.
 */
public final class MonitorWidgetPainter {
    private MonitorWidgetPainter() {}

    /** Pushes the background this far forward of the block face so it doesn't z-fight. */
    public static Matrix4f layer(Matrix4f mat, float forwardOffset) {
        return new Matrix4f(mat).translate(0f, 0f, -forwardOffset);
    }

    public static void renderWidget(
            Widget w,
            Matrix4f underfillMat,
            Matrix4f fillMat,
            Matrix4f edgeMat,
            Matrix4f detailMat,
            Matrix4f textMat,
            MultiBufferSource bufferSource,
            Font font) {
        VertexConsumer gui = bufferSource.getBuffer(RenderType.gui());
        if (w instanceof TextWidget tw) {
            outline(edgeMat, gui, tw.x(), tw.y(), tw.x() + tw.w(), tw.y() + tw.h(), 0xFFFFFFFF);
            drawText(font, textMat, bufferSource, tw.text(), tw.x(), tw.y(), tw.w(), tw.h(),
                    tw.colorArgb(), tw.alignment());
        } else if (w instanceof ClockWidget cw) {
            long time = Minecraft.getInstance().level == null
                    ? 0L
                    : Minecraft.getInstance().level.getDayTime();
            String text = formatTime(time, cw.showSeconds());
            outline(edgeMat, gui, cw.x(), cw.y(), cw.x() + cw.w(), cw.y() + cw.h(), 0xFFFFFFFF);
            drawText(font, textMat, bufferSource, text, cw.x(), cw.y(), cw.w(), cw.h(),
                    cw.colorArgb(), cw.alignment());
        } else if (w instanceof ButtonWidget bw) {
            fill(fillMat, gui, bw.x(), bw.y(), bw.x() + bw.w(), bw.y() + bw.h(), bw.colorArgb());
            outline(edgeMat, gui, bw.x(), bw.y(), bw.x() + bw.w(), bw.y() + bw.h(), 0xFFFFFFFF);
            drawCenteredText(font, textMat, bufferSource, bw.label(), bw.x(), bw.y(), bw.w(), bw.h(),
                    contrastText(bw.colorArgb()));
        } else if (w instanceof SliderWidget sw) {
            int barY1 = sw.y() + sw.h() / 2 - 2;
            int barY2 = sw.y() + sw.h() / 2 + 2;
            fill(fillMat, gui, sw.x(), barY1, sw.x() + sw.w(), barY2, 0xFF202020);
            outline(edgeMat, gui, sw.x(), sw.y(), sw.x() + sw.w(), sw.y() + sw.h(), 0xFFFFFFFF);
            double range = sw.max() - sw.min();
            int handleX;
            if (sw.step() > 0 && range > 0) {
                int ticks = (int) Math.floor(range / sw.step() + 1.0e-6);
                if (ticks > 0 && ticks <= sw.w()) {
                    for (int i = 0; i <= ticks; i++) {
                        double t = Mth.clamp((i * sw.step()) / range, 0.0, 1.0);
                        int tx = sw.x() + (int) Math.round(t * sw.w());
                        fill(detailMat, gui, tx, barY1, tx + 1, barY2, 0xFFFFFFFF);
                    }
                }
                int snappedIdx = (int) Math.round((sw.value() - sw.min()) / sw.step());
                double t = Mth.clamp((snappedIdx * sw.step()) / range, 0.0, 1.0);
                handleX = sw.x() + (int) Math.round(t * sw.w());
            } else {
                double norm = range == 0 ? 0 : (sw.value() - sw.min()) / range;
                handleX = sw.x() + (int) (Mth.clamp(norm, 0.0, 1.0) * sw.w());
            }
            fill(detailMat, gui, handleX - 3, sw.y(), handleX + 3, sw.y() + sw.h(), sw.colorArgb());
        } else if (w instanceof ProgressBarWidget pb) {
            double frac = pb.max() <= 0 ? 0 : Mth.clamp(pb.value() / pb.max(), 0.0, 1.0);
            outline(edgeMat, gui, pb.x(), pb.y(), pb.x() + pb.w(), pb.y() + pb.h(), 0xFFFFFFFF);
            int segs = pb.segments();
            if (segs <= 0) {
                int fillEnd = pb.x() + (int) (frac * pb.w());
                fill(underfillMat, gui, pb.x(), pb.y(), fillEnd, pb.y() + pb.h(), pb.colorArgb());
            } else {
                int litSegs = (int) Math.floor(frac * segs + 1.0e-6);
                int innerY = pb.y() + 2;
                int innerH = Math.max(0, pb.h() - 4);
                int innerW = Math.max(0, pb.w() - 4);
                int gap = segs > 1 ? Math.min(2, Math.max(0, (innerW - segs) / Math.max(1, segs - 1))) : 0;
                int segW = segs > 0 ? Math.max(1, (innerW - gap * (segs - 1)) / segs) : 0;
                int contentW = segW * segs + gap * (segs - 1);
                int leftPad = Math.max(0, (innerW - contentW) / 2);
                int startX = pb.x() + 2 + leftPad;
                for (int i = 0; i < litSegs; i++) {
                    int sx = startX + i * (segW + gap);
                    fill(underfillMat, gui, sx, innerY, sx + segW, innerY + innerH, pb.colorArgb());
                }
            }
        } else if (w instanceof TerminalWidget tw) {
            long gameTime = Minecraft.getInstance().level == null
                    ? 0L
                    : Minecraft.getInstance().level.getGameTime();
            renderTerminal(tw, fillMat, detailMat, textMat, bufferSource, font, gameTime % 40 < 20);
        } else if (w instanceof PanelWidget panel) {
            fill(fillMat, gui, panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + panel.h(),
                    panel.colorArgb());
            outline(edgeMat, gui, panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + panel.h(),
                    panel.borderArgb());
        } else if (w instanceof TextBlockWidget tb) {
            fill(fillMat, gui, tb.x(), tb.y(), tb.x() + tb.w(), tb.y() + tb.h(), tb.bgArgb());
            drawTextBlock(font, textMat, bufferSource, tb.text(), tb.x(), tb.y(), tb.w(), tb.h(),
                    tb.fgArgb(), tb.alignment());
        } else if (w instanceof TableWidget table) {
            renderTable(table, fillMat, edgeMat, textMat, bufferSource, font);
        }
    }

    /**
     * Draws the terminal as a fixed-pitch character grid: base background first, then per-cell
     * background overrides, per-character text in resolved colors, and a blinking cursor underline.
     */
    private static void renderTerminal(TerminalWidget tw, Matrix4f fillMat, Matrix4f detailMat, Matrix4f textMat,
                                       MultiBufferSource bufferSource, Font font, boolean cursorVisible) {
        VertexConsumer gui = bufferSource.getBuffer(RenderType.gui());
        fill(fillMat, gui, tw.x(), tw.y(), tw.x() + tw.w(), tw.y() + tw.h(), tw.defaultBg());
        float scale = tw.textScale();
        int cellW = Math.max(1, Math.round(font.width("M") * scale));
        int cellH = Math.max(1, Math.round(font.lineHeight * scale));
        int cols = Math.max(1, tw.w() / cellW);
        int maxRows = Math.max(1, tw.h() / cellH);
        int rowsToDraw = Math.min(tw.rows().size(), maxRows);
        for (int row = 0; row < rowsToDraw; row++) {
            TerminalWidget.TerminalRow line = tw.rows().get(row);
            int rowY = tw.y() + row * cellH;
            int len = Math.min(line.text().length(), cols);
            for (int col = 0; col < len; col++) {
                int background = line.bg()[col];
                if (background != tw.defaultBg()) {
                    fill(fillMat, gui, tw.x() + col * cellW, rowY,
                            tw.x() + (col + 1) * cellW, rowY + cellH, background);
                }
            }
            for (int col = 0; col < len; col++) {
                char character = line.text().charAt(col);
                if (character == ' ' || character == '\t') {
                    continue;
                }
                float cellX = tw.x() + col * cellW + (cellW - font.width(String.valueOf(character)) * scale) / 2f;
                float cellY = rowY + (cellH - font.lineHeight * scale) / 2f;
                Matrix4f charMat = new Matrix4f(textMat)
                        .translate(cellX, cellY, 0f)
                        .scale(scale, scale, 1f);
                font.drawInBatch(String.valueOf(character), 0, 0, line.fg()[col], false, charMat, bufferSource,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }
        if (!cursorVisible || tw.cursorX() < 0 || tw.cursorY() < 0
                || tw.cursorX() >= cols || tw.cursorY() >= maxRows) {
            return;
        }
        int cursorColor = tw.defaultFg();
        if (tw.cursorY() < rowsToDraw) {
            TerminalWidget.TerminalRow line = tw.rows().get(tw.cursorY());
            if (tw.cursorX() < line.text().length()) {
                cursorColor = line.fg()[tw.cursorX()];
            }
        }
        int cursorX = tw.x() + tw.cursorX() * cellW;
        int cursorY = tw.y() + tw.cursorY() * cellH;
        int underline = Math.max(2, cellH / 4);
        fill(detailMat, gui, cursorX, cursorY + cellH - underline, cursorX + cellW, cursorY + cellH, cursorColor);
    }

    /** Draws newline-separated text lines, each individually aligned, auto-shrunk to the bounds. */
    private static void drawTextBlock(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                      int x, int y, int w, int h, int colorArgb, TextAlignment alignment) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\n", -1);
        int pad = 2;
        int availableW = Math.max(1, w - pad * 2);
        int availableH = Math.max(1, h - pad * 2);
        int longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, font.width(line));
        }
        float scale = Math.min(1f, Math.min(
                availableW / (float) Math.max(1, longest),
                availableH / (float) Math.max(1, lines.length * font.lineHeight)));
        int lineH = Math.max(1, Math.round(font.lineHeight * scale));
        int blockH = lineH * lines.length;
        float startY = y + (h - blockH) / 2f;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                continue;
            }
            int textW = Math.round(font.width(line) * scale);
            float tx = switch (alignment) {
                case LEFT -> x + pad;
                case CENTER -> x + (w - textW) / 2f;
                case RIGHT -> x + w - pad - textW;
            };
            float ty = startY + index * lineH;
            Matrix4f lineMat = new Matrix4f(mat).translate(tx, ty, 0f).scale(scale, scale, 1f);
            font.drawInBatch(line, 0, 0, colorArgb, false, lineMat, bufferSource,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
    }

    /** Draws the header band, equal-width column grid, and clipped cell text of a table widget. */
    private static void renderTable(TableWidget table, Matrix4f fillMat, Matrix4f edgeMat, Matrix4f textMat,
                                    MultiBufferSource bufferSource, Font font) {
        VertexConsumer gui = bufferSource.getBuffer(RenderType.gui());
        int x = table.x();
        int y = table.y();
        int w = table.w();
        int h = table.h();
        fill(fillMat, gui, x, y, x + w, y + h, table.cellBg());
        outline(edgeMat, gui, x, y, x + w, y + h, table.cellFg());
        List<String> columns = table.columns();
        if (columns.isEmpty() || h <= 2) {
            return;
        }
        int headerH = Math.min(h, Math.max(10, font.lineHeight + 4));
        fill(fillMat, gui, x, y, x + w, y + headerH, table.headerBg());
        int colW = Math.max(1, w / columns.size());
        for (int column = 1; column < columns.size(); column++) {
            int separator = x + column * colW;
            if (separator < x + w) {
                fill(edgeMat, gui, separator, y, separator + 1, y + h, table.cellFg());
            }
        }
        fill(edgeMat, gui, x, y + headerH - 1, x + w, y + headerH, table.cellFg());
        for (int column = 0; column < columns.size(); column++) {
            drawTableCell(font, textMat, bufferSource, columns.get(column),
                    x + column * colW + 2, y + 2, colW - 4, headerH - 4, table.headerFg());
        }
        int rowTop = y + headerH;
        int rowCount = table.rows().size();
        if (rowCount == 0) {
            return;
        }
        int rowH = Math.max(1, (y + h - rowTop) / rowCount);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int rowY = rowTop + rowIndex * rowH;
            if (rowY + 1 < y + h) {
                fill(edgeMat, gui, x, rowY, x + w, rowY + 1, table.cellFg());
            }
            List<String> cells = table.rows().get(rowIndex);
            for (int column = 0; column < columns.size() && column < cells.size(); column++) {
                drawTableCell(font, textMat, bufferSource, cells.get(column),
                        x + column * colW + 2, rowY + 2, colW - 4, rowH - 4, table.cellFg());
            }
        }
    }

    private static void drawTableCell(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                      int x, int y, int w, int h, int colorArgb) {
        if (text == null || text.isEmpty() || w <= 0 || h <= 0) {
            return;
        }
        float scale = Math.min(1f, Math.min(
                w / (float) Math.max(1, font.width(text)),
                h / (float) font.lineHeight));
        Matrix4f cellMat = new Matrix4f(mat)
                .translate(x, y + (h - font.lineHeight * scale) / 2f, 0f)
                .scale(scale, scale, 1f);
        font.drawInBatch(text, 0, 0, colorArgb, false, cellMat, bufferSource,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    /**
     * Picks black or white text for best contrast on the given ARGB button background,
     * so light buttons no longer render white-on-white.
     */
    private static int contrastText(int backgroundArgb) {
        return relativeLuminance(backgroundArgb) > 0.5d ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double relativeLuminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0.2126d * linearize(r) + 0.7152d * linearize(g) + 0.0722d * linearize(b);
    }

    private static double linearize(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }

    private static String formatTime(long dayTime, boolean withSeconds) {
        long t = ((dayTime + 6000) % 24000 + 24000) % 24000;
        double hourF = t / 1000.0;
        int hours = (int) hourF;
        int minutes = (int) ((hourF - hours) * 60);
        if (!withSeconds) return String.format("%02d:%02d", hours, minutes);
        int seconds = (int) ((((hourF - hours) * 60) - minutes) * 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    static void fill(Matrix4f mat, VertexConsumer vc, int x1, int y1, int x2, int y2, int argb) {
        emitQuad(mat, vc, x1, y1, x2, y2, argb);
    }

    private static void emitQuad(Matrix4f mat, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ((argb      ) & 0xFF) / 255f;
        if (a == 0f) return;
        vc.addVertex(mat, x1, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, 0).setColor(r, g, b, a);
    }

    private static void outline(Matrix4f mat, VertexConsumer vc, int x1, int y1, int x2, int y2, int argb) {
        fill(mat, vc, x1, y1, x2, y1 + 1, argb);
        fill(mat, vc, x1, y2 - 1, x2, y2, argb);
        fill(mat, vc, x1, y1, x1 + 1, y2, argb);
        fill(mat, vc, x2 - 1, y1, x2, y2, argb);
    }

    private static void drawText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                 int x, int y, int w, int h, int colorArgb) {
        drawText(font, mat, bufferSource, text, x, y, w, h, colorArgb, TextAlignment.LEFT);
    }

    private static void drawCenteredText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                         int x, int y, int w, int h, int colorArgb) {
        drawText(font, mat, bufferSource, text, x, y, w, h, colorArgb, TextAlignment.CENTER);
    }

    private static void drawText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                 int x, int y, int w, int h, int colorArgb, TextAlignment alignment) {
        if (text == null || text.isEmpty()) return;
        String shown = text;
        int pad = Math.min(2, Math.max(0, w / 8));
        int availableW = Math.max(1, w - pad * 2);
        int availableH = Math.max(1, h - pad * 2);
        float scale = Math.min(1.0f, Math.min(availableW / (float) Math.max(1, font.width(shown)),
                availableH / (float) font.lineHeight));
        int textW = Math.round(font.width(shown) * scale);
        int textH = Math.round(font.lineHeight * scale);
        float tx = switch (alignment == null ? TextAlignment.CENTER : alignment) {
            case LEFT -> x + pad;
            case CENTER -> x + (w - textW) / 2f;
            case RIGHT -> x + w - pad - textW;
        };
        float ty = y + (h - textH) / 2f;
        Matrix4f textMat = new Matrix4f(mat).translate(tx, ty, 0f).scale(scale, scale, 1f);
        font.drawInBatch(shown, 0, 0, colorArgb, false, textMat, bufferSource,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}

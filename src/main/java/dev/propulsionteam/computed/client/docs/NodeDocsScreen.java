package dev.propulsionteam.computed.client.docs;

import dev.propulsionteam.computed.client.ComputerEditorScreen;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * In-game node reference browser opened from the editor's Docs button. Left pane: searchable list
 * grouped by category. Right pane: summary, ports, a Lua example, and an Import button that loads
 * a minimal runnable graph for the selected node into the editor.
 */
public final class NodeDocsScreen extends Screen {
    private static final int LIST_W = 170;
    private static final int ROW_H = 14;
    private static final int LIST_TOP = 36;
    private static final int LIST_BOTTOM_PAD = 8;
    private static final int PAD = 12;
    private static final int CLOSE_W = 64;
    private static final int IMPORT_W = 110;
    private static final int IMPORT_H = 18;

    private final ComputerEditorScreen parent;

    private final List<Row> rows = new ArrayList<>();
    /** Categories the player opened explicitly — everything starts collapsed. */
    private final Set<String> expandedFolders = new HashSet<>();

    private EditBox searchBox;
    private String search = "";
    private int listScroll;
    private int detailScroll;
    private int detailContentHeight;
    private NodeDocsEntry selected;
    private NodeDocsExampleStore examples;
    private String statusMessage = "";
    private long statusUntilMs;

    public NodeDocsScreen(ComputerEditorScreen parent) {
        super(Component.literal("Node Docs"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        rebuildRows();
        examples = NodeDocsExampleStore.load(minecraft.getResourceManager());
        searchBox = new EditBox(font, 6, 22, LIST_W - 12, 14, Component.literal("Search nodes"));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value == null ? "" : value;
            listScroll = 0;
            detailScroll = 0;
            rebuildRows();
            // While searching every folder is expanded, so a vanished selection really is gone.
            if (selected != null && !search.isEmpty() && !rowsContain(selected)) {
                selected = firstSelectable();
            }
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(width - CLOSE_W - 6, 6, CLOSE_W, 18)
                .build());

        if (selected == null) {
            selected = firstSelectable();
        } else if (!search.isEmpty() && !rowsContain(selected)) {
            selected = firstSelectable();
        }
    }

    private boolean rowsContain(NodeDocsEntry entry) {
        for (Row row : rows) {
            if (row.entry != null && row.entry == entry) {
                return true;
            }
        }
        return false;
    }

    private NodeDocsEntry firstSelectable() {
        for (Row row : rows) {
            if (row.entry != null) {
                return row.entry;
            }
        }
        return null;
    }

    /** An active search shows all folders; otherwise only the explicitly opened ones. */
    private boolean folderExpanded(String folder) {
        return !search.strip().isEmpty() || expandedFolders.contains(folder);
    }

    private void rebuildRows() {
        rows.clear();
        List<NodeDocsEntry> matches = NodeDocsLibrary.search(search);
        String currentFolder = null;
        boolean expanded = false;
        for (NodeDocsEntry entry : matches) {
            String folder = entry.folder();
            if (!folder.equals(currentFolder)) {
                rows.add(Row.header(folder));
                currentFolder = folder;
                expanded = folderExpanded(folder);
            }
            if (expanded) {
                rows.add(Row.item(entry));
            }
        }
    }

    private int detailX() {
        return LIST_W + PAD;
    }

    private int detailW() {
        return Math.max(40, width - LIST_W - PAD * 2);
    }

    private int importX() {
        return width - IMPORT_W - PAD;
    }

    private int importY() {
        return height - IMPORT_H - 8;
    }

    private int detailTop() {
        return 32;
    }

    /** The import button area below stays fixed — the detail column ends above it. */
    private int detailBottom() {
        return importY() - 6;
    }

    private int detailMaxScroll() {
        return Math.max(0, detailContentHeight - (detailBottom() - detailTop()));
    }

    private boolean canImportSelected() {
        return selected != null && examples != null && examples.has(selected.id());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen supplies its own opaque background. The vanilla pass would run the
        // full-screen blur effect (processBlurEffect) over everything drawn so far, which
        // blurred all custom text — only the widgets rendered after super.render stayed crisp.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Opaque stage background — see renderBackground() above for why the vanilla pass is skipped.
        graphics.fill(0, 0, width, height, ComputedEditorTheme.BACKGROUND_PRIMARY);
        graphics.fill(0, 0, LIST_W, height, ComputedEditorTheme.BACKGROUND_SECONDARY);
        graphics.fill(LIST_W, 0, width, height, ComputedEditorTheme.BACKGROUND_SECTION);
        graphics.vLine(LIST_W - 1, 0, height, ComputedEditorTheme.BORDER_MENU);

        graphics.drawString(font, "Node Docs", 8, 9, ComputedEditorTheme.TEXT_HEADER, false);

        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
        renderImportButton(graphics, mouseX, mouseY);

        // Widgets (search box, close) last so they stay crisp above the custom chrome.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderImportButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        int x = importX();
        int y = importY();
        boolean enabled = canImportSelected();
        boolean hovered = enabled
                && mouseX >= x && mouseX < x + IMPORT_W && mouseY >= y && mouseY < y + IMPORT_H;
        ComputedEditorStyle.drawButton(graphics, x, y, IMPORT_W, IMPORT_H, hovered, enabled);
        String label = enabled ? "Import example" : "No example";
        ComputedEditorStyle.drawCenteredString(
                graphics,
                font,
                label,
                x,
                y,
                IMPORT_W,
                IMPORT_H,
                enabled ? ComputedEditorTheme.TEXT_PRIMARY : ComputedEditorTheme.TEXT_DISABLED);

        if (System.currentTimeMillis() < statusUntilMs && !statusMessage.isEmpty()) {
            int tw = font.width(statusMessage);
            int sx = Math.max(LIST_W + 4, (LIST_W + width) / 2 - tw / 2);
            graphics.fill(sx - 4, y - 14, sx + tw + 4, y - 2, 0xE0000000);
            graphics.drawString(font, statusMessage, sx, y - 12, ComputedEditorTheme.ACCENT, false);
        }
    }

    private void importSelected() {
        if (!canImportSelected()) {
            return;
        }
        ComputedProgramV3 program;
        try {
            program = examples.open(selected.id());
        } catch (RuntimeException exception) {
            statusMessage = "Example failed to load";
            statusUntilMs = System.currentTimeMillis() + 4000;
            return;
        }
        if (parent.importDocsExample(program)) {
            statusMessage = "Imported — returning to editor";
            statusUntilMs = System.currentTimeMillis() + 1500;
            // Defer the screen swap to the next frame so the status line gets one draw.
            minecraft.tell(this::onClose);
        } else {
            statusMessage = "Import rejected by editor";
            statusUntilMs = System.currentTimeMillis() + 4000;
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listH = height - LIST_TOP - LIST_BOTTOM_PAD;
        int contentH = rows.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH);
        listScroll = Mth.clamp(listScroll, 0, maxScroll);

        graphics.enableScissor(0, LIST_TOP, LIST_W, height - LIST_BOTTOM_PAD);
        int y = LIST_TOP - listScroll;
        for (Row row : rows) {
            int rowBottom = y + ROW_H;
            if (rowBottom > LIST_TOP && y < height - LIST_BOTTOM_PAD) {
                boolean hovered = mouseX >= 4 && mouseX < LIST_W - 4
                        && mouseY >= Math.max(y, LIST_TOP)
                        && mouseY < Math.min(rowBottom, height - LIST_BOTTOM_PAD);
                if (row.header) {
                    boolean expanded = folderExpanded(row.label);
                    int fill = hovered ? ComputedEditorTheme.MENU_HOVER : ComputedEditorTheme.MENU_BACKGROUND;
                    graphics.fill(0, y, LIST_W, rowBottom, fill);
                    graphics.drawString(font, (expanded ? "▾ " : "▸ ") + row.label, 8, y + 3,
                            expanded ? ComputedEditorTheme.ACCENT_MUTED : ComputedEditorTheme.TEXT_SECONDARY, false);
                } else {
                    boolean selectedRow = row.entry == selected;
                    int fill = selectedRow
                            ? ComputedEditorTheme.MENU_SELECTED
                            : hovered ? ComputedEditorTheme.MENU_HOVER : 0;
                    if (fill != 0) {
                        graphics.fill(4, y, LIST_W - 4, rowBottom, fill);
                    }
                    graphics.drawString(font, row.label, 12, y + 3,
                            selectedRow ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.TEXT_PRIMARY, false);
                }
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackH = height - LIST_TOP - LIST_BOTTOM_PAD;
            int thumbH = Math.max(12, trackH * trackH / Math.max(1, contentH));
            int thumbY = LIST_TOP + (trackH - thumbH) * listScroll / maxScroll;
            graphics.fill(LIST_W - 3, thumbY, LIST_W - 1, thumbY + thumbH, ComputedEditorTheme.BORDER_HIGHLIGHT);
        }
    }

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int w = detailW();
        int top = detailTop();
        int bottom = detailBottom();
        detailScroll = Mth.clamp(detailScroll, 0, detailMaxScroll());
        // Scissor the detail column so long content never bleeds over the list or the button row.
        graphics.enableScissor(LIST_W, top, width, bottom);

        if (selected == null) {
            String hint = rows.isEmpty()
                    ? "No matching nodes."
                    : "Expand a category on the left to browse nodes.";
            graphics.drawString(font, hint, x, top + 16, ComputedEditorTheme.TEXT_SECONDARY, false);
            graphics.disableScissor();
            detailContentHeight = 0;
            return;
        }

        int y = top - detailScroll;
        graphics.drawString(font, selected.title(), x, y, ComputedEditorTheme.TEXT_HEADER, false);
        y += 13;
        graphics.drawString(font, selected.id(), x, y, ComputedEditorTheme.TEXT_TERTIARY, false);
        y += 13;
        graphics.drawString(font, "Category: " + selected.folder(), x, y, ComputedEditorTheme.ACCENT_MUTED, false);
        y += 18;

        y = drawSectionLabel(graphics, x, y, w, "About");
        y = drawWrapped(graphics, x, y, w, selected.summary(), ComputedEditorTheme.TEXT_PRIMARY);
        y += 8;

        if (selected.inputs().length > 0) {
            y = drawSectionLabel(graphics, x, y, w, "Inputs");
            for (String port : selected.inputs()) {
                graphics.drawString(font, "  " + port, x, y, ComputedEditorTheme.TEXT_SECONDARY, false);
                y += 11;
            }
            y += 6;
        }
        if (selected.outputs().length > 0) {
            y = drawSectionLabel(graphics, x, y, w, "Outputs");
            for (String port : selected.outputs()) {
                graphics.drawString(font, "  " + port, x, y, ComputedEditorTheme.TEXT_SECONDARY, false);
                y += 11;
            }
            y += 6;
        }

        String example = selected.example();
        if (example != null && !example.isBlank()) {
            y = drawSectionLabel(graphics, x, y, w, "Example");
            int codeTop = y;
            List<String> wrapped = wrapCode(example, w);
            int codeH = wrapped.size() * 10 + 10;
            graphics.fill(x - 2, codeTop - 2, x + w, codeTop + codeH, ComputedEditorTheme.BACKGROUND_INPUT);
            graphics.renderOutline(x - 2, codeTop - 2, w + 2, codeH, ComputedEditorTheme.BORDER_DEFAULT);
            int lineY = codeTop + 3;
            for (String line : wrapped) {
                graphics.drawString(font, line, x + 4, lineY, ComputedEditorTheme.TEXT_PRIMARY, false);
                lineY += 10;
            }
            y = codeTop + codeH + 8;
        }

        graphics.disableScissor();
        detailContentHeight = y - top + detailScroll + 8;

        int maxScroll = detailMaxScroll();
        if (maxScroll > 0) {
            int trackH = bottom - top;
            int contentH = Math.max(trackH, detailContentHeight);
            int thumbH = (int) Math.max(12L, (long) trackH * trackH / contentH);
            int thumbY = top + (int) ((long) (trackH - thumbH) * detailScroll / maxScroll);
            graphics.fill(width - 3, thumbY, width - 1, thumbY + thumbH, ComputedEditorTheme.BORDER_HIGHLIGHT);
        }
    }

    private int drawSectionLabel(GuiGraphics graphics, int x, int y, int w, String label) {
        graphics.drawString(font, label, x, y, ComputedEditorTheme.ACCENT, false);
        graphics.hLine(x, x + w - 1, y + 10, ComputedEditorTheme.BORDER_SUBTLE);
        return y + 14;
    }

    /**
     * Wraps Lua source lines to the pane's pixel width: prefer the last word break that still
     * fits, hard-cut tokens that are longer than a line (endpoint calls, long strings) so nothing
     * is clipped by the screen edge.
     */
    private List<String> wrapCode(String code, int w) {
        int maxWidth = Math.max(24, w - 8);
        List<String> out = new ArrayList<>();
        for (String line : code.split("\n", -1)) {
            if (line.isEmpty()) {
                out.add("");
                continue;
            }
            String remaining = line;
            while (font.width(remaining) > maxWidth) {
                int low = 1;
                int high = remaining.length();
                int fit = 1;
                while (low <= high) {
                    int middle = (low + high) / 2;
                    if (font.width(remaining.substring(0, middle)) <= maxWidth) {
                        fit = middle;
                        low = middle + 1;
                    } else {
                        high = middle - 1;
                    }
                }
                int space = remaining.lastIndexOf(' ', fit);
                if (space > 0) {
                    fit = space;
                }
                out.add(remaining.substring(0, fit));
                remaining = remaining.substring(fit).stripLeading();
            }
            out.add(remaining);
        }
        return out;
    }

    private int drawWrapped(GuiGraphics graphics, int x, int y, int w, String text, int color) {
        int maxChars = Math.max(8, w / 6);
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isBlank()) {
                y += 6;
                continue;
            }
            String remaining = paragraph.stripTrailing();
            while (!remaining.isEmpty()) {
                if (remaining.length() <= maxChars) {
                    graphics.drawString(font, remaining, x, y, color, false);
                    y += 10;
                    break;
                }
                int cut = remaining.lastIndexOf(' ', maxChars);
                if (cut < maxChars / 2) {
                    cut = maxChars;
                }
                graphics.drawString(font, remaining.substring(0, cut), x, y, color, false);
                y += 10;
                remaining = remaining.substring(cut).stripLeading();
            }
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < LIST_W && mouseY >= LIST_TOP) {
            listScroll = (int) (listScroll - scrollY * ROW_H);
            return true;
        }
        if (mouseX >= LIST_W && mouseY >= detailTop() && mouseY <= detailBottom()) {
            detailScroll = (int) (detailScroll - scrollY * 12);
            detailScroll = Mth.clamp(detailScroll, 0, detailMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canImportSelected()
                && mouseX >= importX() && mouseX < importX() + IMPORT_W
                && mouseY >= importY() && mouseY < importY() + IMPORT_H) {
            importSelected();
            return true;
        }
        if (button == 0 && mouseX >= 4 && mouseX < LIST_W - 4 && mouseY >= LIST_TOP) {
            int index = (int) ((mouseY - LIST_TOP + listScroll) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                if (row.header) {
                    // While searching all folders are forced open — toggling only applies otherwise.
                    if (search.strip().isEmpty()) {
                        if (!expandedFolders.remove(row.label)) {
                            expandedFolders.add(row.label);
                        }
                        rebuildRows();
                        if (selected == null) {
                            selected = firstSelectable();
                        }
                    }
                    return true;
                }
                if (row.entry != null) {
                    selected = row.entry;
                    detailScroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        if (keyCode == 264 && !rows.isEmpty()) { // DOWN
            moveSelection(1);
            return true;
        }
        if (keyCode == 265 && !rows.isEmpty()) { // UP
            moveSelection(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        int currentIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).entry == selected) {
                currentIndex = i;
                break;
            }
        }
        int next = currentIndex;
        for (int step = 0; step < rows.size(); step++) {
            next += direction;
            if (next < 0 || next >= rows.size()) {
                return;
            }
            if (rows.get(next).entry != null) {
                selected = rows.get(next).entry;
                ensureVisible(next);
                detailScroll = 0;
                return;
            }
        }
    }

    private void ensureVisible(int index) {
        int listH = height - LIST_TOP - LIST_BOTTOM_PAD;
        int top = index * ROW_H;
        int bottom = top + ROW_H;
        if (top < listScroll) {
            listScroll = top;
        } else if (bottom > listScroll + listH) {
            listScroll = bottom - listH;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private record Row(boolean header, String label, NodeDocsEntry entry) {
        static Row header(String label) {
            return new Row(true, label, null);
        }

        static Row item(NodeDocsEntry entry) {
            return new Row(false, "    " + entry.title(), entry);
        }
    }
}

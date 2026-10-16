package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.network.WidgetTextInputPayload;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Small modal for typing a value into a monitor text-input widget. Enter (or the Confirm button)
 * submits the string to the server; Escape cancels. Opened by {@code WidgetTextInputBridge} when
 * the server reports a hit against a {@code TEXT_INPUT} widget.
 */
public class WidgetTextInputScreen extends Screen {
    private final BlockPos originPos;
    private final UUID widgetId;
    private final String initialValue;

    private EditBox editBox;

    public WidgetTextInputScreen(BlockPos originPos, UUID widgetId, String initialValue) {
        super(Component.translatable("computed.widget.text_input.title"));
        this.originPos = originPos;
        this.widgetId = widgetId;
        this.initialValue = initialValue == null ? "" : initialValue;
    }

    @Override
    protected void init() {
        int boxW = Math.min(240, width - 40);
        int boxX = (width - boxW) / 2;
        int boxY = height / 2 - 10;
        editBox = new EditBox(font, boxX, boxY, boxW, 20, Component.translatable("computed.widget.text_input.label"));
        editBox.setMaxLength(WidgetTextInputPayload.MAX_LENGTH);
        editBox.setValue(initialValue);
        addRenderableWidget(editBox);
        setInitialFocus(editBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(width / 2 - 40, boxY + 28, 80, 20)
                .build());
    }

    private void submit() {
        String text = editBox == null ? "" : editBox.getValue();
        PacketDistributor.sendToServer(new WidgetTextInputPayload(originPos, widgetId, text));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER / KP_ENTER
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark scrim only — the vanilla pass runs a full-screen blur effect that would soften
        // the title text drawn by this screen.
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
    }
}

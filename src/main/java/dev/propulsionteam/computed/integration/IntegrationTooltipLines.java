package dev.propulsionteam.computed.integration;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Tooltip lines shared by the optional hover-mod integrations (Jade, WTHIT). */
public final class IntegrationTooltipLines {
    private IntegrationTooltipLines() {}

    /**
     * Returns the computer's numeric id as a translatable tooltip line, or {@code null} when the
     * hovered block is not a Computed computer or its id has not been assigned yet.
     */
    public static @Nullable Component computerId(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof ComputerBlockEntity computer)) {
            return null;
        }
        int id = computer.computerId();
        return id < 0 ? null : Component.translatable("computed.tooltip.computer_id", id);
    }
}

package dev.propulsionteam.computed.menu;

import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The computer's drive slot: exactly one Program Disk, nothing else. */
public class ProgramDiskSlot extends Slot {
    public ProgramDiskSlot(Container container, int computerSlotIndex, int x, int y) {
        super(container, computerSlotIndex, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof ProgramDiskItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }
}

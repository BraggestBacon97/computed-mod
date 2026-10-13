package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Peripheral hardware utilities. Items tagged {@link ComputedTags.Items#PERIPHERAL} (or the legacy
 * {@code PERIPHERALS}) may be inserted into a Computer's container; nodes that need a specific item
 * type can register a gate via {@link #peripheralItemRequiredForNodeType} (none do today).
 */
public final class Peripherals {
    private Peripherals() {}

    /** Hardware token required for a node type, or {@code null} if the node is always available. */
    public static ResourceLocation peripheralItemRequiredForNodeType(ResourceLocation nodeTypeId) {
        return null;
    }

    public static Predicate<ResourceLocation> hardwareMissingPredicate(Set<ResourceLocation> equippedItemIds) {
        Set<ResourceLocation> eq = Set.copyOf(equippedItemIds);
        return nodeTypeId -> {
            ResourceLocation req = peripheralItemRequiredForNodeType(nodeTypeId);
            return req != null && !eq.contains(req);
        };
    }

    public static boolean graphNbtUsesMissingPeripheral(CompoundTag graphBody, Set<ResourceLocation> equippedItemIds) {
        return false;
    }

    public static final String NBT_EDITOR_PERIPHERAL_UNLOCK = "ComputedPeripheralUnlock";

    public static void writePeripheralUnlockTag(Container computer, CompoundTag out) {
        ListTag list = new ListTag();
        for (int i = 0; i < computer.getContainerSize(); i++) {
            ItemStack st = computer.getItem(i);
            if (!st.isEmpty() && isPeripheral(st)) {
                list.add(StringTag.valueOf(nodeTypeFor(st).toString()));
            }
        }
        out.put(NBT_EDITOR_PERIPHERAL_UNLOCK, list);
    }

    public static List<net.minecraft.network.chat.Component> readPlacedPeripheralHudLines(CompoundTag editorBundle) {
        return List.of();
    }

    public static void stripEditorOnlyTags(CompoundTag tag) {
        tag.remove(NBT_EDITOR_PERIPHERAL_UNLOCK);
    }

    public static boolean isPeripheral(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(ComputedTags.Items.PERIPHERAL) || stack.is(ComputedTags.Items.PERIPHERALS);
    }

    public static ResourceLocation nodeTypeFor(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static boolean isPeripheralNodeType(ResourceLocation typeId) {
        if (!BuiltInRegistries.ITEM.containsKey(typeId)) {
            return false;
        }
        ItemStack stack = BuiltInRegistries.ITEM.get(typeId).getDefaultInstance();
        return stack.is(ComputedTags.Items.PERIPHERAL) || stack.is(ComputedTags.Items.PERIPHERALS);
    }

    public static boolean mayPlaceInComputer(Container container, int slot, ItemStack stack) {
        if (slot == ComputerBlockEntity.DISK_SLOT_INDEX) {
            return false;
        }
        if (stack.isEmpty() || !isPeripheral(stack)) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack other = container.getItem(i);
            if (!other.isEmpty() && ItemStack.isSameItemSameComponents(stack, other)) {
                return false;
            }
        }
        return true;
    }
}

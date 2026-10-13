package dev.propulsionteam.computed.content.items;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
import dev.propulsionteam.computed.PortableDisplayBridge;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Handheld windowed screen that mirrors the widgets of a linked computer. Right-click a computer
 * to link; use again to open the display; sneak-use in air clears the link (or copies it from a
 * bound {@link LinkCardItem} held in the offhand).
 */
public class PortableMonitorItem extends Item {
    /** Key of the stored link tag inside the {@code custom_data} component. */
    public static final String CARD_KEY = "StoredLink";
    /**
     * Virtual canvas in widget pixels: 16:9 landscape with four 32px widget lines. The windowed
     * screen letterboxes this into whatever GUI size fits.
     */
    public static final int CANVAS_W = 256;
    public static final int CANVAS_H = 144;

    public PortableMonitorItem(Properties properties) {
        super(properties);
    }

    /** Stored link, or {@code null}. */
    @Nullable
    public static PortableLinkData readLink(ItemStack monitor) {
        if (monitor.isEmpty() || !(monitor.getItem() instanceof PortableMonitorItem)) {
            return null;
        }
        CustomData data = monitor.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompound(CARD_KEY);
        if (!tag.contains("dimension")) {
            return null;
        }
        try {
            return PortableLinkData.of(tag);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Stores the link. */
    public static void writeLink(ItemStack monitor, PortableLinkData link) {
        CustomData existing = monitor.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.put(CARD_KEY, link.toTag());
        monitor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Clears the stored link. */
    public static void clearLink(ItemStack monitor) {
        CustomData existing = monitor.get(DataComponents.CUSTOM_DATA);
        if (existing == null) {
            return;
        }
        CompoundTag tag = existing.copyTag();
        tag.remove(CARD_KEY);
        if (tag.isEmpty()) {
            monitor.remove(DataComponents.CUSTOM_DATA);
        } else {
            monitor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** True when the given stack is this item with a stored link. */
    public static boolean isPaired(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PortableMonitorItem && readLink(stack) != null;
    }

    /** Bound monitors carry the enchantment glint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return readLink(stack) != null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        PortableLinkData link = new PortableLinkData(
                pos.getX(), pos.getY(), pos.getZ(), level.dimension().location().toString());
        writeLink(context.getItemInHand(), link);
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "item.computed.portable_monitor.linked", pos.getX(), pos.getY(), pos.getZ()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            PortableLinkData cardLink = LinkCardItem.read(player.getOffhandItem());
            if (cardLink != null) {
                writeLink(stack, cardLink);
                player.displayClientMessage(Component.translatable(
                        "item.computed.portable_monitor.copied",
                        cardLink.x(), cardLink.y(), cardLink.z()), true);
            } else {
                clearLink(stack);
                player.displayClientMessage(
                        Component.translatable("item.computed.portable_monitor.cleared"), true);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (level.isClientSide) {
            PortableLinkData link = readLink(stack);
            if (link != null) {
                PortableDisplayBridge.openScreen(link.blockPos());
            } else {
                player.displayClientMessage(
                        Component.translatable("item.computed.portable_monitor.hint"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PortableLinkData link = readLink(stack);
        if (link == null) {
            tooltip.add(Component.translatable("item.computed.portable_monitor.unlinked_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.computed.portable_monitor.paired", link.x(), link.y(), link.z())
                .withStyle(ChatFormatting.GRAY));
    }
}

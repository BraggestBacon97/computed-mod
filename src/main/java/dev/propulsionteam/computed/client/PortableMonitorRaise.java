package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.PortableDisplayBridge;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client state machine for raising the portable monitor: right-click starts a short countdown
 * during which the item plays the open animation (GeoLib), and the screen opens only once the
 * raise has finished. Closing the screen lowers the item again.
 */
@EventBusSubscriber(modid = "computed", value = Dist.CLIENT)
public final class PortableMonitorRaise {
    /** Ticks between right-click and screen open; matches the open animation length (0.5417s). */
    private static final int RAISE_TICKS = 11;

    private static List<BlockPos> pendingSources;
    private static int pendingActive;
    private static int countdown = -1;

    private PortableMonitorRaise() {}

    /**
     * Starts the raise for a linked monitor stack. Ignored while already raising or open: holding
     * right-click re-fires use() every 4 ticks, which used to reset the countdown over and over.
     */
    public static void start(ItemStack stack) {
        if (isActive()) {
            return;
        }
        List<PortableLinkData> links = PortableMonitorItem.readLinks(stack);
        if (links.isEmpty()) {
            return;
        }
        pendingSources = links.stream().map(PortableLinkData::blockPos).toList();
        pendingActive = PortableMonitorItem.readActiveIndex(stack);
        countdown = RAISE_TICKS;
    }

    /** True while opening or while the portable screen is open. */
    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return countdown > 0 || mc.screen instanceof PortableMonitorScreen;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (countdown <= 0) {
            return;
        }
        countdown--;
        if (countdown > 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null
                && mc.player != null
                && pendingSources != null
                && mc.player.getMainHandItem().getItem() instanceof PortableMonitorItem) {
            PortableDisplayBridge.openScreen(pendingSources, pendingActive);
        }
        pendingSources = null;
    }
}

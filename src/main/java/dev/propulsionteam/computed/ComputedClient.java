package dev.propulsionteam.computed;

import dev.propulsionteam.computed.client.ComputerEditorScreen;
import dev.propulsionteam.computed.client.ComputerPeripheralScreen;
import dev.propulsionteam.computed.client.PortableMonitorScreen;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import dev.propulsionteam.computed.persistence.ProgramDiskData;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.propulsionteam.computed.client.MonitorBlockEntityRenderer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Computed.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT)
public class ComputedClient {
    static {
        ComputerEditorBridge.install((pos, serverRevision, tag) -> {
            var program = ProgramV3Codec.decode(tag, pos.toShortString(), Computed.LOGGER::warn).program();
            int fsComputerId = tag.contains("FsComputerId") ? tag.getInt("FsComputerId") : -1;
            String computerName = tag.getString("ComputerName");
            Minecraft.getInstance()
                    .setScreen(
                            new ComputerEditorScreen(
                                    pos,
                                    program,
                                    serverRevision,
                                    fsComputerId,
                                    computerName,
                                    readSlotDisk(tag),
                                    tag.getBoolean(ComputerBlockEntity.DISK_PRESENT_TAG)));
        }, (pos, accepted, serverRevision, editorRevision, message) -> {
            if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen
                    && screen.editsComputer(pos)) {
                screen.onServerSaveResult(accepted, serverRevision, editorRevision, message);
            }
        });
        PackageTransferBridge.install(
                (kind, folder, error, entries) -> {
                    if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen) {
                        screen.onPackageListResult(kind, folder, error, entries);
                    }
                },
                (kind, path, error, data) -> {
                    if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen) {
                        screen.onPackageReadResult(kind, path, error, data);
                    }
                },
                (opId, status, fileName, error) -> {
                    if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen) {
                        screen.onPackageWriteResult(opId, status, fileName, error);
                    }
                },
                (accepted, computerId, message) -> {
                    if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen) {
                        screen.onComputerIdResult(accepted, computerId, message);
                    }
                });
        PortableDisplayBridge.install(
                (pos, bytes, name) -> {
                    if (Minecraft.getInstance().screen instanceof PortableMonitorScreen screen) {
                        screen.onDisplay(pos, bytes, name);
                    }
                },
                (sources, active) -> Minecraft.getInstance().setScreen(new PortableMonitorScreen(sources, active)));
    }

    public ComputedClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /** Decodes the computer's drive-slot disk from the editor bundle, or {@code null}. */
    private static ProgramDiskData readSlotDisk(CompoundTag tag) {
        byte[] bytes = tag.getByteArray(ComputerBlockEntity.DISK_TAG);
        if (bytes.length == 0) {
            return null;
        }
        try {
            return ProgramDiskData.decode(bytes);
        } catch (IllegalArgumentException exception) {
            Computed.LOGGER.warn("Discarding damaged computer drive disk data", exception);
            return null;
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Computed.LOGGER.info("Computed client setup");
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                ComputedRegistries.SPEAKER_BLOCK.get(), RenderType.cutout()));
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        MenuType<ComputerPeripheralMenu> type =
                (MenuType<ComputerPeripheralMenu>) ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get();
        event.register(type, ComputerPeripheralScreen::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ComputedRegistries.MONITOR_BLOCK_ENTITY.get(), MonitorBlockEntityRenderer::new);
    }
}

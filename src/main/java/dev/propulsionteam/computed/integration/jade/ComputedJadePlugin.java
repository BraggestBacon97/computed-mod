package dev.propulsionteam.computed.integration.jade;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.integration.IntegrationTooltipLines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade discovers this class by scanning mod jars for {@link WailaPlugin}. */
@WailaPlugin
public final class ComputedJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new ComputerTooltip(), ComputerBlock.class);
    }

    private static final class ComputerTooltip implements IBlockComponentProvider {
        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(Computed.MODID, "computer_id");
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Component line = IntegrationTooltipLines.computerId(accessor.getBlockEntity());
            if (line != null) {
                tooltip.add(line);
            }
        }
    }
}

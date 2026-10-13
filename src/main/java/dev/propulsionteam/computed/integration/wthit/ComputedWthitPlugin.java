package dev.propulsionteam.computed.integration.wthit;

import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.integration.IntegrationTooltipLines;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.network.chat.Component;

/** WTHIT discovers this class through {@code waila_plugins.json} in the mod jar root. */
public final class ComputedWthitPlugin implements IWailaCommonPlugin, IWailaClientPlugin {
    @Override
    public void register(ICommonRegistrar registrar) {}

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(new ComputerTooltip(), ComputerBlock.class);
    }

    private static final class ComputerTooltip implements IBlockComponentProvider {
        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            Component line = IntegrationTooltipLines.computerId(accessor.getBlockEntity());
            if (line != null) {
                tooltip.addLine(line);
            }
        }
    }
}

package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Right-click on a monitor: client sends the origin block pos plus the screen-local (u, v) ∈ [0, 1]
 * coordinates of the hit. Server resolves the widget under that point and dispatches input to it.
 */
public record MonitorClickPayload(BlockPos originPos, double u, double v) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MonitorClickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "monitor_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MonitorClickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            MonitorClickPayload::originPos,
            ByteBufCodecs.DOUBLE,
            MonitorClickPayload::u,
            ByteBufCodecs.DOUBLE,
            MonitorClickPayload::v,
            MonitorClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

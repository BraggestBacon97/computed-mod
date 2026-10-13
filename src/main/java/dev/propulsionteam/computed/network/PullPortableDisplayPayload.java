package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks for the latest widget frame of the computer its open portable monitor is linked to.
 * The server answers only when the requesting player carries a link to that computer, and only
 * when the frame hash differs from {@code knownHash}.
 */
public record PullPortableDisplayPayload(BlockPos computerPos, long knownHash)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PullPortableDisplayPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "pull_portable_display"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullPortableDisplayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    PullPortableDisplayPayload::computerPos,
                    ByteBufCodecs.VAR_LONG,
                    PullPortableDisplayPayload::knownHash,
                    PullPortableDisplayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

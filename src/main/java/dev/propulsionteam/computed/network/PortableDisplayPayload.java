package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server answer to a portable display pull: the wire-encoded {@code WidgetDrawList} of the linked
 * computer, laid out for the portable canvas, plus the computer's display name.
 */
public record PortableDisplayPayload(BlockPos computerPos, long hash, String computerName, byte[] drawListBytes)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PortableDisplayPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "portable_display"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableDisplayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    PortableDisplayPayload::computerPos,
                    ByteBufCodecs.VAR_LONG,
                    PortableDisplayPayload::hash,
                    ByteBufCodecs.STRING_UTF8,
                    PortableDisplayPayload::computerName,
                    ByteBufCodecs.BYTE_ARRAY,
                    PortableDisplayPayload::drawListBytes,
                    PortableDisplayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: request the content of one of a computer's package files. */
public record PackageReadPayload(BlockPos computerPos, int kind, String path)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageReadPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_read"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageReadPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PackageReadPayload::computerPos,
            ByteBufCodecs.VAR_INT,
            PackageReadPayload::kind,
            ByteBufCodecs.STRING_UTF8,
            PackageReadPayload::path,
            PackageReadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

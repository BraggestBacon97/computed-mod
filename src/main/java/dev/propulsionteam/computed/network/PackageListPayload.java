package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: request the immediate children of a computer's package folder. */
public record PackageListPayload(BlockPos computerPos, int kind, String folder)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageListPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PackageListPayload::computerPos,
            ByteBufCodecs.VAR_INT,
            PackageListPayload::kind,
            ByteBufCodecs.STRING_UTF8,
            PackageListPayload::folder,
            PackageListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

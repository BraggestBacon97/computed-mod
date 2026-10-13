package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: response to a completed {@link PackageWritePayload} sequence. {@code status} is the
 * ordinal of {@code PackageTransferService.WriteStatus}; {@code fileName} holds the final name
 * for OK results (differs from the request for NEXT_AVAILABLE).
 */
public record PackageWriteResultPayload(int opId, int status, String fileName, String error)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageWriteResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_write_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageWriteResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PackageWriteResultPayload::opId,
                    ByteBufCodecs.VAR_INT,
                    PackageWriteResultPayload::status,
                    ByteBufCodecs.STRING_UTF8,
                    PackageWriteResultPayload::fileName,
                    ByteBufCodecs.STRING_UTF8,
                    PackageWriteResultPayload::error,
                    PackageWriteResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

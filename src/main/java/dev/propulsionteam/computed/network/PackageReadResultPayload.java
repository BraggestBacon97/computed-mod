package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: response to {@link PackageReadPayload}; {@code error} is empty on success. */
public record PackageReadResultPayload(int kind, String path, String error, byte[] data)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageReadResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_read_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageReadResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PackageReadResultPayload::kind,
                    ByteBufCodecs.STRING_UTF8,
                    PackageReadResultPayload::path,
                    ByteBufCodecs.STRING_UTF8,
                    PackageReadResultPayload::error,
                    ByteBufCodecs.BYTE_ARRAY,
                    PackageReadResultPayload::data,
                    PackageReadResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

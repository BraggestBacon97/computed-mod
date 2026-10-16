package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client right-clicked a portable monitor channel tab to unlink that computer. */
public record RemovePortableSourcePayload(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemovePortableSourcePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "remove_portable_source"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemovePortableSourcePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RemovePortableSourcePayload::index,
                    RemovePortableSourcePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

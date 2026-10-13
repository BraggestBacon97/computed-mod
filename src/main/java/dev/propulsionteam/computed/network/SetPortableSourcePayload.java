package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client tells the server which channel its open portable monitor selected. The server stores the
 * index on the monitor item in the player's inventory so the selection survives reopening.
 */
public record SetPortableSourcePayload(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetPortableSourcePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "set_portable_source"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPortableSourcePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SetPortableSourcePayload::index,
                    SetPortableSourcePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: outcome of a Set Computer ID request. */
public record SetComputerIdResultPayload(boolean accepted, int computerId, String message)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetComputerIdResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "set_computer_id_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetComputerIdResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    SetComputerIdResultPayload::accepted,
                    ByteBufCodecs.VAR_INT,
                    SetComputerIdResultPayload::computerId,
                    ByteBufCodecs.STRING_UTF8,
                    SetComputerIdResultPayload::message,
                    SetComputerIdResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: assign a manually chosen numeric ID to the computer at {@code computerPos}. */
public record SetComputerIdPayload(BlockPos computerPos, int newId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetComputerIdPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "set_computer_id"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetComputerIdPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SetComputerIdPayload::computerPos,
                    ByteBufCodecs.VAR_INT,
                    SetComputerIdPayload::newId,
                    SetComputerIdPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

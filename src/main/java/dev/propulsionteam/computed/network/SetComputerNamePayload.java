package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: assign a player-chosen display name to the computer at {@code computerPos}. */
public record SetComputerNamePayload(BlockPos computerPos, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetComputerNamePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "set_computer_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetComputerNamePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SetComputerNamePayload::computerPos,
                    ByteBufCodecs.STRING_UTF8,
                    SetComputerNamePayload::name,
                    SetComputerNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

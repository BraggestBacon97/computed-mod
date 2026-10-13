package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.persistence.PackageTransferService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: one chunk of a computer's package write, either into the computer's package folders or
 * onto the program disk in the sender's main hand ({@code target}). Chunks of the same
 * {@code opId} arrive in order (TCP); the server assembles them until {@code totalSize} bytes
 * were received.
 *
 * <p>Eight fields exceed what {@link StreamCodec#composite} supports (six), so the codec is
 * hand-written; the chunk is capped at the serverbound payload budget while decoding.
 */
public record PackageWritePayload(
        int opId,
        BlockPos computerPos,
        int kind,
        String path,
        int mode,
        int target,
        int totalSize,
        byte[] chunk)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageWritePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_write"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageWritePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PackageWritePayload decode(RegistryFriendlyByteBuf buf) {
                    return new PackageWritePayload(
                            buf.readVarInt(),
                            BlockPos.STREAM_CODEC.decode(buf),
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readByteArray(PackageTransferService.WRITE_CHUNK_BYTES));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PackageWritePayload payload) {
                    buf.writeVarInt(payload.opId());
                    BlockPos.STREAM_CODEC.encode(buf, payload.computerPos());
                    buf.writeVarInt(payload.kind());
                    buf.writeUtf(payload.path());
                    buf.writeVarInt(payload.mode());
                    buf.writeVarInt(payload.target());
                    buf.writeVarInt(payload.totalSize());
                    buf.writeByteArray(payload.chunk());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

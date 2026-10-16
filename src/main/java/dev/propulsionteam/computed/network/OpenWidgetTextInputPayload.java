package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server tells the client to open the text-input dialog for a hit widget. */
public record OpenWidgetTextInputPayload(BlockPos originPos, UUID widgetId, String initialValue)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenWidgetTextInputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "open_widget_text_input"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID());

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWidgetTextInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    OpenWidgetTextInputPayload::originPos,
                    UUID_CODEC,
                    OpenWidgetTextInputPayload::widgetId,
                    ByteBufCodecs.STRING_UTF8,
                    OpenWidgetTextInputPayload::initialValue,
                    OpenWidgetTextInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

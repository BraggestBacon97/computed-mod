package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Player submitted a text-input widget value. The server routes the string to the originating
 * computer's node {@code input} event (same path as button/slider clicks, but with a string).
 */
public record WidgetTextInputPayload(BlockPos originPos, UUID widgetId, String text)
        implements CustomPacketPayload {
    public static final int MAX_LENGTH = 256;

    public static final CustomPacketPayload.Type<WidgetTextInputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "widget_text_input"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID());

    public static final StreamCodec<RegistryFriendlyByteBuf, WidgetTextInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    WidgetTextInputPayload::originPos,
                    UUID_CODEC,
                    WidgetTextInputPayload::widgetId,
                    ByteBufCodecs.STRING_UTF8,
                    WidgetTextInputPayload::text,
                    WidgetTextInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

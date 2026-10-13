package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.persistence.PackageTransferService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: response to {@link PackageListPayload}; entries are name + directory flag only. */
public record PackageListResultPayload(
        int kind, String folder, String error, List<PackageTransferService.Entry> entries)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PackageListResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "package_list_result"));

    private static final StreamCodec<RegistryFriendlyByteBuf, PackageTransferService.Entry> ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PackageTransferService.Entry::name,
                    ByteBufCodecs.BOOL,
                    PackageTransferService.Entry::directory,
                    (name, directory) -> new PackageTransferService.Entry(name, directory));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<PackageTransferService.Entry>> ENTRIES_CODEC =
            ByteBufCodecs.collection(size -> new ArrayList<>(size), ENTRY_CODEC, 1024);

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageListResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PackageListResultPayload::kind,
                    ByteBufCodecs.STRING_UTF8,
                    PackageListResultPayload::folder,
                    ByteBufCodecs.STRING_UTF8,
                    PackageListResultPayload::error,
                    ENTRIES_CODEC,
                    PackageListResultPayload::entries,
                    PackageListResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

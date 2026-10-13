package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.blocks.SpeakerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.luaj.vm2.LuaValue;

public final class SpeakerEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private SpeakerEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("computed:speaker", endpoint -> endpoint
                .method(
                        "play_note",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playNote,
                        null,
                        "Plays a note-block instrument note on the adjacent speaker. "
                                + "Instrument: harp, basedrum, snare, hat, bass, flute, bell, guitar, chime, "
                                + "xylophone, iron_xylophone, cow_bell, didgeridoo, bit, banjo, pling. "
                                + "Note: 0-24.")
                .method(
                        "play_sound",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::playSound,
                        null,
                        "Plays a registered sound event (namespace:path) at the speaker. "
                                + "Volume defaults are relative to 1.0; pitch 1.0 is normal.")
                .method(
                        "play",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::play,
                        null,
                        "Starts playback of the inserted music disc.")
                .method(
                        "stop",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::stop,
                        null,
                        "Stops the current disc or note immediately.")
                .method(
                        "is_playing",
                        EndpointSignature.of(List.of(), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::isPlaying,
                        null,
                        "Returns whether a disc is currently playing.")
                .method(
                        "has_disc",
                        EndpointSignature.of(List.of(), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, false),
                        SpeakerEndpoints::hasDisc,
                        null,
                        "Returns whether a music disc is inserted.")
                .method(
                        "eject",
                        EndpointSignature.of(List.of(), List.of()),
                        EndpointPolicy.serverThread(true, false),
                        SpeakerEndpoints::eject,
                        null,
                        "Ejects the inserted music disc onto the ground in front of the speaker."));
    }

    private static EndpointResult playNote(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        String name = invocation.arguments().get(0).checkjstring();
        int note = Mth.clamp(invocation.arguments().get(1).checkint(), 0, 24);
        speaker.playNote(instrumentByName(name), note);
        return immediate();
    }

    private static EndpointResult playSound(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        ResourceLocation id = requireSoundId(invocation.arguments().get(0).checkjstring());
        float volume = (float) invocation.arguments().get(1).checkdouble();
        float pitch = (float) invocation.arguments().get(2).checkdouble();
        if (!speaker.playSound(id, Mth.clamp(volume, 0.0F, 4.0F), Mth.clamp(pitch, 0.5F, 2.0F))) {
            throw new IllegalArgumentException("Unknown sound event: " + id);
        }
        return immediate();
    }

    private static EndpointResult play(EndpointInvocation invocation) {
        requireSpeaker(invocation).playDisc();
        return immediate();
    }

    private static EndpointResult stop(EndpointInvocation invocation) {
        requireSpeaker(invocation).stop();
        return immediate();
    }

    private static EndpointResult isPlaying(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).isPlaying()));
    }

    private static EndpointResult hasDisc(EndpointInvocation invocation) {
        return immediate(LuaValue.valueOf(requireSpeaker(invocation).hasDisc()));
    }

    private static EndpointResult eject(EndpointInvocation invocation) {
        SpeakerBlockEntity speaker = requireSpeaker(invocation);
        var level = speaker.getLevel();
        if (level != null && speaker.hasDisc()) {
            var disc = speaker.ejectDisc();
            if (!disc.isEmpty()) {
                ItemEntity item = new ItemEntity(
                        level, speaker.getBlockPos().getX() + 0.5, speaker.getBlockPos().getY() + 0.5,
                        speaker.getBlockPos().getZ() + 0.5, disc);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
        return immediate();
    }

    private static SpeakerBlockEntity requireSpeaker(EndpointInvocation invocation) {
        if (!(invocation.host() instanceof ComputerBlockEntity computer) || computer.getLevel() == null) {
            throw new IllegalStateException("Speaker endpoints require a server computer");
        }
        Direction direction = computer.worldFaceForEndpoint(invocation.target());
        if (direction == null) {
            throw new IllegalArgumentException("Unknown computer side: " + invocation.target());
        }
        BlockPos pos = computer.getBlockPos().relative(direction);
        if (!(computer.getLevel().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            throw new IllegalStateException("No speaker on side: " + invocation.target());
        }
        return speaker;
    }

    static NoteBlockInstrument instrumentByName(String name) {
        for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) {
            if (instrument.getSerializedName().equalsIgnoreCase(name)) {
                return instrument;
            }
        }
        throw new IllegalArgumentException("Unknown instrument: " + name);
    }

    static ResourceLocation requireSoundId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid sound id: " + id);
        }
        return location;
    }

    private static EndpointResult.Immediate immediate(LuaValue... values) {
        return new EndpointResult.Immediate(List.of(values));
    }
}

package dev.propulsionteam.computed.integration.aeronautics;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Adds an {@code aeronautics:telemetry} endpoint that reports the nearest Create
 * contraption (for example a flying Create Aeronautics structure) around the computer.
 *
 * <p>Mirrors the reflective, dependency-free pattern of the Create integration: Create is
 * only loaded at runtime, so classes are never referenced directly.
 *
 * <p>Besides the graph endpoint the same measurement is exposed to attached CC computers
 * through the {@code computed} peripheral's {@code measure()} method, so foreign programs
 * can pull the data and process it further.
 */
public final class AeronauticsIntegration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    /**
     * One measurement per computer per game tick. Individual telemetry nodes (one per value)
     * call {@code measure} every tick, so caching keeps a graph with many of them cheap.
     * Weak keys let entries die with their computer block entity.
     */
    private static final WeakHashMap<ComputerBlockEntity, CacheEntry> CACHE = new WeakHashMap<>();

    private record CacheEntry(long gameTime, Map<String, Object> fields) {}

    private AeronauticsIntegration() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("aeronautics:telemetry", endpoint -> endpoint.method(
                        "measure",
                        EndpointSignature.of(List.of(), List.of(EndpointType.TABLE)),
                        EndpointPolicy.serverThread(false, true),
                        AeronauticsIntegration::measure,
                        ignored -> new EndpointResult.Immediate(List.of(previewTable())),
                        "Measures the nearest Create contraption around the computer and " +
                                "returns a table with: present, name, source; position x/y/z; " +
                                "linear velocity vx/vy/vz; speed (absolute linear speed); " +
                                "angular velocity wx/wy/wz; attitude " +
                                "pitch/roll; heading; heading frame hx/hy/hz; right frame " +
                                "rx/ry/rz; and distance. The same measurement is shared by " +
                                "every contraption telemetry node and cached once per " +
                                "computer per tick."));
    }

    /**
     * Measures the telemetry for a computer. Used by the {@code aeronautics:telemetry}
     * endpoint and by the {@code computed} CC peripheral's {@code measure()} method.
     * Returns a plain map of primitive values (number/boolean/String) so either caller can
     * convert it to its own Lua representation. Never null.
     */
    public static Map<String, Object> measureData(ComputerBlockEntity computer) {
        Level level = computer.getLevel();
        if (level == null || level.isClientSide) {
            return previewFields();
        }
        long gameTime = level.getGameTime();
        CacheEntry cached = CACHE.get(computer);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.fields();
        }
        AeronauticsAccess.Sample sample =
                AeronauticsAccess.sample(level, computer.getBlockPos());
        Map<String, Object> fields = sample != null ? fields(sample) : previewFields();
        CACHE.put(computer, new CacheEntry(gameTime, fields));
        return fields;
    }

    private static EndpointResult measure(EndpointInvocation invocation) {
        return EndpointResult.immediate(toLua(measureData(requireComputer(invocation))));
    }

    private static ComputerBlockEntity requireComputer(EndpointInvocation invocation) {
        if (invocation.host() instanceof ComputerBlockEntity computer && computer.getLevel() != null) {
            return computer;
        }
        throw new IllegalStateException("Aeronautics endpoints require a server computer");
    }

    private static LuaTable toLua(Map<String, Object> fields) {
        LuaTable table = new LuaTable();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            table.set(entry.getKey(), toLuaValue(entry.getValue()));
        }
        return table;
    }

    private static LuaValue toLuaValue(Object value) {
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool.booleanValue());
        }
        if (value instanceof String string) {
            return LuaValue.valueOf(string);
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    private static LuaTable previewTable() {
        return toLua(previewFields());
    }

    private static Map<String, Object> previewFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("present", false);
        fields.put("name", "");
        fields.put("source", "");
        for (String key : new String[] {
                "x", "y", "z", "vx", "vy", "vz", "wx", "wy", "wz", "pitch", "roll", "distance",
                "heading", "hx", "hy", "hz", "rx", "ry", "rz", "speed"}) {
            fields.put(key, 0.0D);
        }
        return fields;
    }

    private static Map<String, Object> fields(AeronauticsAccess.Sample sample) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("present", true);
        fields.put("name", sample.name());
        fields.put("x", sample.x());
        fields.put("y", sample.y());
        fields.put("z", sample.z());
        fields.put("vx", sample.vx());
        fields.put("vy", sample.vy());
        fields.put("vz", sample.vz());
        fields.put("speed", sample.speed());
        fields.put("wx", sample.wx());
        fields.put("wy", sample.wy());
        fields.put("wz", sample.wz());
        fields.put("pitch", sample.pitch());
        fields.put("roll", sample.roll());
        fields.put("distance", sample.distance());
        fields.put("heading", sample.heading());
        fields.put("hx", sample.hx());
        fields.put("hy", sample.hy());
        fields.put("hz", sample.hz());
        fields.put("rx", sample.rx());
        fields.put("ry", sample.ry());
        fields.put("rz", sample.rz());
        fields.put("source", sample.source());
        return fields;
    }
}
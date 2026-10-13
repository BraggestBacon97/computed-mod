package dev.propulsionteam.computed.lua.fs;

import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointRuntimeLifecycle;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * {@code computed:fs} — per-computer isolated filesystem, shaped after CraftOS {@code fs}.
 * Roots live under the world save; Lua never sees Java paths.
 */
public final class FsEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final ComputerFsService SERVICE = new ComputerFsService();
    private static final String COMPUTER_BLOCK_ENTITY =
            "dev.propulsionteam.computed.content.blocks.ComputerBlockEntity";

    private FsEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        EndpointPolicy policy = EndpointPolicy.computerThread(true, false);
        ComputedEndpoints.register("computed:fs", endpoint -> endpoint
                .method(
                        "list",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.TABLE)),
                        policy,
                        invocation -> {
                            List<String> names = requireFs(invocation).list(argument(invocation, 0));
                            return EndpointResult.immediate(tableOf(names));
                        },
                        null,
                        "Lists entry names inside a directory on the computer filesystem.")
                .method(
                        "exists",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireFs(invocation).exists(argument(invocation, 0)))),
                        null,
                        "Reports whether a path exists on the computer filesystem.")
                .method(
                        "is_dir",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireFs(invocation).isDirectory(argument(invocation, 0)))),
                        null,
                        "Reports whether a path is a directory.")
                .method(
                        "is_read_only",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> EndpointResult.immediate(LuaValue.FALSE),
                        null,
                        "Reports whether a path is read-only; the computer filesystem is always writable.")
                .method(
                        "get_size",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        policy,
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                (double) requireFs(invocation).size(argument(invocation, 0)))),
                        null,
                        "Returns the size of a file in bytes.")
                .method(
                        "get_free_space",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        policy,
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                (double) requireFs(invocation).freeSpace())),
                        null,
                        "Returns remaining quota bytes for the computer filesystem.")
                .method(
                        "make_dir",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> {
                            requireFs(invocation).makeDirectory(argument(invocation, 0));
                            return EndpointResult.immediate(LuaValue.TRUE);
                        },
                        null,
                        "Creates a directory (and parents); succeeds if it already exists.")
                .method(
                        "move",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.STRING),
                                List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> {
                            requireFs(invocation).move(argument(invocation, 0), argument(invocation, 1));
                            return EndpointResult.immediate(LuaValue.TRUE);
                        },
                        null,
                        "Moves or renames a file on the computer filesystem.")
                .method(
                        "copy",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.STRING),
                                List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> {
                            requireFs(invocation).copy(argument(invocation, 0), argument(invocation, 1));
                            return EndpointResult.immediate(LuaValue.TRUE);
                        },
                        null,
                        "Copies a file on the computer filesystem.")
                .method(
                        "delete",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        policy,
                        invocation -> {
                            requireFs(invocation).delete(argument(invocation, 0));
                            return EndpointResult.immediate(LuaValue.TRUE);
                        },
                        null,
                        "Deletes a file or directory tree; missing paths succeed.")
                .method(
                        "combine",
                        new EndpointSignature(List.of(), List.of(EndpointType.STRING), true),
                        policy,
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(combine(invocation.arguments()))),
                        null,
                        "Joins path segments into a normalized computer path.")
                .method(
                        "get_dir",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.STRING)),
                        policy,
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(parentDirectory(argument(invocation, 0)))),
                        null,
                        "Returns the parent directory of a path.")
                .method(
                        "get_name",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.STRING)),
                        policy,
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(lastSegment(argument(invocation, 0)))),
                        null,
                        "Returns the final segment of a path.")
                .method(
                        "get_id",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.computerThread(false, false),
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(requireFs(invocation).numericId())),
                        null,
                        "Returns this computer's numeric ID (the folder name under computed/computer).")
                .method(
                        "open",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.STRING),
                                List.of(EndpointType.TABLE)),
                        policy,
                        invocation -> EndpointResult.immediate(
                                openHandleTable(invocation, requireFs(invocation).open(
                                        argument(invocation, 0), argument(invocation, 1)))),
                        null,
                        "Opens a file (modes r, w, a, rb, wb, ab) and returns a handle table."));
        EndpointRuntimeLifecycle.register(new EndpointRuntimeLifecycle.Listener() {
            @Override
            public void tick(UUID computerId, Object host) {
                if (host != null && COMPUTER_BLOCK_ENTITY.equals(host.getClass().getName())) {
                    ComputerFsRoots.attach(SERVICE, computerId, host);
                }
            }

            @Override
            public void unload(UUID computerId, Object host) {
                SERVICE.detachComputer(computerId);
            }
        });
    }

    /** Exposed for tests: the shared service behind {@code computed:fs}. */
    public static ComputerFsService service() {
        return SERVICE;
    }

    private static ComputerFsService.ComputerFs requireFs(EndpointInvocation invocation) {
        if (invocation.preview()) {
            throw new LuaError("The filesystem is unavailable in previews");
        }
        try {
            return SERVICE.require(invocation.computerId());
        } catch (ComputerFsException exception) {
            throw new LuaError(exception.getMessage());
        }
    }

    private static String argument(EndpointInvocation invocation, int index) {
        return invocation.arguments().get(index).checkjstring();
    }

    private static LuaTable tableOf(List<String> names) {
        LuaTable table = new LuaTable();
        for (int index = 0; index < names.size(); index++) {
            table.set(index + 1, LuaValue.valueOf(names.get(index)));
        }
        return table;
    }

    private static LuaTable openHandleTable(EndpointInvocation invocation, ComputerFsService.ComputerFs.Handle handle) {
        LuaTable table = new LuaTable();
        table.set("path", LuaValue.valueOf(handle.path()));
        table.set("mode", LuaValue.valueOf(handle.mode()));
        table.set("close", handleFunction(table, handle, "close", args -> {
            handle.close();
            return LuaValue.NIL;
        }));
        table.set("flush", handleFunction(table, handle, "flush", args -> {
            handle.flush();
            return LuaValue.NIL;
        }));
        table.set("read_line", handleFunction(table, handle, "read_line", args -> {
            String line = handle.readLine();
            return line == null ? LuaValue.NIL : LuaValue.valueOf(line);
        }));
        table.set("read_all", handleFunction(table, handle, "read_all", args -> {
            if (handle.atEnd()) return LuaValue.NIL;
            return LuaValue.valueOf(handle.readAll());
        }));
        table.set("read", handleFunction(table, handle, "read", args -> {
            int count = args.arg(1).isint() ? args.arg(1).checkint() : 1;
            String text = handle.read(count);
            return text == null ? LuaValue.NIL : LuaValue.valueOf(text);
        }));
        table.set("read_bytes", handleFunction(table, handle, "read_bytes", args -> {
            int count = args.arg(1).isint() ? args.arg(1).checkint() : 1;
            byte[] bytes = handle.readBytes(count);
            return bytes == null
                    ? LuaValue.NIL
                    : LuaValue.valueOf(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));
        }));
        table.set("write", handleFunction(table, handle, "write", args -> {
            handle.write(args.arg(1).checkjstring());
            return LuaValue.NIL;
        }));
        table.set("write_line", handleFunction(table, handle, "write_line", args -> {
            handle.writeLine(args.arg(1).checkjstring());
            return LuaValue.NIL;
        }));
        table.set("seek", handleFunction(table, handle, "seek", args -> {
            String whence = args.arg(1).isnil() ? "set" : args.arg(1).checkjstring();
            int offset = args.arg(2).isnil() ? 0 : args.checkint(2);
            handle.seek(whence, offset);
            return LuaValue.valueOf(handle.position());
        }));
        return table;
    }

    @FunctionalInterface
    private interface HandleCall {
        LuaValue invoke(Varargs args);
    }

    private static LuaValue handleFunction(
            LuaTable owner, ComputerFsService.ComputerFs.Handle handle, String name, HandleCall call) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                // Support both handle.read_line() and handle:read_line().
                Varargs shifted = args.arg1() == owner ? args.subargs(2) : args;
                try {
                    return call.invoke(shifted);
                } catch (ComputerFsException exception) {
                    throw new LuaError("fs handle " + name + " failed: " + exception.getMessage());
                }
            }
        };
    }

    private static String combine(List<LuaValue> arguments) {
        StringBuilder builder = new StringBuilder();
        for (LuaValue argument : arguments) {
            String segment = argument.checkjstring().replace('\\', '/');
            if (segment.startsWith("/")) {
                builder.setLength(0);
            }
            for (String part : segment.split("/")) {
                if (part.isEmpty() || part.equals(".")) continue;
                if (part.equals("..")) {
                    int slash = builder.lastIndexOf("/");
                    if (slash < 0) builder.setLength(0);
                    else builder.setLength(slash);
                    continue;
                }
                if (builder.length() > 0) builder.append('/');
                builder.append(part);
            }
        }
        return builder.toString();
    }

    private static String parentDirectory(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? (slash == 0 ? "/" : "") : normalized.substring(0, slash);
    }

    private static String lastSegment(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }
}

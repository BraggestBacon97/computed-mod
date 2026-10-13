package dev.propulsionteam.computed.lua.net;

import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointPolicy;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointRuntimeLifecycle;
import dev.propulsionteam.computed.lua.endpoint.EndpointSignature;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * {@code computed:net} — rednet-style messaging between computers addressed by their numeric
 * computer ID. All methods run on the server thread; {@code receive} waits for the next message
 * or the timeout without blocking the tick.
 */
public final class NetEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final ComputerNetwork NETWORK = new ComputerNetwork();
    private static final double MAX_RECEIVE_TIMEOUT_SECONDS = 600;

    private NetEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        EndpointPolicy policy = EndpointPolicy.serverThread(true, false);
        ComputedEndpoints.register("computed:net", endpoint -> endpoint
                .method(
                        "send",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER, EndpointType.STRING),
                                List.of(EndpointType.BOOLEAN)),
                        policy,
                        NetEndpoints::send,
                        null,
                        "Queues a message for one computer ID and reports whether the target is online.")
                .method(
                        "broadcast",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        policy,
                        NetEndpoints::broadcast,
                        null,
                        "Queues a message for every online computer except this one and returns how many were reached.")
                .method(
                        "receive",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER),
                                List.of(EndpointType.ANY, EndpointType.ANY)),
                        policy,
                        NetEndpoints::receive,
                        null,
                        "Waits for the next message; returns sender and message, or nil on timeout (0 waits forever)."));
        EndpointRuntimeLifecycle.register(new EndpointRuntimeLifecycle.Listener() {
            @Override
            public void tick(UUID computerId, Object host) {
                if (host instanceof BuiltinEndpointHost builtin) {
                    int numericId = builtin.numericComputerId();
                    if (numericId >= 0) {
                        NETWORK.touch(numericId);
                    }
                }
            }
        });
    }

    private static EndpointResult send(EndpointInvocation invocation) {
        int target = invocation.arguments().get(0).checkint();
        if (target < 0) {
            throw new LuaError("Target computer ID must not be negative");
        }
        boolean online = NETWORK.send(computerId(invocation), target, message(invocation, 1));
        return EndpointResult.immediate(LuaValue.valueOf(online));
    }

    private static EndpointResult broadcast(EndpointInvocation invocation) {
        int reached =
                NETWORK.broadcast(computerId(invocation), message(invocation, 0));
        return EndpointResult.immediate(LuaValue.valueOf(reached));
    }

    private static EndpointResult receive(EndpointInvocation invocation) {
        double timeout = invocation.arguments().get(0).checkdouble();
        if (timeout < 0 || timeout > MAX_RECEIVE_TIMEOUT_SECONDS) {
            throw new LuaError(
                    "Timeout must be between 0 and " + (int) MAX_RECEIVE_TIMEOUT_SECONDS + " seconds");
        }
        int computerId = computerId(invocation);
        ComputerNetwork.Message queued = NETWORK.poll(computerId);
        if (queued != null) {
            return immediateMessage(queued);
        }
        CompletableFuture<EndpointResult.Immediate> future = new CompletableFuture<>();
        NETWORK.await(computerId, new ComputerNetwork.MessageSink() {
            @Override
            public boolean deliver(int sender, String text) {
                return future.complete(immediateMessage(new ComputerNetwork.Message(sender, text)));
            }

            @Override
            public boolean stale() {
                return future.isDone();
            }
        });
        if (timeout > 0) {
            CompletableFuture.runAsync(
                    () -> future.complete(EndpointResult.immediate(LuaValue.NIL, LuaValue.NIL)),
                    CompletableFuture.delayedExecutor(
                            (long) (timeout * 1000.0), TimeUnit.MILLISECONDS));
        }
        return EndpointResult.yielded(future);
    }

    private static EndpointResult.Immediate immediateMessage(ComputerNetwork.Message message) {
        return EndpointResult.immediate(
                LuaValue.valueOf(message.sender()), LuaValue.valueOf(message.text()));
    }

    private static String message(EndpointInvocation invocation, int index) {
        String message = invocation.arguments().get(index).checkjstring();
        if (message.length() > ComputerNetwork.MAX_MESSAGE_CHARS) {
            throw new LuaError(
                    "Message too long (max " + ComputerNetwork.MAX_MESSAGE_CHARS + " characters)");
        }
        return message;
    }

    private static int computerId(EndpointInvocation invocation) {
        if (invocation.host() instanceof BuiltinEndpointHost host) {
            int numericId = host.numericComputerId();
            if (numericId >= 0) {
                return numericId;
            }
        }
        throw new LuaError("Computer host is unavailable");
    }
}

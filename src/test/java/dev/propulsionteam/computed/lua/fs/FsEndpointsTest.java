package dev.propulsionteam.computed.lua.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints;
import dev.propulsionteam.computed.lua.endpoint.EndpointInvocation;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.EndpointType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FsEndpointsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void registersEveryCraftOsStyleMethod() {
        BuiltinEndpoints.register();
        var definition = ComputedEndpoints.find("computed:fs").orElseThrow();
        assertTrue(definition.methods().keySet().containsAll(Set.of(
                "list",
                "exists",
                "is_dir",
                "is_read_only",
                "get_size",
                "get_free_space",
                "make_dir",
                "move",
                "copy",
                "delete",
                "combine",
                "get_dir",
                "get_name",
                "get_id",
                "open")));
    }

    @Test
    void getIdReturnsTheNumericComputerId() throws Exception {
        BuiltinEndpoints.register();
        var definition = ComputedEndpoints.find("computed:fs").orElseThrow();
        var method = definition.methods().get("get_id");
        assertEquals(List.of(EndpointType.NUMBER), method.signature().returns());

        UUID computerId = UUID.nameUUIDFromBytes("get-id-test".getBytes(StandardCharsets.UTF_8));
        FsEndpoints.service().attachComputer(computerId, 42, temporaryDirectory.resolve("get-id"));
        EndpointInvocation invocation = new EndpointInvocation(
                computerId, UUID.randomUUID(), "computed:fs", List.of(), false, null);

        var result = method.handler().invoke(invocation);
        assertTrue(result instanceof EndpointResult.Immediate);
        var value = ((EndpointResult.Immediate) result).values().get(0);
        assertTrue(value.isnumber());
        assertEquals(42, value.checkint());
    }
}

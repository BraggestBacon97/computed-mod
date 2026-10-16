package dev.propulsionteam.computed.client.docs;

import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.persistence.ComputedProgramPackage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Pre-built example graphs for the Docs screen, shipped as {@code .computed} archives under
 * {@code assets/computed/docs/}. The resource path mirrors the entry id with slashes, so
 * {@code computed:widget/checkbox} loads from {@code docs/computed/widget/checkbox.computed}.
 *
 * <p>The files are authored externally (editor export or generator, see
 * {@code NodeDocsExamplesTest}) — replacing an example never requires a code change.
 */
public final class NodeDocsExampleStore {
    /** listResources() paths must not end with a slash — MC rejects those outright. */
    private static final String LIST_PATH = "docs";

    private static final String RESOURCE_PREFIX = LIST_PATH + "/";
    private static final String EXTENSION = ".computed";

    private final Map<String, byte[]> archives;

    private NodeDocsExampleStore(Map<String, byte[]> archives) {
        this.archives = archives;
    }

    /** Reads every example archive from the given resource manager (client resources). */
    public static NodeDocsExampleStore load(ResourceManager resources) {
        Map<String, byte[]> archives = new HashMap<>();
        var found = resources.listResources(LIST_PATH, location -> location.getPath().endsWith(EXTENSION));
        dev.propulsionteam.computed.Computed.LOGGER.info(
                "[computed] docs examples: found {} .computed resources under '{}'",
                found.size(),
                LIST_PATH);
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            String stripped =
                    path.substring(RESOURCE_PREFIX.length(), path.length() - EXTENSION.length());
            // Examples are stored as docs/<namespace>/<name>.computed (see NodeDocsExamplesTest.fileFor),
            // e.g. docs/computed/math_add.computed. Strip the redundant namespace folder so the
            // id matches the docs entry (computed:math_add, not computed:computed/math_add).
            String namespacePrefix = location.getNamespace() + "/";
            if (stripped.startsWith(namespacePrefix)) {
                stripped = stripped.substring(namespacePrefix.length());
            }
            String id = location.getNamespace() + ":" + stripped;
            try (InputStream stream = entry.getValue().open()) {
                archives.put(id, stream.readAllBytes());
            } catch (IOException ignored) {
                // An unreadable example behaves like a missing one.
            }
        }
        return new NodeDocsExampleStore(Map.copyOf(archives));
    }

    public boolean has(String entryId) {
        return archives.containsKey(entryId);
    }

    /**
     * @throws IllegalArgumentException when the entry has no example or the archive is corrupt
     */
    public ComputedProgramV3 open(String entryId) {
        byte[] archive = archives.get(entryId);
        if (archive == null) {
            throw new IllegalArgumentException("No example graph for " + entryId);
        }
        return ComputedProgramPackage.importArchive(archive);
    }
}

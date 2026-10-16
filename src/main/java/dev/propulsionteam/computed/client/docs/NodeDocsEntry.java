package dev.propulsionteam.computed.client.docs;

/**
 * One documentation page for a bundled or integration node: short summary plus a runnable-looking
 * Lua example the player can copy into a custom node or read as a wiring recipe.
 */
public record NodeDocsEntry(
        String id, String title, String category, String summary, String example, String[] inputs, String[] outputs) {

    public NodeDocsEntry {
        inputs = inputs == null ? new String[0] : inputs;
        outputs = outputs == null ? new String[0] : outputs;
    }

    /** Human-readable category folder shown in the docs list, e.g. "Math" or "Widgets". */
    public String folder() {
        String path = category == null || category.isBlank() ? "utility" : category;
        int slash = path.indexOf('/');
        String leaf = slash < 0 ? path : path.substring(slash + 1);
        if (leaf.isEmpty()) {
            leaf = path;
        }
        return Character.toUpperCase(leaf.charAt(0)) + leaf.substring(1);
    }
}

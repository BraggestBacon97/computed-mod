package dev.propulsionteam.computed.content.items;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Channel list stored on a portable monitor: up to {@link #MAX_CHANNELS} linked computers plus the
 * active channel index. Kept free of ItemStack so the tag round trip stays unit-testable; the
 * ItemStack read/write lives on {@link PortableMonitorItem}.
 */
public final class PortableMonitorLinks {
    /** Key of the channel list tag inside the root tag (item {@code custom_data}). */
    public static final String LIST_KEY = "StoredLinks";
    /** Key of the active channel index inside the root tag. */
    public static final String ACTIVE_KEY = "ActiveLink";
    /** Legacy single-link key; migrated to a one-entry list on read. */
    public static final String LEGACY_KEY = "StoredLink";

    public static final int MAX_CHANNELS = 8;

    private PortableMonitorLinks() {}

    public static List<PortableLinkData> readList(CompoundTag root) {
        if (root == null) {
            return List.of();
        }
        if (!root.contains(LIST_KEY)) {
            if (root.contains(LEGACY_KEY)) {
                try {
                    return List.of(PortableLinkData.of(root.getCompound(LEGACY_KEY)));
                } catch (IllegalArgumentException exception) {
                    return List.of();
                }
            }
            return List.of();
        }
        ListTag list = root.getList(LIST_KEY, 10);
        List<PortableLinkData> links = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            try {
                links.add(PortableLinkData.of(list.getCompound(index)));
            } catch (IllegalArgumentException exception) {
                // Skip damaged entries.
            }
        }
        return links;
    }

    public static int readActive(CompoundTag root) {
        int size = readList(root).size();
        if (size == 0) {
            return 0;
        }
        return Math.max(0, Math.min(root.getInt(ACTIVE_KEY), size - 1));
    }

    public static void writeList(CompoundTag root, List<PortableLinkData> links, int active) {
        ListTag list = new ListTag();
        for (PortableLinkData link : links) {
            list.add(link.toTag());
        }
        root.put(LIST_KEY, list);
        root.putInt(ACTIVE_KEY, links.isEmpty() ? 0 : Math.max(0, Math.min(active, links.size() - 1)));
        root.remove(LEGACY_KEY);
    }

    /**
     * Appends the link (or reuses an existing identical entry) and returns the resulting list plus
     * the channel index. {@code index} is {@code -1} when a new link would exceed
     * {@link #MAX_CHANNELS}.
     */
    public record AddResult(List<PortableLinkData> links, int index) {}

    public static AddResult add(List<PortableLinkData> links, PortableLinkData link) {
        int existing = links.indexOf(link);
        if (existing >= 0) {
            return new AddResult(links, existing);
        }
        if (links.size() >= MAX_CHANNELS) {
            return new AddResult(links, -1);
        }
        List<PortableLinkData> next = new ArrayList<>(links);
        next.add(link);
        return new AddResult(next, next.size() - 1);
    }

    public static List<PortableLinkData> remove(List<PortableLinkData> links, PortableLinkData link) {
        List<PortableLinkData> next = new ArrayList<>(links);
        next.remove(link);
        return next;
    }
}

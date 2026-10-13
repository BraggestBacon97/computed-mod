package dev.propulsionteam.computed.content.items;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Binding stored on a link card: the position and dimension of the linked computer. Kept free of
 * ItemStack so the tag round trip stays unit-testable; item-level read/write lives on
 * {@link LinkCardItem}.
 */
public record PortableLinkData(int x, int y, int z, String dimension) {
    public PortableLinkData {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Link dimension must not be blank");
        }
    }

    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }

    public ResourceLocation dimensionId() {
        return ResourceLocation.parse(dimension);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putString("dimension", dimension);
        return tag;
    }

    public static PortableLinkData of(CompoundTag tag) {
        if (tag == null || !tag.contains("dimension")) {
            throw new IllegalArgumentException("Link tag is missing a dimension");
        }
        return new PortableLinkData(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getString("dimension"));
    }
}

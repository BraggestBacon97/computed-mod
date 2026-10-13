package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ComputedTags {
    public static final class Items {
        public static final TagKey<Item> PERIPHERAL =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "peripheral"));

        /** Legacy tag; datapacks usually pull in {@link #PERIPHERAL} via tag json. */
        public static final TagKey<Item> PERIPHERALS =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "peripherals"));
    }

    private ComputedTags() {}
}

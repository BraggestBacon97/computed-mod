package net.minecraft.nbt;

/** Test stub mirroring the game's NbtAccounter factory used by reflection-based tests. */
public final class NbtAccounter {
    private NbtAccounter() {}

    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter();
    }
}

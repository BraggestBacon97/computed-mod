package net.minecraft.core;

import java.util.Objects;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public class BlockPos {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    private final int x;
    private final int y;
    private final int z;

    public BlockPos() {
        this(0, 0, 0);
    }

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockPos other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "BlockPos[" + x + ", " + y + ", " + z + "]";
    }
}

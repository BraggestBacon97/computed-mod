package dev.propulsionteam.computed.content.monitors;

import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorBlockEntity extends BlockEntity {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorBlockEntity.class);

    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;

    /** Owner binding is cleared if not refreshed by the Peripheral node within this many ticks. */
    private static final int OWNER_EXPIRY_TICKS = 5;

    private static final String NBT_X = "XIndex";
    private static final String NBT_Y = "YIndex";
    private static final String NBT_WIDTH = "Width";
    private static final String NBT_HEIGHT = "Height";
    private static final String NBT_OWNER = "OwnerPos";
    private static final String NBT_DRAW_LIST = "DrawList";

    private int width = 1;
    private int height = 1;
    private int xIndex = 0;
    private int yIndex = 0;

    private boolean needsUpdate = false;

    @Nullable private BlockPos ownerComputerPos;
    private int ticksSinceOwnerRefresh = Integer.MAX_VALUE / 2;
    private WidgetDrawList currentDrawList = WidgetDrawList.EMPTY;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.MONITOR_BLOCK_ENTITY.get(), pos, state);
    }

    void destroy() {
        if (level != null && !level.isClientSide) contractNeighbours();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt(NBT_X, xIndex);
        tag.putInt(NBT_Y, yIndex);
        tag.putInt(NBT_WIDTH, width);
        tag.putInt(NBT_HEIGHT, height);
        if (ownerComputerPos != null) {
            tag.putLong(NBT_OWNER, ownerComputerPos.asLong());
        }
        if (!currentDrawList.isEmpty()) {
            tag.put(NBT_DRAW_LIST, currentDrawList.toNbt());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        xIndex = tag.getInt(NBT_X);
        yIndex = tag.getInt(NBT_Y);
        width = Math.max(1, tag.getInt(NBT_WIDTH));
        height = Math.max(1, tag.getInt(NBT_HEIGHT));
        ownerComputerPos = tag.contains(NBT_OWNER) ? BlockPos.of(tag.getLong(NBT_OWNER)) : null;
        currentDrawList = tag.contains(NBT_DRAW_LIST)
                ? WidgetDrawList.fromNbt(tag.getCompound(NBT_DRAW_LIST))
                : WidgetDrawList.EMPTY;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        tag.putInt(NBT_X, xIndex);
        tag.putInt(NBT_Y, yIndex);
        tag.putInt(NBT_WIDTH, width);
        tag.putInt(NBT_HEIGHT, height);
        if (ownerComputerPos != null) {
            tag.putLong(NBT_OWNER, ownerComputerPos.asLong());
        }
        tag.put(NBT_DRAW_LIST, currentDrawList.toNbt());
        return tag;
    }

    public void blockTick() {
        if (needsUpdate) {
            needsUpdate = false;
            expand();
        }
    }

    void updateNeighborsDeferred() {
        needsUpdate = true;
    }

    /** Server-side ticker: expires stale owner bindings so an orphaned monitor goes blank. */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MonitorBlockEntity be) {
        if (be.ownerComputerPos == null) return;
        if (be.xIndex != 0 || be.yIndex != 0) return;
        be.ticksSinceOwnerRefresh++;
        if (be.ticksSinceOwnerRefresh > OWNER_EXPIRY_TICKS) {
            be.ownerComputerPos = null;
            if (!be.currentDrawList.isEmpty()) {
                be.currentDrawList = WidgetDrawList.EMPTY;
            }
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // region Owner + draw list

    /** Called by {@code PeripheralNode.evaluate} each graph tick to claim/refresh ownership of this monitor. */
    public void bindOwner(BlockPos computerPos) {
        boolean changed = !computerPos.equals(this.ownerComputerPos);
        this.ownerComputerPos = computerPos;
        this.ticksSinceOwnerRefresh = 0;
        if (changed) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Nullable
    public BlockPos getOwnerComputerPos() {
        return ownerComputerPos;
    }

    /** Replaces the current draw list. Only marks dirty / re-syncs when the content hash actually changed. */
    public void setDrawList(WidgetDrawList list) {
        if (list == null) list = WidgetDrawList.EMPTY;
        if (list.hash() == currentDrawList.hash() && list.equals(currentDrawList)) return;
        this.currentDrawList = list;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public WidgetDrawList getDrawList() {
        return currentDrawList;
    }

    /** Public version of the internal origin lookup. Returns null if the origin chunk is not loaded. */
    @Nullable
    public MonitorBlockEntity findOrigin() {
        return getOrigin();
    }

    // endregion

    // region Sizing and placement

    public Direction getDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(MonitorBlock.FACING) ? state.getValue(MonitorBlock.FACING) : Direction.NORTH;
    }

    public Direction getOrientation() {
        BlockState state = getBlockState();
        return state.hasProperty(MonitorBlock.ORIENTATION) ? state.getValue(MonitorBlock.ORIENTATION) : Direction.NORTH;
    }

    public Direction getFront() {
        Direction orientation = getOrientation();
        return orientation == Direction.NORTH ? getDirection() : orientation;
    }

    public Direction getRight() {
        return getDirection().getCounterClockWise();
    }

    public Direction getDown() {
        Direction orientation = getOrientation();
        if (orientation == Direction.NORTH) return Direction.UP;
        return orientation == Direction.DOWN ? getDirection() : getDirection().getOpposite();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getXIndex() {
        return xIndex;
    }

    public int getYIndex() {
        return yIndex;
    }

    boolean isCompatible(MonitorBlockEntity other) {
        return getOrientation() == other.getOrientation() && getDirection() == other.getDirection();
    }

    private MonitorState getLoadedMonitor(int x, int y) {
        if (x == xIndex && y == yIndex) return MonitorState.present(this);
        BlockPos pos = toWorldPos(x, y);

        var world = getLevel();
        if (world == null || !world.isLoaded(pos)) return MonitorState.UNLOADED;

        var tile = world.getBlockEntity(pos);
        if (!(tile instanceof MonitorBlockEntity monitor)) return MonitorState.MISSING;

        return isCompatible(monitor) ? MonitorState.present(monitor) : MonitorState.MISSING;
    }

    @Nullable
    private MonitorBlockEntity getOrigin() {
        return getLoadedMonitor(0, 0).getMonitor();
    }

    public BlockPos toWorldPos(int x, int y) {
        if (xIndex == x && yIndex == y) return getBlockPos();
        return getBlockPos().relative(getRight(), -xIndex + x).relative(getDown(), -yIndex + y);
    }

    private void updateBlockState() {
        if (level == null) return;
        level.setBlock(getBlockPos(), getBlockState()
            .setValue(MonitorBlock.STATE, MonitorEdgeState.fromConnections(
                yIndex < height - 1, yIndex > 0,
                xIndex > 0, xIndex < width - 1)), Block.UPDATE_CLIENTS);
    }

    void resize(int width, int height) {
        xIndex = 0;
        yIndex = 0;
        this.width = width;
        this.height = height;

        BlockPos pos = getBlockPos();
        Direction down = getDown(), right = getRight();
        for (var x = 0; x < width; x++) {
            for (var y = 0; y < height; y++) {
                var other = getLevel().getBlockEntity(pos.relative(right, x).relative(down, y));
                if (!(other instanceof MonitorBlockEntity monitor) || !isCompatible(monitor)) continue;

                monitor.xIndex = x;
                monitor.yIndex = y;
                monitor.width = width;
                monitor.height = height;
                monitor.needsUpdate = false;
                monitor.updateBlockState();
                monitor.setChanged();
                if (level != null) {
                    level.sendBlockUpdated(monitor.getBlockPos(), monitor.getBlockState(), monitor.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    void expand() {
        var monitor = getOrigin();
        if (monitor != null && monitor.xIndex == 0 && monitor.yIndex == 0) new Expander(monitor).expand();
    }

    private void contractNeighbours() {
        if (width == 1 && height == 1) return;

        BlockPos pos = getBlockPos();
        Direction down = getDown(), right = getRight();
        BlockPos origin = toWorldPos(0, 0);

        MonitorBlockEntity toLeft = null, toAbove = null, toRight = null, toBelow = null;
        if (xIndex > 0) toLeft = tryResizeAt(pos.relative(right, -xIndex), xIndex, 1);
        if (yIndex > 0) toAbove = tryResizeAt(origin, width, yIndex);
        if (xIndex < width - 1) toRight = tryResizeAt(pos.relative(right, 1), width - xIndex - 1, 1);
        if (yIndex < height - 1) {
            toBelow = tryResizeAt(origin.relative(down, yIndex + 1), width, height - yIndex - 1);
        }

        if (toLeft != null) toLeft.expand();
        if (toAbove != null) toAbove.expand();
        if (toRight != null) toRight.expand();
        if (toBelow != null) toBelow.expand();
    }

    @Nullable
    private MonitorBlockEntity tryResizeAt(BlockPos pos, int width, int height) {
        var tile = getLevel().getBlockEntity(pos);
        if (tile instanceof MonitorBlockEntity monitor && isCompatible(monitor)) {
            monitor.resize(width, height);
            return monitor;
        }
        return null;
    }
    // endregion
}

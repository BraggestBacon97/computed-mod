package dev.propulsionteam.computed.integration.aeronautics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reads telemetry for the nearest moving Create/Aeronautics contraption around the
 * computer.
 *
 * <p>In Create + Create Aeronautics on Sable, contraptions are moved into hidden
 * Sable "sub-levels" (physics worlds) and are NOT reported by the normal
 * {@code level.getEntitiesOfClass(...)} query. We therefore enumerate the Sable
 * sub-levels of the server level directly and read pose/velocity/name from the
 * nearest one. A fallback scan of plain {@code AbstractContraptionEntity}s handles
 * classic (non-Sable) contraptions.
 *
 * <p>The measurement mirrors the stock Create Aeronautics sensors: velocity is the
 * per-tick difference of the block's world position transformed through the
 * sub-level's current ({@code logicalPose()}) and previous ({@code lastPose()})
 * pose (× 20 → blocks/second), and orientation is read from the sub-level's
 * {@code logicalPose()} rotation exactly like the Gimbal Sensor (world "down"
 * vector transformed back into ship-local space → pitch/roll).
 *
 * <p>Sable is runtime-only here (through {@code libs/}), so every Sable class is
 * reached via reflection; Create reaches the fallback path also by reflection.
 */
final class AeronauticsAccess {
    private static final double SEARCH_RADIUS = 256.0D;

    /** Linear speed (blocks/second) above which a sub-level counts as moving. */
    private static final double MOVE_THRESHOLD = 0.1D;

    private static final double TPS = 20.0D;

    private static final String SUB_LEVEL_CONTAINER =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    private static final String ABSTRACT_CONTRAPTION_ENTITY =
            "com.simibubi.create.content.contraptions.AbstractContraptionEntity";

    private AeronauticsAccess() {}

    /** A snapshot of the nearest contraption around {@code pos}, or null when none is found. */
    static Sample sample(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (level instanceof ServerLevel server && !level.isClientSide) {
            Sample sub = sampleSubLevels(server, pos);
            if (sub != null) {
                return sub;
            }
        }
        Sample contraption = sampleContraptions(level, pos);
        return contraption;
    }

    /** Returns the Sable sub-level of the nearest contraption around {@code pos}, or null. */
    private static Sample sampleSubLevels(ServerLevel server, BlockPos pos) {
        Object container = subLevelContainer(server);
        if (container == null) {
            return null;
        }
        List<Object> subs = allSubLevels(container);
        if (subs == null || subs.isEmpty()) {
            return null;
        }
        Object self = selfSubLevel(server, pos);
        if (self == null) {
            self = subLevelContaining(subs, pos);
        }
        Object chosen = chooseSubLevel(subs, self, pos);
        if (chosen == null) {
            return null;
        }
        return toSample(server, chosen, pos);
    }

    /**
     * Picks the sub-level to report. A computer riding a contraption measures the
     * sub-level it stands on. A computer on a static ground build would otherwise read
     * its own unmoving sub-level forever, so in that case the nearest sub-level that
     * actually holds or moves a contraption wins, ranked on world positions.
     */
    private static Object chooseSubLevel(List<Object> subs, Object self, BlockPos pos) {
        if (self != null && contraption(self) != null) {
            return self;
        }
        if (self != null && subSpeed(self) > MOVE_THRESHOLD) {
            return self;
        }
        Object bestContraption = null;
        double bestContraptionDistance = Double.MAX_VALUE;
        Object bestMoving = null;
        double bestMovingDistance = Double.MAX_VALUE;
        Object bestOther = null;
        double bestOtherDistance = Double.MAX_VALUE;
        for (Object sub : subs) {
            if (sub == self) {
                continue;
            }
            double[] anchor = effectivePosition(sub);
            if (anchor == null) {
                continue;
            }
            double distance = distanceSqr(anchor[0], anchor[1], anchor[2], pos);
            if (contraption(sub) != null && distance < bestContraptionDistance) {
                bestContraptionDistance = distance;
                bestContraption = sub;
            }
            if (subSpeed(sub) > MOVE_THRESHOLD && distance < bestMovingDistance) {
                bestMovingDistance = distance;
                bestMoving = sub;
            }
            if (distance < bestOtherDistance) {
                bestOtherDistance = distance;
                bestOther = sub;
            }
        }
        if (bestContraption != null) {
            return bestContraption;
        }
        if (bestMoving != null) {
            return bestMoving;
        }
        if (bestOther != null) {
            return bestOther;
        }
        return self;
    }

    /** Position used for nearest-sub-level ranking: the contraption's live pos if any. */
    private static double[] effectivePosition(Object sub) {
        Object contraption = contraption(sub);
        if (contraption != null) {
            double[] p = contraptionPosition(contraption);
            if (p != null) {
                return p;
            }
        }
        return subPosition(sub);
    }

    /** Absolute linear speed of the sub-level (blocks/second), 0 when unreadable. */
    private static double subSpeed(Object sub) {
        double[] velocity = linearVelocityField(sub);
        if (velocity == null) {
            return 0.0D;
        }
        return Math.sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2]);
    }

    /**
     * The sub-level containing the block's world position — i.e. the ship the computer
     * block itself stands on. Uses {@code Sable.HELPER.getContaining(level, x, z)}, which
     * maps the block's world coordinates onto the sub-level at that spot (the "plot").
     */
    private static Object selfSubLevel(ServerLevel server, BlockPos pos) {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Field helper = sable.getField("HELPER");
            Object companion = helper.get(null);
            if (companion == null) {
                return null;
            }
            Method getContaining = companion.getClass().getMethod(
                    "getContaining", Level.class, double.class, double.class);
            return getContaining.invoke(companion, server,
                    pos.getX() + 0.5D, pos.getZ() + 0.5D);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Picks the sub-level whose world bounding box contains the computer's true world
     * position (the one the block stands on). The computer's {@link BlockPos} is
     * plot-local when riding a ship, so it is first mapped through the sub-level's live
     * pose into world coordinates; a static fallback picks the nearest sub-level.
     */
    private static Object subLevelContaining(List<Object> subs, BlockPos pos) {
        Object nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Object sub : subs) {
            double[] mapped = mappedPosition(sub, pos);
            if (mapped != null && subContains(sub, mapped[0], mapped[1], mapped[2])) {
                return sub;
            }
            double[] anchor = subPosition(sub);
            if (anchor == null) {
                continue;
            }
            double distance = distanceSqr(anchor[0], anchor[1], anchor[2], pos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = sub;
            }
        }
        return nearest;
    }

    private static Sample toSample(ServerLevel server, Object sub, BlockPos pos) {
        double[] position = subPosition(sub);
        if (position == null) {
            return null;
        }
        double[] velocity = subVelocity(server, sub, pos);
        double[] angularVelocity = subAngularVelocity(sub);
        double[] basis = subBasis(sub);
        double hx = 0;
        double hy = 0;
        double hz = 1;
        double rx = -1;
        double ry = 0;
        double rz = 0;
        if (basis != null && basis.length >= 6) {
            hx = basis[0];
            hy = basis[1];
            hz = basis[2];
            rx = basis[3];
            ry = basis[4];
            rz = basis[5];
        }
        double[] tilt = subTilt(sub);
        return new Sample(
                position[0], position[1], position[2],
                velocity != null ? velocity[0] : 0.0D,
                velocity != null ? velocity[1] : 0.0D,
                velocity != null ? velocity[2] : 0.0D,
                angularVelocity != null ? angularVelocity[0] : 0.0D,
                angularVelocity != null ? angularVelocity[1] : 0.0D,
                angularVelocity != null ? angularVelocity[2] : 0.0D,
                tilt != null ? tilt[0] : 0.0D,
                tilt != null ? tilt[1] : 0.0D,
                Math.sqrt(distanceSqr(position[0], position[1], position[2], pos)),
                subName(sub),
                headingDeg(basis),
                hx, hy, hz, rx, ry, rz,
                "sublevel");
    }

    private static double headingDeg(double[] basis) {
        if (basis == null || basis.length < 2) {
            return 0.0D;
        }
        double yaw = Math.toDegrees(Math.atan2(-basis[0], basis[2]));
        return normalizedYaw(yaw);
    }

    /** Whether the sub-level's {@code boundingBox()} contains the point, via reflection. */
    private static boolean subContains(Object sub, double x, double y, double z) {
        try {
            Method boxMethod = sub.getClass().getMethod("boundingBox");
            Object box = boxMethod.invoke(sub);
            if (box == null) {
                return false;
            }
            Method contains = box.getClass().getMethod("contains", double.class, double.class, double.class);
            Object result = contains.invoke(box, x, y, z);
            return result != null && ((Boolean) result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static Object subLevelContainer(ServerLevel server) {
        try {
            Class<?> clazz = Class.forName(SUB_LEVEL_CONTAINER);
            Method get = clazz.getMethod("getContainer", ServerLevel.class);
            return get.invoke(null, server);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static List<Object> allSubLevels(Object container) {
        try {
            Method all = container.getClass().getMethod("getAllSubLevels");
            Object result = all.invoke(container);
            if (!(result instanceof List<?> list)) {
                return null;
            }
            List<Object> out = new ArrayList<>();
            for (Object element : list) {
                if (element != null) {
                    out.add(element);
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Returns the first valid Create/Aeronautics contraption of the sub-level's plot,
     * or null. The plot is a {@code ServerLevelPlot} at runtime, whose
     * {@code getContraptions()} returns the {@code KinematicContraption}s of the ship.
     * Used to pick the sub-level a ground computer should measure (the flying
     * contraption) and to rank sub-levels when several ships exist.
     */
    private static Object contraption(Object sub) {
        try {
            Object plot = sub.getClass().getMethod("getPlot").invoke(sub);
            if (plot == null) {
                return null;
            }
            Method get = findMethod(plot.getClass(), "getContraptions");
            if (get == null) {
                return null;
            }
            Object collection = get.invoke(plot);
            if (!(collection instanceof java.util.Collection<?> contraptions)) {
                return null;
            }
            for (Object contraption : contraptions) {
                if (contraption != null && isValidContraption(contraption)) {
                    return contraption;
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** Whether the contraption is a live ship ({@code sable$isValid()}). */
    private static boolean isValidContraption(Object contraption) {
        try {
            Object ok = contraption.getClass().getMethod("sable$isValid").invoke(contraption);
            return ok instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /** First non-null public method {@code name}, searching {@code type} up its superclasses. */
    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** Current world position [x, y, z] of the contraption, or null. */
    private static double[] contraptionPosition(Object contraption) {
        try {
            Method pos = contraption.getClass().getMethod("sable$getPosition");
            Object vector = pos.invoke(contraption);
            return vector != null ? vector3d(vector) : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Returns [x, y, z] of the sub-level's current world origin, or null. Read from
     * {@code logicalPose()} — Sable writes the live physics transform into this pose.
     */
    private static double[] subPosition(Object sub) {
        Object pose = livePose(sub);
        if (pose == null) {
            return null;
        }
        try {
            Method positionMethod = pose.getClass().getMethod("position");
            Object vector = positionMethod.invoke(pose);
            return vector3d(vector);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** The sub-level's current pose ({@code logicalPose()}), else the last tick animation pose. */
    private static Object livePose(Object sub) {
        for (String name : new String[] {"logicalPose", "lastPose"}) {
            try {
                Method poseMethod = sub.getClass().getMethod(name);
                Object pose = poseMethod.invoke(sub);
                if (pose != null) {
                    return pose;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try next source
            }
        }
        return null;
    }

    /**
     * The world position a block at plot-local {@code pos} has inside the sub-level's
     * moving frame: the point is transformed through the sub-level's live pose. Returns
     * null when the pose or transformation is unavailable.
     */
    private static double[] mappedPosition(Object sub, BlockPos pos) {
        Object pose = livePose(sub);
        if (pose == null) {
            return null;
        }
        Object mapped = transformPosition(
                pose, new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
        return mapped instanceof Vector3d vector ? new double[] {vector.x, vector.y, vector.z} : null;
    }

    /**
     * Returns [vx, vy, vz] of the sub-level's absolute linear velocity in blocks/second,
     * or null. Mirrors the Create Aeronautics velocity sensor: the block's position is
     * transformed through the current ({@code logicalPose()}) and previous tick
     * ({@code lastPose()}) pose, the per-tick difference is scaled by 20.
     */
    private static double[] subVelocity(ServerLevel server, Object sub, BlockPos pos) {
        try {
            Method logical = sub.getClass().getMethod("logicalPose");
            Method last = sub.getClass().getMethod("lastPose");
            Object poseNow = logical.invoke(sub);
            Object posePrev = last.invoke(sub);
            if (poseNow != null && posePrev != null) {
                Vector3d point = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                Object now = transformPosition(poseNow, point);
                Object prev = transformPosition(posePrev, point);
                if (now instanceof Vector3d n && prev instanceof Vector3d p) {
                    n.sub(p).mul(TPS);
                    return new double[] {n.x, n.y, n.z};
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // fall through to the Sable velocity field below
        }
        return linearVelocityField(sub);
    }

    /** {@code pose.transformPosition(point, tmp)} via reflection (Pose3d is a Sable class).
     *
     * <p>The method is declared on the {@code Pose3dc} interface with a read-only
     * {@code Vector3dc} source, so an exact {@code getMethod(name, Vector3d.class, …)}
     * lookup fails. We therefore search by name and accept any parameter type that is
     * assignable from {@link Vector3d} (i.e. {@code Vector3d} or {@code Vector3dc}).
     */
    private static Object transformPosition(Object pose, Vector3d point) {
        try {
            Class<?> type = pose.getClass();
            Method[] methods = type.getMethods();
            Method transform = null;
            for (Method method : methods) {
                if (!method.getName().equals("transformPosition")) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 2
                        || !parameters[0].isAssignableFrom(Vector3d.class)
                        || !parameters[1].isAssignableFrom(Vector3d.class)) {
                    continue;
                }
                transform = method;
                break;
            }
            if (transform == null) {
                return null;
            }
            return transform.invoke(pose, point, new Vector3d());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Returns [pitch, roll] of the sub-level in degrees, or null. Mirrors the Create
     * Aeronautics Gimbal Sensor: the world \"down\" direction, transformed back into the
     * contraption's local frame, yields pitch (rotation about local X) and roll (about
     * local Z) relative to the gravity plane.
     */
    private static double[] subTilt(Object sub) {
        Object pose = livePose(sub);
        if (pose == null) {
            return null;
        }
        try {
            Method orientationMethod = pose.getClass().getMethod("orientation");
            Object quaternion = orientationMethod.invoke(pose);
            if (!(quaternion instanceof Quaterniondc q)) {
                return null;
            }
            Vector3d localDown = q.transformInverse(new Vector3d(0, -1, 0));
            double x = localDown.x();
            double y = localDown.y();
            double z = localDown.z();
            double pitch = y < 0 || z * z > 0.001 ? Math.toDegrees(Math.atan2(z, -y)) : 0;
            double roll = y < 0 || x * x > 0.001 ? Math.toDegrees(Math.atan2(x, -y)) : 0;
            return new double[] {pitch, roll};
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** Reads the authoritative {@code latestLinearVelocity} field Sable keeps per tick. */
    private static double[] linearVelocityField(Object sub) {
        return vectorField(sub, "latestLinearVelocity");
    }

    /**
     * Returns [wx, wy, wz] of the sub-level's angular velocity in radians per second, or
     * null. Reads the authoritative {@code latestAngularVelocity} field Sable keeps on its
     * {@code ServerSubLevel} during physics ticks.
     */
    private static double[] subAngularVelocity(Object sub) {
        return vectorField(sub, "latestAngularVelocity");
    }

    private static double[] vectorField(Object sub, String fieldName) {
        try {
            Field field = sub.getClass().getField(fieldName);
            Object vector = field.get(sub);
            return vector != null ? vector3d(vector) : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static String subName(Object sub) {
        try {
            Method nameMethod = sub.getClass().getMethod("getName");
            Object name = nameMethod.invoke(sub);
            return name != null ? String.valueOf(name) : "";
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "";
        }
    }

    /**
     * Returns the contraption's horizontal basis in world coordinates:
     * {@code [hx, hy, hz, rx, ry, rz]} from the orientation quaternion of the current pose.
     * Returns null when not readable.
     */
    private static double[] subBasis(Object sub) {
        Object pose = livePose(sub);
        if (pose == null) {
            return null;
        }
        try {
            Method orientationMethod = pose.getClass().getMethod("orientation");
            Object quaternion = orientationMethod.invoke(pose);
            if (!(quaternion instanceof Quaterniondc q)) {
                return null;
            }
            Vector3d heading = new Vector3d(0, 0, 1).rotate(q);
            Vector3d right = new Vector3d(1, 0, 0).rotate(q);
            return new double[] {
                heading.x, heading.y, heading.z,
                right.x, right.y, right.z
            };
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static double[] vector3d(Object vector) {
        double x = asDouble(vector, "x");
        double y = asDouble(vector, "y");
        double z = asDouble(vector, "z");
        return new double[] {x, y, z};
    }

    private static double asDouble(Object vector, String field) {
        try {
            Field f = vector.getClass().getField(field);
            return f.getDouble(vector);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0.0D;
        }
    }

    private static double distanceSqr(double x, double y, double z, BlockPos pos) {
        double dx = x - (pos.getX() + 0.5D);
        double dy = y - (pos.getY() + 0.5D);
        double dz = z - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double normalizedYaw(double yaw) {
        return ((yaw % 360.0D) + 360.0D) % 360.0D;
    }

    /** Fallback: nearest plain Create contraption entity, or null. */
    private static Sample sampleContraptions(Level level, BlockPos pos) {
        Class<?> type = contraptionType();
        if (type == null) {
            return null;
        }
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        try {
            AABB bounds = AABB.ofSize(
                    Vec3.atCenterOf(pos), SEARCH_RADIUS * 2.0D, SEARCH_RADIUS * 2.0D, SEARCH_RADIUS * 2.0D);
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Entity> candidates =
                    (List<Entity>) level.getEntitiesOfClass((Class) type, bounds);
            for (Entity candidate : candidates) {
                double distance = candidate.distanceToSqr(
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
        } catch (RuntimeException exception) {
            return null;
        }
        if (nearest == null) {
            return null;
        }
        Vec3 position = nearest.position();
        Vec3 velocity = nearest.getDeltaMovement();
        double yaw = normalizedYaw(nearest.getYRot());
        double yawRad = Math.toRadians(yaw);
        double hx = -Math.sin(yawRad);
        double hz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);
        return new Sample(
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                0.0D, 0.0D, 0.0D,
                0.0D, 0.0D,
                Math.sqrt(nearestDistance),
                String.valueOf(nearest.getDisplayName().getString()),
                yaw,
                hx, 0.0D, hz, rx, 0.0D, rz,
                "entity");
    }

    private static Class<?> contraptionType() {
        try {
            return Class.forName(ABSTRACT_CONTRAPTION_ENTITY);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    /**
     * Snapshot of the nearest contraption. Field names match the Lua table produced by
     * the {@code aeronautics:telemetry} endpoint. Position and orientation are read from
     * Sable's live sub-level pose ({@code logicalPose()}); {@code vx/vy/vz} are the
     * absolute linear velocity (blocks/second), {@code wx/wy/wz} the angular velocity
     * (rad/s), {@code pitch/roll} follow the Gimbal Sensor convention (degrees).
     */
    record Sample(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            double wx,
            double wy,
            double wz,
            double pitch,
            double roll,
            double distance,
            String name,
            double heading,
            double hx,
            double hy,
            double hz,
            double rx,
            double ry,
            double rz,
            String source) {

        /** Absolute linear speed in blocks/second (magnitude of vx/vy/vz). */
        double speed() {
            return Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
    }
}
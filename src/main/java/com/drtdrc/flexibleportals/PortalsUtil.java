package com.drtdrc.flexibleportals;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PortalsUtil {

    private PortalsUtil() {}

    // -----------------------
    // Tunables / safety caps
    // -----------------------
    private static final int MAX_AREA = 4096;
    private static final int MAX_SPAN = 128;

    // -----------------------
    // Plane abstraction
    // -----------------------
    public enum Plane { HORIZONTAL, VERTICAL_X, VERTICAL_Z }

    /** A detected free-form region on a specific plane (for Nether axis, the plane implies AXIS). */
    public record FreeformRegion(Plane plane, List<BlockPos> interior) {
        public double centerX() { return interior.stream().mapToInt(BlockPos::getX).average().orElse(0) + 0.5; }
        public double centerY() { return interior.stream().mapToInt(BlockPos::getY).average().orElse(0) + 0.5; }
        public double centerZ() { return interior.stream().mapToInt(BlockPos::getZ).average().orElse(0) + 0.5; }
    }

    /**
     * PortalSpec describes how to detect/construct a specific portal family (End vs Nether).
     *
     * - allowedPlanes: which planes we’ll attempt from the origin (order matters; first success wins)
     * - frame: block predicate for boundary (e.g., End: eyed frame; Nether: obsidian || crying_obsidian)
     * - interior: predicate for interior cells we’re allowed to flood through (usually AIR and “same portal”;
     *             Nether often also allows FIRE)
     * - portalBlock: the block to place (END_PORTAL or NETHER_PORTAL)
     * - orientedStateForPlane: how to orient the block on a given plane (End: identity; Nether: AXIS=X/Z)
     */
    public record PortalSpec(
            List<Plane> allowedPlanes,
            Predicate<BlockState> frame,
            Predicate<BlockState> interior,
            Block portalBlock,
            Function<Plane, BlockState> orientedStateForPlane
    ) {
        // Convenience factories:

        /** End portal: horizontal only; frame = eyed frames; interior = air or end portal. */
        public static PortalSpec end() {
            Predicate<BlockState> frame = s -> s.isOf(Blocks.END_PORTAL_FRAME) && s.getOrEmpty(Properties.EYE).orElse(false);
            Predicate<BlockState> interior = s -> s.isAir() || s.isOf(Blocks.END_PORTAL);
            return new PortalSpec(
                    List.of(Plane.HORIZONTAL),
                    frame,
                    interior,
                    Blocks.END_PORTAL,
                    plane -> Blocks.END_PORTAL.getDefaultState() // no orientation property on End portal
            );
        }

        /** Nether portal: vertical planes; frame = obsidian OR crying obsidian; interior = air/fire/portal. */
        public static PortalSpec nether() {
            Predicate<BlockState> frame = s -> s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.CRYING_OBSIDIAN);
            Predicate<BlockState> interior = s -> s.isAir() || s.isIn(BlockTags.FIRE) || s.isOf(Blocks.NETHER_PORTAL);
            return new PortalSpec(
                    List.of(Plane.VERTICAL_X, Plane.VERTICAL_Z), // try YZ then XY by default
                    frame,
                    interior,
                    Blocks.NETHER_PORTAL,
                    plane -> {
                        var axis = (plane == Plane.VERTICAL_X) ? Direction.Axis.X : Direction.Axis.Z;
                        return Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);
                    }
            );
        }
    }

    // ----------------------------------------------------
    // Public API – find region and optionally create it
    // ----------------------------------------------------

    /**
     * Try to find a framed region starting from an origin block (e.g., clicked frame for End,
     * fire cell for Nether), using the planes allowed by spec. Returns the first valid region.
     *
     * The origin is treated as a "hint": we look for a valid interior seed adjacent on the chosen plane.
     */
    public static Optional<FreeformRegion> findRegion(ServerWorld world, BlockPos origin, PortalSpec spec) {
        for (Plane plane : spec.allowedPlanes()) {
            BlockPos seed = pickInteriorSeed(world, origin, plane, spec);
            if (seed == null) continue;

            List<BlockPos> region = floodInteriorIfFramed(world, seed, plane, spec);
            if (region != null && !region.isEmpty()) {
                return Optional.of(new FreeformRegion(plane, region));
            }
        }
        return Optional.empty();
    }

    /**
     * Find and create a portal using the spec. Plays optional SFX at the region centroid.
     * Returns true if creation succeeded.
     */
    public static boolean findAndCreate(ServerWorld world, BlockPos origin, PortalSpec spec, @SuppressWarnings("SameParameterValue") SoundEvent creationSound) {
        var found = findRegion(world, origin, spec);
        if (found.isEmpty()) return false;

        var region = found.get();
        BlockState place = spec.orientedStateForPlane().apply(region.plane());
        for (BlockPos p : region.interior()) {
            if (!world.getBlockState(p).isOf(spec.portalBlock())) {
                world.setBlockState(p, place, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        }

        if (creationSound != null) {
            float vol = Math.min(1.0f, 0.2f + region.interior().size() * 0.0025f);
            world.playSound(null,
                    BlockPos.ofFloored(region.centerX(), region.centerY(), region.centerZ()),
                    creationSound, SoundCategory.BLOCKS, vol, 1.0f);
        }
        return true;
    }

    // ----------------------------------------------------
    // BFS / geometry helpers (plane-agnostic but parameterized)
    // ----------------------------------------------------

    /** Choose a nearby interior seed on the plane (adjacent to origin), if any. */
    private static BlockPos pickInteriorSeed(ServerWorld world, BlockPos origin, Plane plane, PortalSpec spec) {
        for (Direction d : inPlaneNeighbors(plane)) {
            BlockPos n = origin.offset(d);
            if (!isOnPlane(origin, n, plane)) continue; // keep strictly on plane
            BlockState s = world.getBlockState(n);
            if (spec.interior().test(s) || s.isOf(spec.portalBlock())) {
                return n;
            }
        }
        return null;
    }

    /** Flood through interior cells; boundary must be frame; anything else invalidates the region. */
    private static List<BlockPos> floodInteriorIfFramed(ServerWorld world, BlockPos seed, Plane plane, PortalSpec spec) {
        if (!spec.interior().test(world.getBlockState(seed))) return null;

        var q = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var interior = new ArrayList<BlockPos>();

        q.add(seed);
        seen.add(seed);

        // bounds for safety
        int minU = planeU(seed, plane), maxU = minU;
        int minV = planeV(seed, plane), maxV = minV;

        Direction[] neigh = inPlaneNeighbors(plane);

        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            BlockState s = world.getBlockState(p);

            if (!(spec.interior().test(s) || s.isOf(spec.portalBlock()))) return null;

            interior.add(p);

            int u = planeU(p, plane), v = planeV(p, plane);
            if (u < minU) minU = u; if (u > maxU) maxU = u;
            if (v < minV) minV = v; if (v > maxV) maxV = v;

            if (interior.size() > MAX_AREA) return null;
            if ((maxU - minU + 1) > MAX_SPAN || (maxV - minV + 1) > MAX_SPAN) return null;

            for (Direction d : neigh) {
                BlockPos n = p.offset(d);
                if (!isOnPlane(seed, n, plane)) continue;

                BlockState ns = world.getBlockState(n);
                if (spec.interior().test(ns) || ns.isOf(spec.portalBlock())) {
                    if (seen.add(n)) q.addLast(n);
                } else if (spec.frame().test(ns)) {
                    // boundary OK
                } else {
                    // touched something that is not interior and not frame => open/dirty
                    return null;
                }
            }
        }
        return interior;
    }

    // plane math

    private static boolean isOnPlane(BlockPos a, BlockPos b, Plane plane) {
        return switch (plane) {
            case HORIZONTAL -> a.getY() == b.getY();
            case VERTICAL_X -> a.getX() == b.getX(); // YZ plane
            case VERTICAL_Z -> a.getZ() == b.getZ(); // XY plane
        };
    }

    private static Direction[] inPlaneNeighbors(Plane plane) {
        return switch (plane) {
            case HORIZONTAL -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            case VERTICAL_X -> new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
            case VERTICAL_Z -> new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        };
    }

    private static int planeU(BlockPos p, Plane plane) {
        return switch (plane) {
            case HORIZONTAL -> p.getX();
            case VERTICAL_X -> p.getZ(); // YZ plane -> use Z
            case VERTICAL_Z -> p.getX(); // XY plane -> use X
        };
    }

    private static int planeV(BlockPos p, Plane plane) {
        return switch (plane) {
            case HORIZONTAL -> p.getZ();
            case VERTICAL_X -> p.getY(); // YZ plane -> use Y
            case VERTICAL_Z -> p.getY(); // XY plane -> use Y
        };
    }

    // ----------------------------------------------------
    // Convenience break helpers (unchanged)
    // ----------------------------------------------------

    public static void breakConnectedEndPortal(ServerWorld w, BlockPos start) {
        var q = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        q.add(start);

        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            if (!seen.add(p)) continue;
            if (!w.getBlockState(p).isOf(Blocks.END_PORTAL)) continue;

            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.NOTIFY_ALL);
            w.syncWorldEvent(WorldEvents.BLOCK_BROKEN, p, Block.getRawIdFromState(Blocks.END_PORTAL.getDefaultState()));
            for (Direction d : Direction.values()) q.add(p.offset(d));
        }
    }

    public static void breakConnectedNetherPortal(ServerWorld w, BlockPos start) {
        var q = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        q.add(start);

        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            if (!seen.add(p)) continue;
            if (!w.getBlockState(p).isOf(Blocks.NETHER_PORTAL)) continue;

            w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.NOTIFY_ALL);
            w.syncWorldEvent(WorldEvents.BLOCK_BROKEN, p, Block.getRawIdFromState(Blocks.NETHER_PORTAL.getDefaultState()));
            for (Direction d : Direction.values()) q.add(p.offset(d));
        }
    }
}

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

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PortalsUtil {

    private PortalsUtil() {}

    // -----------------------
    // Tunables / safety caps
    // -----------------------
    private static final int MAX_AREA = 4096;      // max interior cells (safety)
    private static final int MAX_COMPONENT = 8192; // max frame component pixels (safety)

    // -----------------------
    // Plane abstraction
    // -----------------------
    public enum Plane { HORIZONTAL, VERTICAL_X, VERTICAL_Z }

    // Region result
    public record FreeformRegion(Plane plane, List<BlockPos> interior) {
        public double centerX() { return interior.stream().mapToInt(BlockPos::getX).average().orElse(0) + 0.5; }
        public double centerY() { return interior.stream().mapToInt(BlockPos::getY).average().orElse(0) + 0.5; }
        public double centerZ() { return interior.stream().mapToInt(BlockPos::getZ).average().orElse(0) + 0.5; }
    }

    /**
     * PortalSpec describes how to detect/construct a specific portal family (End vs Nether).
     * - allowedPlanes: which planes we’ll attempt from the origin (order matters; first success wins)
     * - frame: block predicate for boundary (e.g., End: eyed frame; Nether: obsidian || crying_obsidian)
     * - interior: predicate for interior cells we’re allowed to accept (STRICT: air or same portal only)
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
        /** End portal: horizontal only; frame = eyed frames; interior = air or end portal. */
        public static PortalSpec end() {
            Predicate<BlockState> frame = s -> s.isOf(Blocks.END_PORTAL_FRAME) && s.getOrEmpty(Properties.EYE).orElse(false);
            Predicate<BlockState> interior = s -> s.isAir() || s.isOf(Blocks.END_PORTAL);
            return new PortalSpec(
                    List.of(Plane.HORIZONTAL),
                    frame,
                    interior,
                    Blocks.END_PORTAL,
                    plane -> Blocks.END_PORTAL.getDefaultState()
            );
        }

        /** Nether portal: vertical planes; frame = obsidian OR crying obsidian; interior = air OR existing portal (STRICT). */
        public static PortalSpec nether() {
            Predicate<BlockState> frame = s -> s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.CRYING_OBSIDIAN);
            Predicate<BlockState> interior = s -> s.isAir() || s.isOf(Blocks.NETHER_PORTAL) || s.isIn(BlockTags.FIRE);
            return new PortalSpec(
                    List.of(Plane.VERTICAL_X, Plane.VERTICAL_Z), // try YZ then XY by default
                    frame,
                    interior,
                    Blocks.NETHER_PORTAL,
                    plane -> {
                        var axis = (plane == Plane.VERTICAL_X) ? Direction.Axis.Z : Direction.Axis.X;
                        return Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);
                    }
            );
        }
    }

    // ----------------------------------------------------
    // Public API – find region and optionally create it
    // ----------------------------------------------------

    /** Find and create a portal using the spec. Plays optional SFX at the region centroid. */
    public static boolean findAndCreate(ServerWorld world, BlockPos origin, PortalSpec spec, SoundEvent creationSound) {
        var found = findBlocksToFill(world, origin, spec);
        if (found.isEmpty()) return false;

        var region = found.get();
        BlockState place = spec.orientedStateForPlane().apply(region.plane());

        // Only place into air or existing portal tiles; correct orientation if needed.
        for (BlockPos p : region.interior()) {
            BlockState s = world.getBlockState(p);
            // Do not replace frame blocks that happen to be inside the cavity.
            if (spec.frame().test(s)) continue;

            if (spec.interior().test(s)) {
                if (!s.equals(place)) {
                    world.setBlockState(p, place, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
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

    /** Try each allowed plane: find an 8-connected frame component and compute interior. */
    public static Optional<FreeformRegion> findBlocksToFill(ServerWorld world, BlockPos origin, PortalSpec spec) {
        for (Plane plane : spec.allowedPlanes()) {
            BlockPos first = findNearestFrameOnPlane(world, origin, plane, spec.frame() /*radius*/);
            if (first == null) continue;

            // Collect the 8-connected frame "pixels" in UV space on this plane
            Set<Long> frameUV = collectFrameComponentUV(world, first, plane, spec.frame());
            if (frameUV.isEmpty()) continue;

            // Compute interior via edge-safe outside flood (diagonal-safe)
            int constCoord = cOf(first, plane);
            Bounds b = boundsOf(frameUV);
            List<BlockPos> interior = interiorFromFrameUV(frameUV, plane, constCoord, b.minU, b.minV, b.maxU, b.maxV);

            // STRICT VALIDATION: every interior block must already be air OR the same portal.
            boolean allClear = true;
            for (BlockPos p : interior) {
                BlockState s = world.getBlockState(p);
                if (!(spec.interior().test(s) || spec.frame().test(s))) {
                    allClear = false;
                    break;
                }
            }
            if (!allClear) continue; // reject this plane/frame if anything blocks the interior

            if (interior.isEmpty() || interior.size() > MAX_AREA) continue;

            return Optional.of(new FreeformRegion(plane, interior));
        }
        return Optional.empty();
    }

    // ----------------------------------------------------
    // Geometry helpers (UV mapping on a given plane)
    // ----------------------------------------------------
    private static int uOf(BlockPos p, Plane plane){ return switch(plane){ case HORIZONTAL->p.getX(); case VERTICAL_X->p.getZ(); case VERTICAL_Z->p.getX(); }; }
    private static int vOf(BlockPos p, Plane plane){ return switch(plane){ case HORIZONTAL->p.getZ(); case VERTICAL_X->p.getY(); case VERTICAL_Z->p.getY(); }; }
    private static int cOf(BlockPos p, Plane plane){ return switch(plane){ case HORIZONTAL->p.getY(); case VERTICAL_X->p.getX(); case VERTICAL_Z->p.getZ(); }; }
    private static BlockPos fromUVC(int u,int v,int c,Plane plane){
        return switch(plane){
            case HORIZONTAL -> new BlockPos(u, c, v);    // XZ (Y=c)
            case VERTICAL_X -> new BlockPos(c, v, u);    // YZ (X=c)
            case VERTICAL_Z -> new BlockPos(u, v, c);    // XY (Z=c)
        };
    }
    private static long pack(int u,int v){ return ((long)u<<32) ^ (v & 0xFFFFFFFFL); }

    private record Bounds(int minU,int minV,int maxU,int maxV){}
    private static Bounds boundsOf(Set<Long> uv){
        int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE, maxV = Integer.MIN_VALUE;
        for (long k : uv){
            int u = (int)(k>>32), v = (int)k;
            if (u < minU) minU = u; if (u > maxU) maxU = u;
            if (v < minV) minV = v; if (v > maxV) maxV = v;
        }
        return new Bounds(minU,minV,maxU,maxV);
    }

    // ----------------------------------------------------
    // Step 1: find nearest frame pixel on plane (ring scan)
    // ----------------------------------------------------
    private static BlockPos findNearestFrameOnPlane(ServerWorld w, BlockPos origin, Plane plane,
                                                    Predicate<BlockState> isFrame){
        if (isFrame.test(w.getBlockState(origin))) return origin;

        for(int r = 1; r<= 24; r++){
            switch(plane){
                case HORIZONTAL -> {
                    int y=origin.getY();
                    for(int x=origin.getX()-r;x<=origin.getX()+r;x++){
                        BlockPos p1=new BlockPos(x,y,origin.getZ()-r), p2=new BlockPos(x,y,origin.getZ()+r);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                    for(int z=origin.getZ()-r+1;z<=origin.getZ()+r-1;z++){
                        BlockPos p1=new BlockPos(origin.getX()-r,y,z), p2=new BlockPos(origin.getX()+r,y,z);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                }
                case VERTICAL_X -> { // YZ plane
                    int x=origin.getX();
                    for(int y=origin.getY()-r;y<=origin.getY()+r;y++){
                        BlockPos p1=new BlockPos(x,y,origin.getZ()-r), p2=new BlockPos(x,y,origin.getZ()+r);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                    for(int z=origin.getZ()-r+1;z<=origin.getZ()+r-1;z++){
                        BlockPos p1=new BlockPos(x,origin.getY()-r,z), p2=new BlockPos(x,origin.getY()+r,z);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                }
                case VERTICAL_Z -> { // XY plane
                    int z=origin.getZ();
                    for(int y=origin.getY()-r;y<=origin.getY()+r;y++){
                        BlockPos p1=new BlockPos(origin.getX()-r,y,z), p2=new BlockPos(origin.getX()+r,y,z);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                    for(int x=origin.getX()-r+1;x<=origin.getX()+r-1;x++){
                        BlockPos p1=new BlockPos(x,origin.getY()-r,z), p2=new BlockPos(x,origin.getY()+r,z);
                        if (isFrame.test(w.getBlockState(p1))) return p1;
                        if (isFrame.test(w.getBlockState(p2))) return p2;
                    }
                }
            }
        }
        return null;
    }

    // ----------------------------------------------------
    // Step 2: collect 8-connected frame component in UV space
    // ----------------------------------------------------
    private static final int[][] DIR8 = {
            { 1, 0}, { 1,-1}, {0,-1}, {-1,-1}, {-1, 0}, {-1, 1}, {0, 1}, { 1, 1}
    };

    private static Set<Long> collectFrameComponentUV(ServerWorld w, BlockPos seed, Plane plane,
                                                     Predicate<BlockState> isFrame){
        ArrayDeque<long[]> q = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        int C = cOf(seed, plane);
        int su = uOf(seed, plane), sv = vOf(seed, plane);
        long sk = pack(su, sv);
        q.add(new long[]{su, sv});
        seen.add(sk);

        while(!q.isEmpty()){
            long[] cur = q.removeFirst();
            int u=(int)cur[0], v=(int)cur[1];

            for (int[] d : DIR8){
                int nu=u+d[0], nv=v+d[1];
                long nk=pack(nu,nv);
                if (seen.contains(nk)) continue;
                BlockPos wp = fromUVC(nu, nv, C, plane);
                if (isFrame.test(w.getBlockState(wp))){
                    seen.add(nk); q.add(new long[]{nu,nv});
                    if (seen.size()> PortalsUtil.MAX_COMPONENT) return Set.of(); // safety
                }
            }
        }
        return seen;
    }

    // ----------------------------------------------------
    // Step 3: outside flood (4-neighbor) to get interior
    // ----------------------------------------------------
    private static final int[][] DIR4 = { {1,0}, {-1,0}, {0,1}, {0,-1} };

    /** Edge-safe outside flood: interior = bbox − (outside ∪ frame). Diagonal pinholes remain sealed. */
    private static List<BlockPos> interiorFromFrameUV(
            Set<Long> frameUV, Plane plane, int C,
            int minU, int minV, int maxU, int maxV) {

        // expand bbox with moat
        int uMin = minU - 1, uMax = maxU + 1;
        int vMin = minV - 1, vMax = maxV + 1;

        HashSet<Long> outside = new HashSet<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();

        // seed perimeter
        for (int u = uMin; u <= uMax; u++) {
            enqueueIfFree(u, vMin, frameUV, outside, q);
            enqueueIfFree(u, vMax, frameUV, outside, q);
        }
        for (int v = vMin + 1; v <= vMax - 1; v++) {
            enqueueIfFree(uMin, v, frameUV, outside, q);
            enqueueIfFree(uMax, v, frameUV, outside, q);
        }

        // flood (4-neighbor, do not enter frame)
        while (!q.isEmpty()) {
            long[] cur = q.removeFirst();
            int u = (int)cur[0], v = (int)cur[1];

            for (int[] d : DIR4) {
                int nu = u + d[0], nv = v + d[1];
                if (nu < uMin || nu > uMax || nv < vMin || nv > vMax) continue;
                long nk = pack(nu, nv);
                if (outside.contains(nk)) continue;
                if (frameUV.contains(nk)) continue;
                outside.add(nk);
                q.addLast(new long[]{nu, nv});
            }
        }

        // interior = bbox cells not in frame and not outside (restricted to original bbox)
        ArrayList<BlockPos> interior = new ArrayList<>();
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                long k = pack(u, v);
                if (frameUV.contains(k)) continue;
                if (outside.contains(k)) continue;
                interior.add(fromUVC(u, v, C, plane));
                if (interior.size() > MAX_AREA) return List.of(); // safety
            }
        }
        return interior;
    }

    private static void enqueueIfFree(int u, int v, Set<Long> frameUV,
                                      Set<Long> outside, ArrayDeque<long[]> q) {
        long k = pack(u, v);
        if (!frameUV.contains(k) && outside.add(k)) q.add(new long[]{u, v});
    }

    // ----------------------------------------------------
    // Utilities kept from your original class
    // ----------------------------------------------------
    public static void breakConnectedEndPortal(ServerWorld w, BlockPos start) {
        var q = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        q.add(start);

        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();
            if (!seen.add(p)) continue;
            if (!w.getBlockState(p).isOf(Blocks.END_PORTAL)) continue;

            w.breakBlock(p, false);
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
            BlockState state = w.getBlockState(p);
            if (!state.isOf(Blocks.NETHER_PORTAL)) continue;

            w.breakBlock(p, false);
            for (Direction d : Direction.values()) q.add(p.offset(d));
        }
    }
}

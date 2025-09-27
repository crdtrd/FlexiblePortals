package com.drtdrc.flexibleportals.mixin;

import com.drtdrc.flexibleportals.PortalsUtil;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(NetherPortal.class)
public abstract class NetherPortalMixin {

    // 1) Allow crying obsidian as a valid frame block (matches your PortalsUtil spec)
    @Shadow @Final @Mutable
    private static AbstractBlock.ContextPredicate IS_VALID_FRAME_BLOCK;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void expandValidFrame(CallbackInfo ci) {
        IS_VALID_FRAME_BLOCK = (state, world, pos) ->
                state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN);
    }

    // 2) Try free-form creation first; if we succeed, short-circuit vanilla rectangle creation
    @Inject(
            method = "getNewPortal",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void freeformCreateFirst(
            WorldAccess world, BlockPos pos, Direction.Axis firstCheckedAxis,
            CallbackInfoReturnable<Optional<NetherPortal>> cir) {

        if (!(world instanceof ServerWorld sw)) return;

        // Let PortalsUtil try both vertical planes (YZ then XY). No sound here; fire just lit.
        boolean created = PortalsUtil.findAndCreate(sw, pos, PortalsUtil.PortalSpec.nether(), /*creationSound*/ null);

        if (created) {
            // We already placed the portal blocks; tell vanilla to skip its rectangle workflow.
            cir.setReturnValue(Optional.empty());
            cir.cancel();
        }
    }
}

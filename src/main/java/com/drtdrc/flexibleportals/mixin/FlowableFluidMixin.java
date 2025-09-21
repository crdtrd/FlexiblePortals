package com.drtdrc.flexibleportals.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.HashSet;

@Mixin(FlowableFluid.class)
public class FlowableFluidMixin {

    @Inject(
            method = "flow(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;Lnet/minecraft/fluid/FluidState;)V",
            at = @At("HEAD")
    )
    private void portalsPlus$breakEndPortalOnSourceFlow(WorldAccess world, BlockPos pos, BlockState state, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (world.isClient()) return;

        boolean isSource = fluidState.isStill();
        boolean isWaterOrLava = fluidState.isIn(FluidTags.WATER) || fluidState.isIn(FluidTags.LAVA);
        if (!isSource || !isWaterOrLava) return;

        if (state.isOf(Blocks.END_PORTAL)) {
            // Remove the portal block first so vanilla flow can fill the space this tick.
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.NOTIFY_ALL);

            // (Optional) small feedback so it "feels" like a portal pop. Purely server-side.
            if (world instanceof ServerWorld sw) {
                sw.playSound(
                        null, pos,
                        SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, // close-enough SFX; pick another if you prefer
                        SoundCategory.BLOCKS,
                        0.6f, 1.0f
                );
            }
        }
    }


}

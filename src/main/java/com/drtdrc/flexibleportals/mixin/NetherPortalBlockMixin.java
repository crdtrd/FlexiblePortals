package com.drtdrc.flexibleportals.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.dimension.NetherPortal;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @Inject(
            method = "getStateForNeighborUpdate",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGetStateForNeighborUpdate(
            BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random, CallbackInfoReturnable<BlockState> cir
    ) {
        //if(neighborState.isOf(Blocks.WATER) || neighborState.isOf(Blocks.WATER))
        cir.setReturnValue(state);
    }
}

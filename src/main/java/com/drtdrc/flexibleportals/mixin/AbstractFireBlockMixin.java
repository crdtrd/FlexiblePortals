package com.drtdrc.flexibleportals.mixin;

import com.drtdrc.flexibleportals.PortalsUtil;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {


    @Inject(method = "onBlockAdded", at = @At("HEAD"), cancellable = true)
    private void onBlockAddedModified (BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (world.isClient()) return;

        if (oldState.isOf(state.getBlock())) {
            return;
        }
        if (world.getRegistryKey() == World.OVERWORLD || world.getRegistryKey() == World.NETHER) {

            PortalsUtil.createFreeformPortal((ServerWorld) world, )
            return;
        }
        if (!state.canPlaceAt(world, pos)) {
            world.removeBlock(pos, false);
        }
    }
}

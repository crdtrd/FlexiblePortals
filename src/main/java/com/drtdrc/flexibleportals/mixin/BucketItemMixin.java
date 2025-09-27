package com.drtdrc.flexibleportals.mixin;

import com.drtdrc.flexibleportals.PortalsUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BucketItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {

    /**
     * Redirect the single break that vanilla does when a block reports it can be replaced by fluid
     * (the 'bl' path). For portals, we do a BFS break instead, otherwise delegate to vanilla.
     */
    @Redirect(
            method = "placeFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;breakBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"
            )
    )
    private boolean redirectBreakForPortal(World world, BlockPos pos, boolean drop) {
        BlockState state = world.getBlockState(pos);

        if (!world.isClient()) {
            if (state.isOf(Blocks.END_PORTAL)) {
                PortalsUtil.breakConnectedEndPortal((ServerWorld) world, pos);
                return true;
            }
            if (state.isOf(Blocks.NETHER_PORTAL)) {
                PortalsUtil.breakConnectedNetherPortal((ServerWorld) world, pos);
                return true;
            }
        }

        // Non-portal blocks: do vanilla break (drops, updates, etc.)
        return world.breakBlock(pos, drop);
    }

}

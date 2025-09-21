package com.drtdrc.flexibleportals.mixin;

import com.drtdrc.flexibleportals.PortalsUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {

    @Shadow public abstract boolean placeFluid(@Nullable LivingEntity user, World world, BlockPos pos, @Nullable BlockHitResult hitResult);

    @Inject(
            method = "placeFluid",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlaceFluid(LivingEntity user, World world, BlockPos pos, BlockHitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient()) return;
        BlockState state = world.getBlockState(pos);

        if (state.isOf(Blocks.END_PORTAL)) {
            PortalsUtil.breakConnectedEndPortal((ServerWorld) world, pos);
            boolean placed = this.placeFluid(user, world, pos, hitResult);
            cir.setReturnValue(placed);
        }

        if (state.isOf(Blocks.NETHER_PORTAL)) {
            PortalsUtil.breakConnectedNetherPortal((ServerWorld) world, pos);
            boolean placed = this.placeFluid(user, world, pos, hitResult);
            cir.setReturnValue(placed);
        }
    }

}

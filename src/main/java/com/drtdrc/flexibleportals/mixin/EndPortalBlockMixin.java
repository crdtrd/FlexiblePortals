package com.drtdrc.flexibleportals.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
    @Inject(
            method = "canBucketPlace",
            at = @At("HEAD"),
            cancellable = true
    )
    void onCanBucketPlace(BlockState state, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

}

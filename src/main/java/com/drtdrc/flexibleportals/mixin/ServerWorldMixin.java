package com.drtdrc.flexibleportals.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(
            method = "syncGlobalEvent(ILnet/minecraft/util/math/BlockPos;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void portalsLocal$endPortalOpenedLocal(int eventId, BlockPos pos, int data, CallbackInfo ci) {
        if (eventId == WorldEvents.END_PORTAL_OPENED) {
            ServerWorld self = (ServerWorld) (Object) this;

            // Play the normal (local) portal spawn sound near the portal
            self.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.0f, 1.0f);

            // Cancel the global sound broadcast
            ci.cancel();
        }
    }
}

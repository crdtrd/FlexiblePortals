package com.drtdrc.flexibleportals.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow private boolean mining;
    @Shadow private int startMiningTime;
    @Shadow private BlockPos miningPos;
    @Shadow private int blockBreakingProgress;
    @Shadow protected ServerWorld world;
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow private int tickCounter;

    @Unique private BlockState oldState;

    @Shadow protected abstract float continueMining(BlockState state, BlockPos pos, int failedStartMiningTime);

    @Shadow public abstract boolean tryBreakBlock(BlockPos pos);

    // Vanilla client won't break end portal frame and therefore never send the BLOCK_DESTROY packet.
    // So the server has to keep track of block breaking progress for the client.
    @Inject(
            method = "update",
            at = @At(value = "HEAD")
    )
    public void onUpdate(CallbackInfo ci) {
        if(!this.mining) return;

        BlockState state = this.world.getBlockState(this.miningPos);
        if (!state.isOf(Blocks.END_PORTAL_FRAME)) return;

        float f = this.continueMining(state, this.miningPos, this.startMiningTime);
        if (f >= 1.0f) {
            this.world.setBlockBreakingInfo(this.player.getId(), this.miningPos, -1);
            this.blockBreakingProgress = -1;
            this.mining = false;
            this.tryBreakBlock(this.miningPos);
        }
    }

    // Save original block state on call of tryBreakBlock, we'll need it later.
    // Could also use locals but meh
    @Inject(method = "tryBreakBlock(Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"))
    private void captureOriginal(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {

        this.oldState = this.world.getBlockState(pos);
    }

    // Spawning break particles and sound.
    // Is called when we reach stage 10 of break progress and we want to break the block.

    @Inject(
            method = "tryBreakBlock(Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;onBroken(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onTryBreakBlockOnBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {

        if (!oldState.isOf(Blocks.END_PORTAL_FRAME)) return;

        this.world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, oldState),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, // x, y, z
                10,     // count
                0.5, 0.5, 0.5,  // spread in each axis
                0.1    // speed
        );

        int rawId = Block.getRawIdFromState(oldState);
        world.syncWorldEvent(2001, pos, rawId);
    }

    // Called from update(). Sending break progress to client every tick
    @Inject(
            method = "continueMining(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;I)F",
            at = @At("RETURN")
    )
    private void onContinueMining(BlockState state, BlockPos pos, int startTime, CallbackInfoReturnable<Float> cir) {

        if (!state.isOf(Blocks.END_PORTAL_FRAME)) return;

        // recalc exactly what vanilla did:
        int ticks = this.tickCounter - startTime;
        float f = state.calcBlockBreakingDelta(this.player, this.player.getEntityWorld(), pos) * (ticks + 1);
        int stage = (int)(f * 10.0f);

        // send the crack‐stage packet back to the breaker themselves, every tick
        player.networkHandler.sendPacket(
                new BlockBreakingProgressS2CPacket(player.getId(), pos, stage)
        );
    }

}

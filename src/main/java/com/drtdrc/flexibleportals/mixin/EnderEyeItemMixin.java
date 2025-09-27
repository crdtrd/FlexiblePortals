package com.drtdrc.flexibleportals.mixin;

import com.drtdrc.flexibleportals.PortalsUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.item.EnderEyeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(EnderEyeItem.class)
public class EnderEyeItemMixin {
    @Inject(
            method = "useOnBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        BlockPos blockPos = context.getBlockPos();
        BlockState blockState = world.getBlockState(blockPos);
        if (!blockState.isOf(Blocks.END_PORTAL_FRAME) || blockState.get(EndPortalFrameBlock.EYE)) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }
        if (world.isClient) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        BlockState blockState2 = blockState.with(EndPortalFrameBlock.EYE, true);
        Block.pushEntitiesUpBeforeBlockChange(blockState, blockState2, world, blockPos);
        world.setBlockState(blockPos, blockState2, Block.NOTIFY_LISTENERS);
        world.updateComparators(blockPos, Blocks.END_PORTAL_FRAME);
        context.getStack().decrement(1);
        world.syncWorldEvent(WorldEvents.END_PORTAL_FRAME_FILLED, blockPos, 0);

        PortalsUtil.findAndCreate((ServerWorld) world, blockPos, PortalsUtil.PortalSpec.end(), SoundEvents.BLOCK_END_PORTAL_SPAWN);

        cir.setReturnValue(ActionResult.SUCCESS);
    }
}

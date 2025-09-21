package com.drtdrc.flexibleportals.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetherPortal.class)
public class NetherPortalMixin {

    @Shadow @Final @Mutable
    private static AbstractBlock.ContextPredicate IS_VALID_FRAME_BLOCK = (state, world, pos) -> state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN);

//    @Inject(
//            method = create
//    )
}

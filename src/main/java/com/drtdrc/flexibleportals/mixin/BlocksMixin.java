package com.drtdrc.flexibleportals.mixin;


import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Blocks.class)
public class BlocksMixin {

    // Hacking the bedrock registry to change hardness, allow drops, and require the mineable tool, in this case a pickaxe
    @Inject(
            method = "register(Lnet/minecraft/registry/RegistryKey;Ljava/util/function/Function;Lnet/minecraft/block/AbstractBlock$Settings;)Lnet/minecraft/block/Block;",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private static void onRegister(RegistryKey<Block> key, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, CallbackInfoReturnable<Block> cir) {

        if (key.getValue().toString().contentEquals("minecraft:end_portal_frame")) {

            AbstractBlock.Settings newBlockSettings = AbstractBlock.Settings.create().mapColor(MapColor.GREEN).instrument(NoteBlockInstrument.BASEDRUM).luminance(state -> 1).strength(50.0f, 3600000.0f).requiresTool();

            Block block = factory.apply(newBlockSettings.registryKey(key));
            cir.setReturnValue(Registry.register(Registries.BLOCK, key, block));
        }

        if (key.getValue().toString().contentEquals("minecraft:end_portal")) {

            AbstractBlock.Settings newBlockSettings = AbstractBlock.Settings.create().mapColor(MapColor.BLACK).noCollision().sounds(BlockSoundGroup.GLASS).luminance(state -> 15).strength(-1.0f, 3600000.0f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);

            Block block = factory.apply(newBlockSettings.registryKey(key));
            cir.setReturnValue(Registry.register(Registries.BLOCK, key, block));
        }

        if (key.getValue().toString().contentEquals("minecraft:nether_portal")) {

            AbstractBlock.Settings newBlockSettings = AbstractBlock.Settings.create().noCollision().ticksRandomly().strength(-1.0f).sounds(BlockSoundGroup.GLASS).luminance(state -> 11).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);

            Block block = factory.apply(newBlockSettings.registryKey(key));
            cir.setReturnValue(Registry.register(Registries.BLOCK, key, block));
        }
    }
}


// ===== Vanilla Registry values =====

// public static final Block END_PORTAL_FRAME = Blocks.register("end_portal_frame", EndPortalFrameBlock::new, AbstractBlock.Settings.create().mapColor(MapColor.GREEN).instrument(NoteBlockInstrument.BASEDRUM).sounds(BlockSoundGroup.GLASS).luminance(state -> 1).strength(-1.0f, 3600000.0f).dropsNothing();

// public static final Block END_PORTAL = Blocks.register("end_portal", EndPortalBlock::new, AbstractBlock.Settings.create().mapColor(MapColor.BLACK).noCollision().luminance(state -> 15).strength(-1.0f, 3600000.0f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);

// public static final Block NETHER_PORTAL = Blocks.register("nether_portal", NetherPortalBlock::new, AbstractBlock.Settings.create().noCollision().ticksRandomly().strength(-1.0f).sounds(BlockSoundGroup.GLASS).luminance(state -> 11).pistonBehavior(PistonBehavior.BLOCK);
package com.drtdrc.flexibleportals.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class FlexiblePortalsBlockLootTableProvider extends FabricBlockLootTableProvider {
    protected FlexiblePortalsBlockLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        // Drops itself with silk touch
        addDropWithSilkTouch(Blocks.END_PORTAL_FRAME);
    }
}

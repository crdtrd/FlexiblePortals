package com.drtdrc.flexibleportals.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

public class FlexiblePortalsTagProvider extends FabricTagProvider.BlockTagProvider {
    public FlexiblePortalsTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // add pickaxe speed buff and diamond mining level requirement
        getTagBuilder(BlockTags.PICKAXE_MINEABLE).add(Identifier.ofVanilla("end_portal_frame"));
        getTagBuilder(BlockTags.NEEDS_DIAMOND_TOOL).add(Identifier.ofVanilla("end_portal_frame"));
    }
}

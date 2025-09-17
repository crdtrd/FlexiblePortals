package com.drtdrc.flexibleportals.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.recipe.RecipeExporter;
import net.minecraft.data.recipe.RecipeGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class FlexiblePortalsRecipeProvider extends FabricRecipeProvider {
    public FlexiblePortalsRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        return new RecipeGenerator(wrapperLookup, recipeExporter) {
            @Override
            public void generate() {
                RegistryWrapper.Impl<Item> itemLookup = registries.getOrThrow(RegistryKeys.ITEM);
                createShaped(RecipeCategory.BUILDING_BLOCKS, Items.END_PORTAL_FRAME, 1)
                        .pattern("ppp")
                        .pattern("eoe")
                        .pattern("eee")
                        .input('p', Items.ENDER_PEARL)
                        .input('e', Items.END_STONE)
                        .input('o', Items.OBSIDIAN)
                        .criterion(hasItem(Items.END_PORTAL_FRAME), conditionsFromItem(Items.END_PORTAL_FRAME))
                        .offerTo(exporter);
            }
        };
    }

    @Override
    public String getName() {
        return "FlexiblePortalsRecipeProvider";
    }
}

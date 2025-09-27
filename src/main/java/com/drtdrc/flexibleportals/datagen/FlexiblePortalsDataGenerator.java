package com.drtdrc.flexibleportals.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class FlexiblePortalsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(FlexiblePortalsTagProvider::new);
        pack.addProvider(FlexiblePortalsBlockLootTableProvider::new);
        pack.addProvider(FlexiblePortalsRecipeProvider::new);
    }
}

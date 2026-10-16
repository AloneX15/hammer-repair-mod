package com.takumistudios.hammer_repair.registry;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.item.RepairHammerItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registro del único objeto del mod. */
public final class ModItems {
    public static final ResourceKey<Item> REPAIR_HAMMER_KEY = ResourceKey.create(Registries.ITEM, HammerRepair.id("repair_hammer"));

    /** Sin durabilidad (no se desgasta ni se repara), no apilable, no encantable y resistente al fuego como la netherita. */
    public static final Item REPAIR_HAMMER = Registry.register(BuiltInRegistries.ITEM, REPAIR_HAMMER_KEY,
            new RepairHammerItem(new Item.Properties()
                    .setId(REPAIR_HAMMER_KEY)
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    private ModItems() {
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(REPAIR_HAMMER));
    }
}

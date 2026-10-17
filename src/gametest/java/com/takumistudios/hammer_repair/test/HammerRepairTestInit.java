package com.takumistudios.hammer_repair.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

//? if >=26.1 {
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
//?} else {
/*import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
*///?}

/**
 * Registra el datapack opcional de pruebas que sustituye la receta del martillo (desactivado por defecto). El formato
 * del ingrediente cambia entre versiones, así que 1.21.1 tiene su propia copia (datapack_override_legacy).
 */
public final class HammerRepairTestInit implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer("hammer_repair-test").ifPresent(container -> {
            //? if >=26.1 {
            ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("hammer_repair", "datapack_override"), container,
                    PackActivationType.NORMAL);
            //?} else {
            /*ResourceManagerHelper.registerBuiltinResourcePack(Identifier.fromNamespaceAndPath("hammer_repair", "datapack_override_legacy"),
                    container, ResourcePackActivationType.NORMAL);
            *///?}
        });
    }
}

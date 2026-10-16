package com.takumistudios.hammer_repair.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Registra el datapack opcional de pruebas que sustituye la receta del martillo (desactivado por defecto). */
public final class HammerRepairTestInit implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer("hammer_repair-test").ifPresent(container ->
                ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("hammer_repair", "datapack_override"), container,
                        PackActivationType.NORMAL));
    }
}

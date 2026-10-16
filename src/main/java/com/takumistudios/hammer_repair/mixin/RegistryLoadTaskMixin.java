package com.takumistudios.hammer_repair.mixin;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import com.takumistudios.hammer_repair.recipe.RegistryLoadingContext;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Desde 26.3 las recetas son un registro recargable cargado desde los datapacks. Tras registrar las recetas de los
 * datapacks, si ninguno definió {@code hammer_repair:repair_hammer}, se registra la generada desde config (antes de
 * congelar el registro). En versiones anteriores el registro de recetas no pasa por aquí y no hace nada.
 * Ver MIXINS.md.
 */
@Mixin(RegistryLoadTask.class)
public abstract class RegistryLoadTaskMixin<T> implements RegistryLoadingContext {
    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @Shadow
    @Final
    private Object registryWriteLock;

    @Unique
    private volatile RegistryOps.RegistryInfoLookup hammer_repair$context;

    @Override
    public void hammer_repair$setContext(RegistryOps.RegistryInfoLookup context) {
        this.hammer_repair$context = context;
    }

    @Inject(method = "registerElements", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void hammer_repair$addConfigRecipe(Stream<?> elements, CallbackInfo ci) {
        RegistryOps.RegistryInfoLookup context = this.hammer_repair$context;
        if (context == null || !this.registry.key().equals(Registries.RECIPE)) {
            return;
        }
        this.hammer_repair$context = null;
        //? if >=26.3 {
        Optional<HolderGetter<Item>> itemLookup = context.lookup(Registries.ITEM);
        //?} else {
        /*Optional<HolderGetter<Item>> itemLookup = context.lookup(Registries.ITEM).map(RegistryOps.RegistryInfo::getter);
        *///?}
        try {
            itemLookup.ifPresent(items -> {
                boolean fromDatapack = this.registry.containsKey((net.minecraft.resources.ResourceKey<T>) (Object) HammerRecipes.RECIPE_KEY);
                Optional<Recipe<?>> recipe = HammerRecipes.onRecipesLoading(fromDatapack, items);
                recipe.ifPresent(value -> {
                    synchronized (this.registryWriteLock) {
                        this.registry.register((net.minecraft.resources.ResourceKey<T>) (Object) HammerRecipes.RECIPE_KEY,
                                (T) value, RegistrationInfo.BUILT_IN);
                    }
                });
            });
        } catch (RuntimeException e) {
            HammerRepair.LOGGER.error("No se pudo añadir la receta del martillo; la carga de datos sigue sin ella", e);
        }
    }
}

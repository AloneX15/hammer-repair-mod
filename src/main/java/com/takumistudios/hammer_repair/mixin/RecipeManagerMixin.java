package com.takumistudios.hammer_repair.mixin;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hasta 26.2 las recetas se leen en {@code RecipeManager.prepare}: al terminar, si ningún datapack definió
 * {@code hammer_repair:repair_hammer}, se añade la receta generada desde config. Desde 26.3 este mixin queda vacío y
 * se usa {@link RegistryLoadTaskMixin}. Ver MIXINS.md.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    //? if <26.3 {
    /*@Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At("RETURN"), cancellable = true)
    private void hammer_repair$addConfigRecipe(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir) {
        try {
            RecipeMap loaded = cir.getReturnValue();
            boolean fromDatapack = loaded.byKey(HammerRecipes.RECIPE_KEY) != null;
            Optional<Recipe<?>> recipe = HammerRecipes.onRecipesLoading(fromDatapack, this.registries.lookupOrThrow(Registries.ITEM));
            if (recipe.isPresent()) {
                List<RecipeHolder<?>> holders = new ArrayList<>(loaded.values());
                holders.add(new RecipeHolder<>(HammerRecipes.RECIPE_KEY, recipe.get()));
                cir.setReturnValue(RecipeMap.create(holders));
            }
        } catch (RuntimeException e) {
            HammerRepair.LOGGER.error("No se pudo añadir la receta del martillo; la carga de datos sigue sin ella", e);
        }
    }
    *///?}
}

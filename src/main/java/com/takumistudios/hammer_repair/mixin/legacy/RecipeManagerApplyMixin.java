package com.takumistudios.hammer_repair.mixin.legacy;

import com.google.gson.JsonElement;
import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * En 1.21.1 las recetas se leen en {@code RecipeManager.apply}: al terminar, si ningún datapack definió
 * {@code hammer_repair:repair_hammer}, se añade la receta generada desde config. Ver MIXINS.md.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerApplyMixin {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Shadow
    public abstract Optional<RecipeHolder<?>> byKey(Identifier id);

    @Shadow
    public abstract Collection<RecipeHolder<?>> getRecipes();

    @Shadow
    public abstract void replaceRecipes(Iterable<RecipeHolder<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void hammer_repair$addConfigRecipe(Map<Identifier, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler,
                                               CallbackInfo ci) {
        try {
            boolean fromDatapack = byKey(HammerRecipes.RECIPE_KEY).isPresent();
            Optional<Recipe<?>> recipe = HammerRecipes.onRecipesLoading(fromDatapack, this.registries.lookupOrThrow(Registries.ITEM));
            if (recipe.isPresent()) {
                List<RecipeHolder<?>> holders = new ArrayList<>(getRecipes());
                holders.add(new RecipeHolder<>(HammerRecipes.RECIPE_KEY, recipe.get()));
                replaceRecipes(holders);
            }
        } catch (RuntimeException e) {
            HammerRepair.LOGGER.error("No se pudo añadir la receta del martillo; la carga de datos sigue sin ella", e);
        }
    }
}

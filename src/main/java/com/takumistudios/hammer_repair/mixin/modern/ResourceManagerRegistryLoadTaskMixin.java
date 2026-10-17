package com.takumistudios.hammer_repair.mixin.modern;

import com.takumistudios.hammer_repair.recipe.modern.RegistryLoadingContext;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Guarda el contexto de carga para {@link RegistryLoadTaskMixin}. Solo lectura de un argumento. Ver MIXINS.md. */
@Mixin(ResourceManagerRegistryLoadTask.class)
public abstract class ResourceManagerRegistryLoadTaskMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private void hammer_repair$rememberContext(RegistryOps.RegistryInfoLookup context, Executor executor,
                                               CallbackInfoReturnable<CompletableFuture<?>> cir) {
        ((RegistryLoadingContext) this).hammer_repair$setContext(context);
    }
}

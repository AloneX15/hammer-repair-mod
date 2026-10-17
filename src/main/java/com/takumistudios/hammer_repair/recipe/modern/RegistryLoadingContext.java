package com.takumistudios.hammer_repair.recipe.modern;

import net.minecraft.resources.RegistryOps;

/**
 * Interfaz que los mixins añaden a {@code RegistryLoadTask} para recordar el contexto de carga (objetos y etiquetas
 * nuevas) entre {@code load} y {@code registerElements}.
 */
public interface RegistryLoadingContext {
    void hammer_repair$setContext(RegistryOps.RegistryInfoLookup context);
}

package com.takumistudios.hammer_repair.command;

import com.mojang.brigadier.CommandDispatcher;
import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.config.CommonConfig;
import com.takumistudios.hammer_repair.config.ConfigResult;
import com.takumistudios.hammer_repair.config.Diagnostic;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.config.RecipeConfig;
import com.takumistudios.hammer_repair.network.HammerNetwork;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * {@code /hammerrepair reload}: lee y valida ambos archivos fuera del hilo principal; si todo es válido cancela las
 * reparaciones en curso, aplica los parámetros y recarga los datos del servidor para regenerar la receta. Si algo es
 * inválido no se aplica nada y se conserva la última configuración válida.
 */
public final class HammerRepairCommand {
    /** Evita recargas solapadas si alguien repite el comando muy seguido. */
    private static final AtomicBoolean RELOADING = new AtomicBoolean();
    /** Máximo de diagnósticos mostrados en el chat (el log los tiene todos). */
    private static final int MAX_SHOWN = 8;

    private HammerRepairCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hammerrepair")
                //? if >=26.1 {
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                //?} else {
                /*.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                *///?}
                .then(Commands.literal("reload").executes(context -> {
                    reload(context.getSource());
                    return 1;
                })));
    }

    /** Resultado de aplicar una configuración leída. */
    public enum Outcome { APPLIED, REJECTED, ERROR }

    public static void reload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (!RELOADING.compareAndSet(false, true)) {
            source.sendFailure(Component.translatable("hammer_repair.command.reload.busy"));
            return;
        }
        source.sendSuccess(() -> Component.translatable("hammer_repair.command.reload.start"), true);
        CompletableFuture.supplyAsync(HammerConfig::readFromDisk, HammerRepair.ioExecutor())
                .thenComposeAsync(loaded -> apply(source, server, loaded), server)
                .whenComplete((outcome, error) -> {
                    RELOADING.set(false);
                    if (error != null) {
                        LOGGER.error("Error al recargar la configuración de Hammer Repair", error);
                        server.execute(() -> source.sendFailure(Component.translatable("hammer_repair.command.reload.error")));
                    }
                });
    }

    /**
     * Valida y aplica una configuración ya leída del disco. Se llama en el hilo del servidor; el futuro termina cuando
     * la recarga de datos ha acabado (o enseguida si se rechaza).
     */
    public static CompletableFuture<Outcome> apply(CommandSourceStack source, MinecraftServer server, HammerConfig.Loaded loaded) {
        try {
            ConfigResult<CommonConfig> common = loaded.common();
            ConfigResult<RecipeConfig> recipe = loaded.recipe();

            // Con la receta activada, sus ingredientes deben resolverse contra los objetos y etiquetas del servidor
            List<Diagnostic> recipeDiagnostics = new ArrayList<>(recipe.diagnostics());
            boolean recipeValid = recipe.isValid();
            if (recipeValid && recipe.orThrow().enabled()) {
                ConfigResult<RecipeConfig.Resolved> resolved = recipe.orThrow().resolve(HammerRecipes.liveLookup(server));
                recipeDiagnostics.addAll(resolved.diagnostics());
                recipeValid = resolved.isValid();
                HammerRecipes.markReported(resolved.diagnostics());
            }

            HammerConfig.log("common.json", common.diagnostics());
            HammerConfig.log("recipe.json", recipeDiagnostics);
            report(source, "common.json", common.diagnostics());
            report(source, "recipe.json", recipeDiagnostics);

            if (!common.isValid() || !recipeValid) {
                // Nada se aplica parcialmente: se conserva la última configuración válida
                source.sendFailure(Component.translatable("hammer_repair.command.reload.rejected"));
                return CompletableFuture.completedFuture(Outcome.REJECTED);
            }

            RepairSessions.cancelAll(server);
            HammerConfig.apply(common.orThrow(), recipe.orThrow(), recipeDiagnostics);
            HammerNetwork.sendSettingsToAll(server);

            boolean enabled = recipe.orThrow().enabled();
            return server.reloadResources(server.getPackRepository().getSelectedIds()).handleAsync((ignored, error) -> {
                if (error != null) {
                    LOGGER.error("La recarga de datos tras /hammerrepair reload falló", error);
                    source.sendFailure(Component.translatable("hammer_repair.command.reload.error"));
                    return Outcome.ERROR;
                }
                if (HammerRecipes.isDatapackOverride()) {
                    source.sendSuccess(() -> Component.translatable("hammer_repair.command.reload.datapack_override",
                            HammerRecipes.recipeId().toString()), true);
                } else if (!enabled) {
                    source.sendSuccess(() -> Component.translatable("hammer_repair.command.reload.recipe_disabled"), true);
                }
                source.sendSuccess(() -> Component.translatable("hammer_repair.command.reload.success"), true);
                return Outcome.APPLIED;
            }, server);
        } catch (RuntimeException e) {
            LOGGER.error("Error al aplicar la configuración de Hammer Repair", e);
            source.sendFailure(Component.translatable("hammer_repair.command.reload.error"));
            return CompletableFuture.completedFuture(Outcome.ERROR);
        }
    }

    private static void report(CommandSourceStack source, String file, List<Diagnostic> diagnostics) {
        int shown = 0;
        for (Diagnostic diagnostic : diagnostics) {
            if (shown++ >= MAX_SHOWN) {
                int hidden = diagnostics.size() - MAX_SHOWN;
                source.sendSystemMessage(Component.translatable("hammer_repair.command.reload.more", hidden).withStyle(ChatFormatting.GRAY));
                return;
            }
            source.sendSystemMessage(describe(file, diagnostic));
        }
    }

    /** "recipe.json — Error de ingredientes — Fila 2, columna 1: el objeto x no existe" (con prefijo, no solo color). */
    public static MutableComponent describe(String file, Diagnostic diagnostic) {
        String severity = diagnostic.isError() ? "error" : "warning";
        MutableComponent line = Component.literal(file + " — ")
                .append(Component.translatable("hammer_repair.diagnostic.severity." + severity))
                .append(" (")
                .append(Component.translatable("hammer_repair.diagnostic.category." + diagnostic.category().name().toLowerCase(java.util.Locale.ROOT)))
                .append("): ");
        String locationKey = diagnostic.location().translationKey();
        if (!locationKey.isEmpty()) {
            line.append(Component.translatable(locationKey, diagnostic.location().translationArgs().toArray())).append(": ");
        }
        line.append(Component.translatable(diagnostic.translationKey(), diagnostic.args().toArray()));
        return line.withStyle(diagnostic.isError() ? ChatFormatting.RED : ChatFormatting.YELLOW);
    }
}

package com.takumistudios.hammer_repair;

import com.takumistudios.hammer_repair.command.HammerRepairCommand;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.item.RepairHammerItem;
import com.takumistudios.hammer_repair.network.HammerNetwork;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import com.takumistudios.hammer_repair.registry.ModItems;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Punto de entrada común (servidor y cliente). Cada evento que se registra aquí va protegido con try/catch para que un
 * error de Hammer Repair nunca tumbe el juego.
 */
public final class HammerRepair implements ModInitializer {
    public static final String MOD_ID = "hammer_repair";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Errores seguidos en el tick de reparaciones antes de desactivar la función hasta reiniciar. */
    private static final int MAX_TICK_ERRORS = 3;

    private static ExecutorService ioExecutor;
    private static int tickErrors;
    private static volatile boolean repairsEnabled = true;

    @Override
    public void onInitialize() {
        HammerConfig.setDirectory(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
        ModItems.init();
        HammerNetwork.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            try {
                HammerRepairCommand.register(dispatcher);
            } catch (RuntimeException e) {
                LOGGER.error("No se pudo registrar /hammerrepair", e);
            }
        });

        // Reparación propia: prioridad sobre equipar la pieza sostenida (PASS si no se cumplen las condiciones)
        UseItemCallback.EVENT.register(RepairHammerItem::onUseItem);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!repairsEnabled) {
                return;
            }
            try {
                RepairSessions.tick(server);
                tickErrors = 0;
            } catch (RuntimeException e) {
                LOGGER.error("Error en el tick de reparaciones; se cancelan las reparaciones en curso", e);
                RepairSessions.cancelAll(null);
                if (++tickErrors >= MAX_TICK_ERRORS) {
                    repairsEnabled = false;
                    LOGGER.error("Demasiados errores seguidos: las reparaciones con el martillo quedan desactivadas hasta reiniciar");
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                HammerConfig.recipe(); // normalmente ya cargada al leer los datapacks
            } catch (RuntimeException e) {
                LOGGER.error("No se pudo cargar la configuración de Hammer Repair", e);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                HammerRecipes.verifyLoaded(server);
            } catch (RuntimeException e) {
                LOGGER.error("No se pudo verificar la receta del martillo", e);
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) {
                return;
            }
            try {
                HammerRecipes.verifyLoaded(server);
                HammerRecipes.unlockFor(server, server.getPlayerList().getPlayers());
            } catch (RuntimeException e) {
                LOGGER.error("Error al actualizar la receta del martillo tras recargar los datos", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                RepairSessions.cancelAll(server);
            } catch (RuntimeException e) {
                LOGGER.error("Error al cancelar las reparaciones al cerrar", e);
            }
            shutdownIo();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HammerConfig.reset());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                HammerNetwork.sendSettings(handler.getPlayer());
                HammerRecipes.unlockFor(server, List.of(handler.getPlayer()));
            } catch (RuntimeException e) {
                LOGGER.error("Error al preparar Hammer Repair para {}", handler.getPlayer().getName().getString(), e);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                RepairSessions.onDisconnect(handler.getPlayer().getUUID());
            } catch (RuntimeException e) {
                LOGGER.error("Error al limpiar la sesión de reparación", e);
            }
        });

        LOGGER.info("Hammer Repair {} cargado. Creado por TakumiStudios.", version());
    }

    /** Falso si el tick de reparaciones falló demasiadas veces seguidas (hasta reiniciar). */
    public static boolean repairsEnabled() {
        return repairsEnabled;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("desconocida");
    }

    /** Hilo propio para leer y escribir la configuración fuera del hilo principal. */
    public static synchronized ExecutorService ioExecutor() {
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ioExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Hammer Repair-IO");
                thread.setDaemon(true);
                return thread;
            });
        }
        return ioExecutor;
    }

    /** Espera a que terminen las lecturas y escrituras pendientes al cerrar el servidor. */
    private static synchronized void shutdownIo() {
        if (ioExecutor == null) {
            return;
        }
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("El hilo de E/S de Hammer Repair no terminó a tiempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ioExecutor = null;
    }
}

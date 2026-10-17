package com.takumistudios.hammer_repair.repair;

import com.takumistudios.hammer_repair.compat.Versions;
import com.takumistudios.hammer_repair.config.CommonConfig;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Sesiones de reparación activas: una por reparador. Solo se recorren estas sesiones en cada tick, nunca todos los
 * jugadores ni inventarios. El cliente solo pide empezar o dejar de usar el martillo (paquetes de uso de vanilla); el
 * servidor valida manos, objetivo, distancia, visión, consentimiento y cadencia.
 */
public final class RepairSessions {
    /** Ticks mínimos entre dos avisos iguales en la barra de acción. */
    private static final int MESSAGE_COOLDOWN = 40;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Último aviso enviado a cada jugador (también sin sesión, p. ej. al intentar empezar sin experiencia). */
    private static final Map<UUID, LastMessage> LAST_MESSAGES = new HashMap<>();

    private RepairSessions() {
    }

    public enum Mode { TARGET, SELF }

    private record LastMessage(String key, long tick) {
    }

    private static final class Session {
        final Mode mode;
        final UUID target;
        final InteractionHand hammerHand;
        int progress;
        /**
         * Identidad de las piezas al empezar el golpe actual, solo para detectar sustituciones (nunca se modifican a
         * través de estas referencias).
         */
        Object[] snapshot;

        Session(Mode mode, UUID target, InteractionHand hammerHand) {
            this.mode = mode;
            this.target = target;
            this.hammerHand = hammerHand;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Inicio
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Empieza (o continúa, si ya existe la misma) una sesión cooperativa. Repetir la solicitud no reinicia el
     * temporizador ni acelera los golpes.
     *
     * @return {@code true} si el reparador puede usar el martillo
     */
    public static boolean startTarget(ServerPlayer repairer, ServerPlayer target) {
        Session current = SESSIONS.get(repairer.getUUID());
        if (current != null && current.mode == Mode.TARGET && current.target.equals(target.getUUID())) {
            return true;
        }
        if (!canStart(repairer)) {
            return false;
        }
        if (!ArmorPieces.hasDamagedArmor(target)) {
            notify(repairer, "hammer_repair.message.no_damaged_armor", target.getDisplayName());
            return false;
        }
        Session session = new Session(Mode.TARGET, target.getUUID(), InteractionHand.MAIN_HAND);
        session.snapshot = snapshot(repairer, target, session.mode);
        SESSIONS.put(repairer.getUUID(), session);
        // Golpe al pulsar el clic derecho, sin esperar a que se complete la primera reparación
        Versions.swingHammer(repairer, session.hammerHand);
        return true;
    }

    /** Empieza (o continúa) la reparación de la pieza sostenida en la mano principal con el martillo en la otra. */
    public static boolean startSelf(ServerPlayer repairer) {
        Session current = SESSIONS.get(repairer.getUUID());
        if (current != null && current.mode == Mode.SELF) {
            return true;
        }
        if (!canStart(repairer)) {
            return false;
        }
        Session session = new Session(Mode.SELF, repairer.getUUID(), InteractionHand.OFF_HAND);
        session.snapshot = snapshot(repairer, repairer, session.mode);
        SESSIONS.put(repairer.getUUID(), session);
        Versions.swingHammer(repairer, session.hammerHand);
        return true;
    }

    private static boolean canStart(ServerPlayer repairer) {
        if (repairer.isSpectator() || !repairer.isAlive()) {
            return false;
        }
        if (!RepairService.canPayAnything(repairer)) {
            notify(repairer, "hammer_repair.message.no_xp");
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        CommonConfig config = HammerConfig.common();
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer repairer = server.getPlayerList().getPlayer(entry.getKey());
            if (!tickSession(server, repairer, entry.getValue(), config)) {
                it.remove();
                if (repairer != null && isUsingHammer(repairer, entry.getValue().hammerHand)) {
                    repairer.stopUsingItem();
                }
            }
        }
    }

    /** @return {@code false} si la sesión termina */
    private static boolean tickSession(MinecraftServer server, ServerPlayer repairer, Session session, CommonConfig config) {
        // Soltar el botón, cambiar de objeto, morir, desconectarse o pasar a espectador interrumpe
        if (repairer == null || repairer.isRemoved() || !repairer.isAlive() || repairer.isSpectator()
                || !isUsingHammer(repairer, session.hammerHand)) {
            return false;
        }

        ServerPlayer target;
        if (session.mode == Mode.TARGET) {
            target = server.getPlayerList().getPlayer(session.target);
            if (target == null || !RepairTargeting.stillTargeting(repairer, target, config.maxDistance())) {
                return false;
            }
            if (config.requireTargetSneaking() && !target.isShiftKeyDown()) {
                session.progress = 0;
                session.snapshot = snapshot(repairer, target, session.mode);
                notify(repairer, "hammer_repair.message.needs_consent", target.getDisplayName());
                return true;
            }
        } else {
            target = repairer;
            // En la reparación propia, cambiar la pieza sostenida cancela
            if (session.snapshot[0] != repairer.getMainHandItem() || !ArmorPieces.isEligible(repairer.getMainHandItem())) {
                return false;
            }
        }

        // Una sustitución de equipo durante el golpe lo invalida: se empieza otro sin cobrar
        Object[] now = snapshot(repairer, target, session.mode);
        if (!java.util.Arrays.equals(now, session.snapshot)) {
            session.snapshot = now;
            session.progress = 0;
            return true;
        }

        if (++session.progress < config.hitIntervalTicks()) {
            return true;
        }
        session.progress = 0;

        RepairService.HitResult result = session.mode == Mode.TARGET
                ? RepairService.hitTarget(repairer, target, config)
                : RepairService.hitHeld(repairer, config);
        session.snapshot = snapshot(repairer, target, session.mode);

        switch (result.outcome()) {
            case NOTHING_TO_REPAIR -> {
                notify(repairer, session.mode == Mode.TARGET ? "hammer_repair.message.finished_target" : "hammer_repair.message.finished",
                        target.getDisplayName());
                return false;
            }
            case NO_XP -> {
                notify(repairer, "hammer_repair.message.no_xp");
                return false;
            }
            case REPAIRED -> {
                if (!result.moreToRepair()) {
                    notify(repairer, session.mode == Mode.TARGET ? "hammer_repair.message.finished_target" : "hammer_repair.message.finished",
                            target.getDisplayName());
                    return false;
                }
                if (result.repairerOutOfXp()) {
                    notify(repairer, "hammer_repair.message.no_xp");
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isUsingHammer(ServerPlayer player, InteractionHand hand) {
        return player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem().is(ModItems.REPAIR_HAMMER)
                && player.getItemInHand(hand).is(ModItems.REPAIR_HAMMER);
    }

    private static Object[] snapshot(ServerPlayer repairer, ServerPlayer target, Mode mode) {
        if (mode == Mode.SELF) {
            return new Object[] {repairer.getMainHandItem()};
        }
        Object[] pieces = new Object[ArmorPieces.SLOTS.size()];
        for (int i = 0; i < pieces.length; i++) {
            EquipmentSlot slot = ArmorPieces.SLOTS.get(i);
            ItemStack stack = target.getItemBySlot(slot);
            pieces[i] = stack.isEmpty() ? null : stack;
        }
        return pieces;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fin y limpieza
    // ---------------------------------------------------------------------------------------------------------------

    public static boolean hasSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }


    public static void end(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && isUsingHammer(player, session.hammerHand)) {
            player.stopUsingItem();
        }
    }

    public static void onDisconnect(UUID player) {
        SESSIONS.remove(player);
        LAST_MESSAGES.remove(player);
    }

    /** Cancela todas las reparaciones en curso (recarga de configuración, cierre del servidor). */
    public static void cancelAll(MinecraftServer server) {
        if (server != null) {
            for (UUID id : SESSIONS.keySet().toArray(UUID[]::new)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    end(player);
                }
            }
        }
        SESSIONS.clear();
        LAST_MESSAGES.clear();
    }

    /** Aviso en la barra de acción, limitado para no repetirse constantemente. */
    public static void notify(ServerPlayer player, String key, Object... args) {
        long now = player.level().getGameTime();
        LastMessage last = LAST_MESSAGES.get(player.getUUID());
        if (last != null && last.key().equals(key) && now - last.tick() < MESSAGE_COOLDOWN && now >= last.tick()) {
            return;
        }
        LAST_MESSAGES.put(player.getUUID(), new LastMessage(key, now));
        Versions.sendOverlay(player, Component.translatable(key, args));
    }
}

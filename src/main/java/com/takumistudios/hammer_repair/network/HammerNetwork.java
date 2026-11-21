package com.takumistudios.hammer_repair.network;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.config.HammerConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * Red del mod: solo servidor → cliente. El uso del martillo viaja con los paquetes de uso de vanilla, así que no hay
 * paquetes del cliente que validar ni limitar más allá de los del juego.
 *
 * <ul>
 *   <li>{@link Handshake}: en la fase de configuración, con el número de protocolo. Un cliente sin el mod o con otro
 *       protocolo recibe un mensaje claro en lugar de un fallo extraño.</li>
 *   <li>{@link Settings}: el alcance del servidor para que el cliente prediga el uso (al entrar y tras recargar).</li>
 *   <li>{@link RepairCost} (desde 1.1.0, opcional): durabilidad por punto de experiencia, solo para el tooltip. Se
 *       envía solo si el cliente lo registra, así que clientes y servidores 1.0.x siguen siendo compatibles.</li>
 * </ul>
 */
public final class HammerNetwork {
    /**
     * Subir al cambiar el formato de cualquier paquete o el comportamiento acordado entre cliente y servidor. Un paquete
     * nuevo y opcional (que se envía solo con {@code canSend}) no lo sube.
     */
    public static final int PROTOCOL_VERSION = 1;

    private HammerNetwork() {
    }

    public record Handshake(int protocol) implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = new Type<>(HammerRepair.id("handshake"));
        public static final StreamCodec<FriendlyByteBuf, Handshake> CODEC =
                ByteBufCodecs.VAR_INT.map(Handshake::new, Handshake::protocol).cast();

        @Override
        public Type<Handshake> type() {
            return TYPE;
        }
    }

    public record Settings(double maxDistance) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(HammerRepair.id("settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC =
                ByteBufCodecs.DOUBLE.map(Settings::new, Settings::maxDistance).cast();

        @Override
        public Type<Settings> type() {
            return TYPE;
        }
    }

    public record RepairCost(int durabilityPerXp) implements CustomPacketPayload {
        public static final Type<RepairCost> TYPE = new Type<>(HammerRepair.id("repair_cost"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RepairCost> CODEC =
                ByteBufCodecs.VAR_INT.map(RepairCost::new, RepairCost::durabilityPerXp).cast();

        @Override
        public Type<RepairCost> type() {
            return TYPE;
        }
    }

    public static void init() {
        //? if >=26.1 {
        PayloadTypeRegistry.clientboundConfiguration().register(Handshake.TYPE, Handshake.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Settings.TYPE, Settings.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RepairCost.TYPE, RepairCost.CODEC);
        //?} else {
        /*PayloadTypeRegistry.configurationS2C().register(Handshake.TYPE, Handshake.CODEC);
        PayloadTypeRegistry.playS2C().register(Settings.TYPE, Settings.CODEC);
        PayloadTypeRegistry.playS2C().register(RepairCost.TYPE, RepairCost.CODEC);
        *///?}

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            try {
                if (!ServerConfigurationNetworking.canSend(handler, Handshake.TYPE)) {
                    // Texto con respaldo en inglés: un cliente sin el mod no tiene nuestras traducciones
                    handler.disconnect(Component.translatableWithFallback("hammer_repair.disconnect.missing",
                            "This server requires Hammer Repair %s (network protocol %s) on the client.",
                            HammerRepair.version(), PROTOCOL_VERSION));
                    return;
                }
                ServerConfigurationNetworking.send(handler, new Handshake(PROTOCOL_VERSION));
            } catch (RuntimeException e) {
                LOGGER.error("Error en el handshake de red de Hammer Repair", e);
            }
        });
    }

    /** Envía el alcance y el coste actuales a un jugador (cada uno solo si su cliente lo conoce). */
    public static void sendSettings(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Settings.TYPE)) {
            ServerPlayNetworking.send(player, new Settings(HammerConfig.common().maxDistance()));
        }
        if (ServerPlayNetworking.canSend(player, RepairCost.TYPE)) {
            ServerPlayNetworking.send(player, new RepairCost(HammerConfig.common().durabilityPerXp()));
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}

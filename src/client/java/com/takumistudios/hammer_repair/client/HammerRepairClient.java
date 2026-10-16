package com.takumistudios.hammer_repair.client;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.network.HammerNetwork;
import com.takumistudios.hammer_repair.network.SyncedSettings;
import com.takumistudios.hammer_repair.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/** Lado cliente: handshake de protocolo, alcance sincronizado y tooltip del martillo. */
public final class HammerRepairClient implements ClientModInitializer {
    /** Líneas del tooltip creadas una vez: nada de asignaciones por frame. */
    private static final List<Component> TOOLTIP = List.of(
            Component.translatable("hammer_repair.tooltip.partner").withStyle(ChatFormatting.GRAY),
            Component.translatable("hammer_repair.tooltip.self").withStyle(ChatFormatting.GRAY),
            Component.translatable("hammer_repair.tooltip.cost").withStyle(ChatFormatting.DARK_GREEN));

    @Override
    public void onInitializeClient() {
        ClientConfigurationNetworking.registerGlobalReceiver(HammerNetwork.Handshake.TYPE, (payload, context) -> {
            try {
                if (payload.protocol() != HammerNetwork.PROTOCOL_VERSION) {
                    context.responseSender().disconnect(Component.translatable("hammer_repair.disconnect.protocol",
                            payload.protocol(), HammerNetwork.PROTOCOL_VERSION, HammerRepair.version()));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Error en el handshake de red de Hammer Repair", e);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HammerNetwork.Settings.TYPE, (payload, context) -> {
            double distance = payload.maxDistance();
            if (Double.isFinite(distance)) {
                SyncedSettings.setClientMaxDistance(distance);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SyncedSettings.resetClient());

        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (stack.is(ModItems.REPAIR_HAMMER)) {
                lines.addAll(Math.min(1, lines.size()), TOOLTIP); // justo debajo del nombre
            }
        });
    }
}

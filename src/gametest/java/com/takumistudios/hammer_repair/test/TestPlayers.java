package com.takumistudios.hammer_repair.test;

import com.mojang.authlib.GameProfile;
import com.takumistudios.hammer_repair.registry.ModItems;
import com.takumistudios.hammer_repair.repair.XpMath;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Jugadores de prueba reales (en la lista de jugadores y en el mundo) y utilidades comunes de los gametests. */
final class TestPlayers {
    private TestPlayers() {
    }

    /**
     * Crea un jugador conectado por un canal embebido en la posición relativa indicada, mirando en horizontal hacia
     * {@code yRot} (0 = sur, +Z). Como {@code GameTestHelper#makeMockServerPlayerInLevel} pero con modo de juego libre.
     */
    static ServerPlayer spawn(GameTestHelper helper, GameType mode, double x, double y, double z, float yRot) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "hr-" + Integer.toHexString((int) (Math.random() * 0xFFFFFF)));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        place(player, helper.absoluteVec(new Vec3(x, y, z)), yRot);
        player.setYHeadRot(yRot);
        return player;
    }

    /** Coloca al jugador en una posición absoluta mirando en horizontal hacia {@code yRot}. */
    static void place(ServerPlayer player, Vec3 pos, float yRot) {
        //? if >=26.1 {
        player.snapTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        //?} else {
        /*player.moveTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        *///?}
    }

    static void remove(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
    }

    static void giveLevels(ServerPlayer player, int levels) {
        player.setExperienceLevels(levels);
        player.setExperiencePoints(0);
    }

    static long xp(ServerPlayer player) {
        return XpMath.totalPoints(player.experienceLevel, player.experienceProgress);
    }

    static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    static ItemStack hammer() {
        return new ItemStack(ModItems.REPAIR_HAMMER);
    }

    /** Uso real del objeto por el servidor (incluye el {@code UseItemCallback} de Fabric). */
    static InteractionResult use(ServerPlayer player, InteractionHand hand) {
        return player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
    }

    static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack);
    }
}

package com.takumistudios.hammer_repair.test;

import com.takumistudios.hammer_repair.config.CommonConfig;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

//? if >=26.1 {
import net.fabricmc.fabric.api.gametest.v1.GameTest;
//?} else {
/*import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
*///?}

/**
 * Avisos configurables ({@code messages}, {@code show_progress}, {@code notify_target}, {@code particles}). Cambian la
 * configuración global, así que cada variante va en su propio entorno de pruebas: los lotes de entornos distintos no se
 * ejecutan a la vez.
 */
public class HammerFeedbackGameTests {
    /** Después de este tick el primer golpe ya se ha completado. */
    private static final int AFTER_HIT = 25;

    /** Avisos recibidos por cada jugador de prueba (cada prueba usa jugadores nuevos, así que no se mezclan). */
    private static final Map<UUID, List<Component>> OVERLAYS = new ConcurrentHashMap<>();

    static {
        RepairSessions.overlayListenerForTests = (player, message) ->
                OVERLAYS.computeIfAbsent(player.getUUID(), id -> new CopyOnWriteArrayList<>()).add(message);
    }

    private static List<Component> overlays(ServerPlayer player) {
        return OVERLAYS.getOrDefault(player.getUUID(), List.of());
    }

    private static boolean received(ServerPlayer player, String key) {
        return overlays(player).stream().anyMatch(message -> containsKey(message, key));
    }

    private static boolean containsKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getKey().equals(key)) {
                return true;
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component child && containsKey(child, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsKey(sibling, key));
    }

    /** Argumentos del texto de durabilidad de vanilla dentro del aviso, o una lista vacía. */
    private static List<Object> durabilityArgs(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals("item.durability")) {
            return List.of(translatable.getArgs());
        }
        for (Component sibling : component.getSiblings()) {
            List<Object> args = durabilityArgs(sibling);
            if (!args.isEmpty()) {
                return args;
            }
        }
        return List.of();
    }

    private static CommonConfig with(CommonConfig base, CommonConfig.MessageMode messages, boolean progress, boolean notifyTarget,
                                     boolean particles) {
        return new CommonConfig(base.durabilityPerXp(), base.maxRepairPerHit(), base.hitIntervalTicks(), base.maxDistance(),
                base.requireTargetSneaking(), messages, progress, notifyTarget, particles);
    }

    private static void apply(CommonConfig config) {
        HammerConfig.apply(config, null, List.of());
    }

    private static void check(GameTestHelper helper, boolean condition, String message, CommonConfig original) {
        if (!condition) {
            apply(original);
            throw TestSupport.fail(helper, message);
        }
    }

    /** Reparador mirando al sur, compañero a 2 bloques con una pechera de hierro dañada (240 máx.). */
    private static ServerPlayer[] pairWithDamagedChest(GameTestHelper helper, ItemStack chest) {
        ServerPlayer repairer = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        ServerPlayer target = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 3.5, 180.0F);
        repairer.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.hammer());
        TestPlayers.giveLevels(repairer, 30);
        TestPlayers.wear(target, EquipmentSlot.CHEST, chest);
        return new ServerPlayer[] {repairer, target};
    }

    /** "Solo la acción": se repara y se cobra igual, pero nadie recibe ningún aviso, ni siquiera los de fallo de uso. */
    //? if >=26.1 {
    @GameTest(environment = "hammer_repair:feedback_quiet", maxTicks = 120)
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "hammer_repair_feedback_quiet", timeoutTicks = 120)
    *///?}
    public void messagesNoneRepairsWithoutAnyMessage(GameTestHelper helper) {
        CommonConfig original = HammerConfig.common();
        // show_progress y notify_target activados a propósito: "none" debe imponerse
        apply(with(original, CommonConfig.MessageMode.NONE, true, true, false));

        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        ServerPlayer[] pair = pairWithDamagedChest(helper, chest);
        ServerPlayer repairer = pair[0];
        ServerPlayer target = pair[1];
        long xp = TestPlayers.xp(repairer);

        // Sin experiencia: la reparación propia no empieza y tampoco avisa
        ServerPlayer broke = TestPlayers.spawn(helper, GameType.SURVIVAL, 0.5, 2, 0.5, 180.0F);
        broke.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.damaged(Items.IRON_HELMET, 50));
        broke.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(broke, InteractionHand.MAIN_HAND);
        check(helper, !RepairSessions.hasSession(broke), "sin experiencia no debe empezar", original);

        TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
        check(helper, RepairSessions.hasSession(repairer), "debe empezar la reparación", original);

        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chest.getDamageValue() == 80, "debe reparar 20 igual que siempre: daño " + chest.getDamageValue(), original);
            check(helper, TestPlayers.xp(repairer) == xp - 10, "debe cobrar 10 igual que siempre", original);
            check(helper, overlays(repairer).isEmpty(), "el reparador no debe recibir avisos: " + overlays(repairer), original);
            check(helper, overlays(target).isEmpty(), "el compañero no debe recibir avisos: " + overlays(target), original);
            check(helper, overlays(broke).isEmpty(), "sin experiencia tampoco se avisa: " + overlays(broke), original);
            apply(original);
            TestPlayers.remove(helper, repairer, target, broke);
            helper.succeed();
        });
    }

    /** Todos los avisos: el compañero sabe quién le repara y el reparador ve la durabilidad tras cada golpe. */
    //? if >=26.1 {
    @GameTest(environment = "hammer_repair:feedback_verbose", maxTicks = 120)
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "hammer_repair_feedback_verbose", timeoutTicks = 120)
    *///?}
    public void progressAndTargetNotice(GameTestHelper helper) {
        CommonConfig original = HammerConfig.common();
        apply(with(original, CommonConfig.MessageMode.ALL, true, true, true));

        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        ServerPlayer[] pair = pairWithDamagedChest(helper, chest);
        ServerPlayer repairer = pair[0];
        ServerPlayer target = pair[1];

        TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
        check(helper, received(target, "hammer_repair.message.being_repaired"), "el compañero debe saber que le reparan", original);
        check(helper, overlays(repairer).isEmpty(), "antes del primer golpe no hay progreso", original);

        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chest.getDamageValue() == 80, "debe reparar 20: daño " + chest.getDamageValue(), original);
            check(helper, received(repairer, "item.durability"), "debe mostrarse la durabilidad: " + overlays(repairer), original);
            List<Object> args = durabilityArgs(overlays(repairer).get(overlays(repairer).size() - 1));
            check(helper, args.equals(List.of(160, 240)), "durabilidad 160 / 240: " + args, original);
            apply(original);
            TestPlayers.remove(helper, repairer, target);
            helper.succeed();
        });
    }

    /** Con los valores por defecto no hay progreso por golpe, y {@code notify_target: false} silencia solo al compañero. */
    //? if >=26.1 {
    @GameTest(environment = "hammer_repair:feedback_defaults", maxTicks = 120)
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "hammer_repair_feedback_defaults", timeoutTicks = 120)
    *///?}
    public void defaultsShowNoProgressAndTargetNoticeCanBeDisabled(GameTestHelper helper) {
        CommonConfig original = HammerConfig.common();
        apply(with(CommonConfig.DEFAULTS, CommonConfig.MessageMode.ALL, false, false, true));

        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 30);
        ServerPlayer[] pair = pairWithDamagedChest(helper, chest);
        ServerPlayer repairer = pair[0];
        ServerPlayer target = pair[1];

        TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chest.getDamageValue() == 10, "debe reparar 20: daño " + chest.getDamageValue(), original);
            check(helper, overlays(target).isEmpty(), "notify_target: false no avisa al compañero: " + overlays(target), original);
            check(helper, !received(repairer, "item.durability"), "sin show_progress no hay progreso", original);
        });
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, chest.getDamageValue() == 0, "debe terminar la reparación: daño " + chest.getDamageValue(), original);
            check(helper, received(repairer, "hammer_repair.message.finished_target"), "debe avisar al terminar", original);
            apply(original);
            TestPlayers.remove(helper, repairer, target);
            helper.succeed();
        });
    }
}

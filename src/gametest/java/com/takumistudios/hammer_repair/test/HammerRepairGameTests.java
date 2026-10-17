package com.takumistudios.hammer_repair.test;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.registry.ModItems;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

/**
 * Gametests de servidor de la reparación: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI).
 * Configuración predeterminada: 2 de durabilidad por punto, 20 por golpe, un golpe cada 20 ticks y 3 bloques.
 */
public class HammerRepairGameTests {
    /** Antes de este tick el primer golpe no puede haberse completado. */
    private static final int BEFORE_HIT = 15;
    /** Después de este tick el primer golpe ya se ha completado. */
    private static final int AFTER_HIT = 25;

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }

    /** Reparador en (1.5, 2, 1.5) mirando al sur y compañero a 2 bloques mirándole. */
    private static ServerPlayer[] pair(GameTestHelper helper, GameType repairerMode) {
        ServerPlayer repairer = TestPlayers.spawn(helper, repairerMode, 1.5, 2, 1.5, 0.0F);
        ServerPlayer target = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 3.5, 180.0F);
        repairer.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.hammer());
        return new ServerPlayer[] {repairer, target};
    }

    @GameTest(maxTicks = 120)
    public void modIsLoaded(GameTestHelper helper) {
        check(helper, FabricLoader.getInstance().isModLoaded(HammerRepair.MOD_ID), "Hammer Repair no está cargado");
        ItemStack hammer = TestPlayers.hammer();
        check(helper, hammer.getMaxStackSize() == 1, "el martillo no debe apilarse");
        check(helper, !hammer.isDamageableItem(), "el martillo no pierde durabilidad");
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void selfRepairTakesPriorityOverEquipping(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        long xpBefore = TestPlayers.xp(player);
        ItemStack chestplate = TestPlayers.damaged(Items.DIAMOND_CHESTPLATE, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, chestplate);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());

        InteractionResult result = TestPlayers.use(player, InteractionHand.MAIN_HAND);
        check(helper, result.consumesAction(), "la reparación propia debe consumir la acción: " + result);
        check(helper, player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "no debe equiparse la pechera");
        check(helper, player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND, "debe usarse el martillo");

        helper.runAfterDelay(BEFORE_HIT, () -> check(helper, chestplate.getDamageValue() == 100, "golpe antes de tiempo"));
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chestplate.getDamageValue() == 80, "debe reparar 20: daño " + chestplate.getDamageValue());
            check(helper, TestPlayers.xp(player) == xpBefore - 10, "debe costar 10 puntos: " + (xpBefore - TestPlayers.xp(player)));
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    private static boolean swinging(ServerPlayer player, InteractionHand hand) {
        //? if >=26.3 {
        return player.isSwinging() && player.getCurrentSwing() != null && player.getCurrentSwing().hand() == hand;
        //?} else {
        /*return player.swinging && player.swingingArm == hand;
        *///?}
    }

    @GameTest(maxTicks = 120)
    public void startingARepairSwingsTheHammerImmediately(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, TestPlayers.damaged(Items.IRON_CHESTPLATE, 50));
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        check(helper, swinging(pair[0], InteractionHand.MAIN_HAND), "debe golpear con el martillo al pulsar");

        ServerPlayer self = TestPlayers.spawn(helper, GameType.SURVIVAL, 3.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(self, 30);
        self.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.damaged(Items.IRON_HELMET, 50));
        self.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(self, InteractionHand.MAIN_HAND);
        check(helper, swinging(self, InteractionHand.OFF_HAND), "debe golpear con el martillo de la segunda mano");

        TestPlayers.remove(helper, pair[0], pair[1], self);
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void undamagedPieceIsEquippedNormally(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        check(helper, player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "sin daño no se intercepta: debe equiparse");
        check(helper, !RepairSessions.hasSession(player), "no debe haber sesión");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void cooperativeRepairChargesOnlyTheRepairer(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        ServerPlayer repairer = pair[0];
        ServerPlayer target = pair[1];
        TestPlayers.giveLevels(repairer, 30);
        TestPlayers.giveLevels(target, 5);
        long repairerXp = TestPlayers.xp(repairer);
        long targetXp = TestPlayers.xp(target);
        ItemStack helmet = TestPlayers.damaged(Items.IRON_HELMET, 50);   // 165 máx: ~70 % restante
        ItemStack boots = TestPlayers.damaged(Items.IRON_BOOTS, 150);    // 195 máx: ~23 % restante
        TestPlayers.wear(target, EquipmentSlot.HEAD, helmet);
        TestPlayers.wear(target, EquipmentSlot.FEET, boots);

        InteractionResult result = TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
        check(helper, result.consumesAction(), "debe empezar la reparación cooperativa: " + result);

        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, boots.getDamageValue() == 130, "debe reparar primero la pieza más desgastada: " + boots.getDamageValue());
            check(helper, helmet.getDamageValue() == 50, "el casco no se toca en el primer golpe");
            check(helper, TestPlayers.xp(repairer) == repairerXp - 10, "el reparador paga 10");
            check(helper, TestPlayers.xp(target) == targetXp, "el compañero no paga nada");
            TestPlayers.remove(helper, repairer, target);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void intactArmorCannotStartAndChargesNothing(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        TestPlayers.wear(pair[1], EquipmentSlot.FEET, TestPlayers.damaged(Items.ELYTRA, 100));
        TestPlayers.wear(pair[1], EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));

        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        check(helper, !RepairSessions.hasSession(pair[0]), "sin armadura dañada elegible no hay sesión");
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, TestPlayers.xp(pair[0]) == xp, "nunca se cobra sobre piezas intactas");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void elytraInChestSlotIsNotRepaired(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        ItemStack elytra = TestPlayers.damaged(Items.ELYTRA, 200);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, elytra);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        check(helper, !RepairSessions.hasSession(pair[0]), "los élitros quedan fuera del alcance");
        check(helper, elytra.getDamageValue() == 200, "los élitros no cambian");
        TestPlayers.remove(helper, pair);
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void withoutXpNothingStarts(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 0);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, chest);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        check(helper, !RepairSessions.hasSession(pair[0]), "sin experiencia no hay sesión");
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chest.getDamageValue() == 100, "sin experiencia no se repara");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void partialXpRepairsWhatItCanAndStops(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        player.setExperiencePoints(3); // nivel 0 con 3 puntos
        ItemStack boots = TestPlayers.damaged(Items.DIAMOND_BOOTS, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, boots);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, boots.getDamageValue() == 94, "3 puntos pagan 6 de durabilidad: " + boots.getDamageValue());
            check(helper, TestPlayers.xp(player) == 0, "la experiencia queda a 0 sin negativos");
            check(helper, player.experienceLevel == 0 && player.experienceProgress >= 0.0F, "nivel y barra válidos");
            check(helper, !RepairSessions.hasSession(player), "sin experiencia la acción termina");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void releasingBeforeTheHitChargesNothing(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        long xp = TestPlayers.xp(player);
        ItemStack leggings = TestPlayers.damaged(Items.IRON_LEGGINGS, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, leggings);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, player::releaseUsingItem);
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, leggings.getDamageValue() == 100, "un golpe incompleto no repara");
            check(helper, TestPlayers.xp(player) == xp, "un golpe incompleto no se cobra");
            check(helper, !RepairSessions.hasSession(player), "soltar el botón termina la sesión");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void changingTheHeldPieceCancels(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        long xp = TestPlayers.xp(player);
        ItemStack first = TestPlayers.damaged(Items.IRON_LEGGINGS, 100);
        ItemStack second = TestPlayers.damaged(Items.IRON_BOOTS, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, first);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> player.setItemInHand(InteractionHand.MAIN_HAND, second));
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, first.getDamageValue() == 100 && second.getDamageValue() == 100, "cambiar la pieza cancela sin reparar");
            check(helper, TestPlayers.xp(player) == xp, "sin cobro");
            check(helper, !RepairSessions.hasSession(player), "la sesión termina");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void switchingAwayFromTheHammerCancels(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, chest);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> pair[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)));
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, chest.getDamageValue() == 100, "cambiar de objeto interrumpe");
            check(helper, TestPlayers.xp(pair[0]) == xp, "sin cobro");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void swappingTargetArmorDuringTheHitInvalidatesIt(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        ItemStack original = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        ItemStack replacement = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, original);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> TestPlayers.wear(pair[1], EquipmentSlot.CHEST, replacement));
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, replacement.getDamageValue() == 100, "la sustitución invalida el golpe en curso");
            check(helper, original.getDamageValue() == 100, "la pieza retirada nunca se modifica");
            check(helper, TestPlayers.xp(pair[0]) == xp, "golpe invalidado sin cobro");
        });
        helper.runAfterDelay(AFTER_HIT + 15, () -> {
            check(helper, replacement.getDamageValue() == 80, "el siguiente golpe repara la pieza nueva: " + replacement.getDamageValue());
            check(helper, original.getDamageValue() == 100, "la pieza retirada sigue intacta");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void leavingRangeInterrupts(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, chest);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> {
            var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 7.5));
            pair[1].snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        });
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, chest.getDamageValue() == 100, "fuera de alcance no se repara");
            check(helper, TestPlayers.xp(pair[0]) == xp, "sin cobro");
            check(helper, !RepairSessions.hasSession(pair[0]), "la sesión termina");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void blockedLineOfSightInterrupts(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, chest);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> {
            helper.setBlock(new BlockPos(1, 3, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(1, 4, 2), Blocks.STONE);
        });
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, chest.getDamageValue() == 100, "sin línea de visión no se repara");
            check(helper, TestPlayers.xp(pair[0]) == xp, "sin cobro");
            TestPlayers.remove(helper, pair);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void targetDisconnectInterrupts(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        long xp = TestPlayers.xp(pair[0]);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, TestPlayers.damaged(Items.IRON_CHESTPLATE, 100));
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> TestPlayers.remove(helper, pair[1]));
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, TestPlayers.xp(pair[0]) == xp, "desconexión del compañero: sin cobro");
            check(helper, !RepairSessions.hasSession(pair[0]), "la sesión termina");
            TestPlayers.remove(helper, pair[0]);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void repairerDisconnectCleansUp(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SURVIVAL);
        TestPlayers.giveLevels(pair[0], 30);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, chest);
        TestPlayers.use(pair[0], InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> TestPlayers.remove(helper, pair[0]));
        helper.runAfterDelay(AFTER_HIT + 20, () -> {
            check(helper, chest.getDamageValue() == 100, "desconexión del reparador: no se repara");
            check(helper, !RepairSessions.hasSession(pair[0]), "sesión eliminada");
            TestPlayers.remove(helper, pair[1]);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void twoRepairersOnlyPayForRealWork(GameTestHelper helper) {
        ServerPlayer first = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        ServerPlayer second = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 5.5, 180.0F);
        ServerPlayer target = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 3.5, 0.0F);
        for (ServerPlayer repairer : new ServerPlayer[] {first, second}) {
            repairer.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.hammer());
            TestPlayers.giveLevels(repairer, 30);
        }
        long before = TestPlayers.xp(first) + TestPlayers.xp(second);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 30);
        TestPlayers.wear(target, EquipmentSlot.CHEST, chest);
        check(helper, TestPlayers.use(first, InteractionHand.MAIN_HAND).consumesAction(), "primer reparador");
        check(helper, TestPlayers.use(second, InteractionHand.MAIN_HAND).consumesAction(), "segundo reparador");
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, chest.getDamageValue() == 0, "entre los dos reparan los 30 puntos: " + chest.getDamageValue());
            long paid = before - TestPlayers.xp(first) - TestPlayers.xp(second);
            check(helper, paid == 15, "solo se paga el trabajo real (10 + 5): " + paid);
            TestPlayers.remove(helper, first, second, target);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void repeatedUseRequestsDoNotSpeedUpHits(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        ItemStack chest = TestPlayers.damaged(Items.DIAMOND_CHESTPLATE, 300);
        player.setItemInHand(InteractionHand.MAIN_HAND, chest);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        for (int tick = 1; tick < 40; tick += 3) {
            helper.runAfterDelay(tick, () -> TestPlayers.use(player, InteractionHand.MAIN_HAND));
        }
        helper.runAfterDelay(30, () -> check(helper, chest.getDamageValue() == 280, "un solo golpe en 30 ticks: " + chest.getDamageValue()));
        helper.runAfterDelay(45, () -> {
            check(helper, chest.getDamageValue() == 260, "dos golpes en 45 ticks: " + chest.getDamageValue());
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void repairKeepsEnchantmentsNameAndComponents(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        ItemStack chest = TestPlayers.damaged(Items.NETHERITE_CHESTPLATE, 200);
        Holder<Enchantment> protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        chest.enchant(protection, 4);
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("Pechera de prueba"));
        ItemStack expected = chest.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, chest);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(AFTER_HIT, () -> {
            expected.setDamageValue(180);
            check(helper, ItemStack.matches(expected, chest), "solo cambia el daño: " + chest.getComponentsPatch());
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void creativeRepairsWithoutXp(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.CREATIVE, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 0);
        ItemStack helmet = TestPlayers.damaged(Items.GOLDEN_HELMET, 50);
        player.setItemInHand(InteractionHand.MAIN_HAND, helmet);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, helmet.getDamageValue() == 30, "en creativo se repara sin experiencia: " + helmet.getDamageValue());
            check(helper, TestPlayers.xp(player) == 0, "sin cobro en creativo");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void spectatorCannotStart(GameTestHelper helper) {
        ServerPlayer[] pair = pair(helper, GameType.SPECTATOR);
        TestPlayers.giveLevels(pair[0], 30);
        TestPlayers.wear(pair[1], EquipmentSlot.CHEST, TestPlayers.damaged(Items.IRON_CHESTPLATE, 100));
        check(helper, pair[0].getMainHandItem().is(ModItems.REPAIR_HAMMER), "martillo en la mano");
        pair[0].getMainHandItem().use(helper.getLevel(), pair[0], InteractionHand.MAIN_HAND);
        check(helper, !RepairSessions.hasSession(pair[0]), "en espectador no se inicia nada");
        TestPlayers.remove(helper, pair);
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void finishingTheRepairEndsTheSession(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        TestPlayers.giveLevels(player, 30);
        long xp = TestPlayers.xp(player);
        ItemStack boots = TestPlayers.damaged(Items.IRON_BOOTS, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, boots);
        player.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
        TestPlayers.use(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(AFTER_HIT, () -> {
            check(helper, boots.getDamageValue() == 0, "pieza reparada por completo");
            check(helper, TestPlayers.xp(player) == xp - 1, "falta 1 punto: cuesta 1");
            check(helper, !RepairSessions.hasSession(player) && !player.isUsingItem(), "la acción termina sola");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }
}

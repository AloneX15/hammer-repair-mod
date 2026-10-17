package com.takumistudios.hammer_repair.test;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

//? if >=26.1 {
import net.fabricmc.fabric.api.gametest.v1.GameTest;
//?} else {
/*import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
*///?}

/**
 * Desactivación de seguridad tras errores seguidos en el tick. Cambia el estado global del mod y cancela todas las
 * reparaciones, así que va en su propio entorno de pruebas: los lotes de entornos distintos no se ejecutan a la vez.
 */
public class HammerSafetyGameTests {
    private static final int AFTER_HIT = 25;

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw TestSupport.fail(helper, message);
        }
    }

    //? if >=26.1 {
    @GameTest(environment = "hammer_repair:safety", maxTicks = 120)
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "hammer_repair_safety", timeoutTicks = 120)
    *///?}
    public void tickErrorsStopTheHammerInBothModes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer repairer = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        ServerPlayer target = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 3.5, 180.0F);
        repairer.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.hammer());
        TestPlayers.giveLevels(repairer, 30);
        long xp = TestPlayers.xp(repairer);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(target, EquipmentSlot.CHEST, chest);

        try {
            TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
            check(helper, RepairSessions.hasSession(repairer) && repairer.isUsingItem(), "debe empezar la reparación cooperativa");

            // Un error cancela la reparación y deja de usar el martillo
            HammerRepair.tickFailed(server, new RuntimeException("error simulado por HammerSafetyGameTests"));
            check(helper, !RepairSessions.hasSession(repairer), "el error debe cancelar la sesión");
            check(helper, !repairer.isUsingItem(), "el error debe dejar de usar el martillo");
            check(helper, HammerRepair.repairsEnabled(), "un solo error no desactiva el martillo");

            HammerRepair.tickFailed(server, new RuntimeException("error simulado por HammerSafetyGameTests"));
            HammerRepair.tickFailed(server, new RuntimeException("error simulado por HammerSafetyGameTests"));
            check(helper, !HammerRepair.repairsEnabled(), "tres errores seguidos desactivan el martillo");

            // Desactivado, ningún modo empieza una sesión
            InteractionResult result = TestPlayers.use(repairer, InteractionHand.MAIN_HAND);
            check(helper, !result.consumesAction(), "la reparación cooperativa no debe empezar: " + result);
            check(helper, !RepairSessions.hasSession(repairer), "sin sesión cooperativa con el martillo desactivado");

            ServerPlayer self = TestPlayers.spawn(helper, GameType.SURVIVAL, 3.5, 2, 1.5, 0.0F);
            TestPlayers.giveLevels(self, 30);
            self.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.damaged(Items.IRON_HELMET, 50));
            self.setItemInHand(InteractionHand.OFF_HAND, TestPlayers.hammer());
            TestPlayers.use(self, InteractionHand.MAIN_HAND);
            check(helper, !RepairSessions.hasSession(self), "sin sesión propia con el martillo desactivado");
            TestPlayers.remove(helper, self);
        } catch (RuntimeException e) {
            HammerRepair.resetRepairsForTests();
            TestPlayers.remove(helper, repairer, target);
            throw e;
        }

        helper.runAfterDelay(AFTER_HIT, () -> {
            HammerRepair.resetRepairsForTests();
            TestPlayers.remove(helper, repairer, target);
            check(helper, chest.getDamageValue() == 100, "desactivado no se repara: daño " + chest.getDamageValue());
            check(helper, TestPlayers.xp(repairer) == xp, "desactivado no se cobra nada");
            helper.succeed();
        });
    }
}

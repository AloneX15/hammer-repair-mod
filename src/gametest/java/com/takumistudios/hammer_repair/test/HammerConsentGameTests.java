package com.takumistudios.hammer_repair.test;

import com.takumistudios.hammer_repair.config.CommonConfig;
import com.takumistudios.hammer_repair.config.HammerConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Consentimiento ({@code require_target_sneaking}). Cambia la configuración global, así que va en su propio entorno
 * de pruebas: los lotes de entornos distintos no se ejecutan a la vez.
 */
public class HammerConsentGameTests {
    //? if >=26.1 {
    @GameTest(environment = "hammer_repair:consent", maxTicks = 200)
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "hammer_repair_consent", timeoutTicks = 200)
    *///?}
    public void targetMustCrouchToAccept(GameTestHelper helper) {
        CommonConfig original = HammerConfig.common();
        HammerConfig.apply(new CommonConfig(original.durabilityPerXp(), original.maxRepairPerHit(), original.hitIntervalTicks(),
                original.maxDistance(), true), null, java.util.List.of());

        ServerPlayer repairer = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        ServerPlayer target = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 3.5, 180.0F);
        repairer.setItemInHand(InteractionHand.MAIN_HAND, TestPlayers.hammer());
        TestPlayers.giveLevels(repairer, 30);
        long xp = TestPlayers.xp(repairer);
        ItemStack chest = TestPlayers.damaged(Items.IRON_CHESTPLATE, 100);
        TestPlayers.wear(target, EquipmentSlot.CHEST, chest);
        TestPlayers.use(repairer, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(40, () -> {
            if (chest.getDamageValue() != 100 || TestPlayers.xp(repairer) != xp) {
                restore(original);
                throw helper.assertionException("sin agacharse no debe repararse ni cobrarse nada");
            }
            target.setShiftKeyDown(true);
        });
        helper.runAfterDelay(70, () -> {
            restore(original);
            boolean ok = chest.getDamageValue() == 80 && TestPlayers.xp(repairer) == xp - 10;
            TestPlayers.remove(helper, repairer, target);
            if (!ok) {
                throw helper.assertionException("agachado debe aceptar la reparación: daño " + chest.getDamageValue());
            }
            helper.succeed();
        });
    }

    private static void restore(CommonConfig original) {
        HammerConfig.apply(original, null, java.util.List.of());
    }
}

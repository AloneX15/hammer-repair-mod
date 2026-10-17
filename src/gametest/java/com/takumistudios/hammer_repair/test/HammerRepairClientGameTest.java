package com.takumistudios.hammer_repair.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import com.takumistudios.hammer_repair.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Test de cliente con entrada real de ratón: reparación propia manteniendo clic derecho, prioridad frente a equipar
 * la pieza, cancelación al soltar, animación en primera y tercera persona, tooltip, traducciones y libro de recetas.
 * Después repite la reparación contra un servidor dedicado real. Capturas en
 * versions/<v>/build/run/clientGameTest/screenshots/. Ejecutar con ./gradlew :26.3:runClientGameTest.
 */
public class HammerRepairClientGameTest implements FabricClientGameTest {
    private static final int DAMAGE = 200;

    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            waitForChunks(world);
            TestServerContext server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");

            checkTranslationsAndTooltip(context);
            check(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getRecipeBook().contains(HammerRecipes.RECIPE_KEY)),
                    "la receta del martillo debe estar en el libro de recetas");

            // Modelo 3D y textura del martillo en reposo, en la mano principal
            server.runCommand("item replace entity @a weapon.mainhand with hammer_repair:repair_hammer");
            context.getInput().lookAt(0.0F, 10.0F);
            context.waitTicks(20);
            context.takeScreenshot("hammer_repair_held_model");

            prepareSelfRepair(server);
            context.getInput().lookAt(0.0F, -40.0F);
            context.waitTicks(5);

            // Mantener clic derecho: repara la pieza en vez de equiparla
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            context.takeScreenshot("hammer_repair_self_repair_first_person");
            context.waitTicks(15);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(2);
            int afterHold = heldDamage(server);
            check(afterHold == DAMAGE - 20, "un golpe completo debe reparar 20 puntos (daño " + afterHold + ")");
            check(server.computeOnServer(s -> firstPlayer(s).getItemBySlot(EquipmentSlot.CHEST).isEmpty()),
                    "la pieza no debe equiparse: la reparación tiene prioridad");

            // Soltar antes de completar el golpe no repara ni cobra
            int xpBefore = server.computeOnServer(s -> firstPlayer(s).experienceLevel);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(8);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(30);
            check(heldDamage(server) == afterHold, "soltar el botón cancela el golpe pendiente");
            check(server.computeOnServer(s -> firstPlayer(s).experienceLevel) == xpBefore, "sin cobro al cancelar");

            // Tercera persona: animación del golpe vista desde fuera
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(21);
            context.takeScreenshot("hammer_repair_self_repair_third_person");
            context.waitTicks(4);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(2);
            check(heldDamage(server) == afterHold - 20, "segundo golpe en tercera persona");
        }

        // Servidor dedicado real: misma interacción por red
        try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
            try (var connection = server.connect()) {
                //? if >=26.2 {
                connection.waitForChunksRender();
                //?} else {
                /*connection.getClientLevel().waitForChunksRender();
                *///?}
                server.runCommand("time set noon");
                prepareSelfRepair(server);
                context.getInput().lookAt(0.0F, -40.0F);
                context.waitTicks(5);
                context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                context.waitTicks(25);
                context.takeScreenshot("hammer_repair_dedicated_server");
                context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                context.waitTicks(5);
                int damage = heldDamage(server);
                check(damage == DAMAGE - 20, "en servidor dedicado también repara 20 puntos (daño " + damage + ")");
            }
        }
    }

    private static void checkTranslationsAndTooltip(ClientGameTestContext context) {
        context.runOnClient(client -> {
            for (String key : List.of("item.hammer_repair.repair_hammer", "hammer_repair.tooltip.partner", "hammer_repair.tooltip.self",
                    "hammer_repair.tooltip.cost", "hammer_repair.message.no_xp")) {
                check(Language.getInstance().has(key), "falta la traducción " + key);
            }
            ItemStack hammer = new ItemStack(ModItems.REPAIR_HAMMER);
            List<String> lines = hammer.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
                    .stream().map(Component::getString).toList();
            check(lines.contains(Component.translatable("hammer_repair.tooltip.self").getString()), "tooltip sin la reparación propia: " + lines);
            check(lines.contains(Component.translatable("hammer_repair.tooltip.cost").getString()), "tooltip sin el coste: " + lines);
        });
    }

    /** Supervivencia, 30 niveles, pechera dañada en la mano principal y martillo en la segunda. */
    private static void prepareSelfRepair(TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("xp set @a 30 levels");
        server.runCommand("item replace entity @a armor.chest with air");
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_chestplate[damage=" + DAMAGE + "]");
        server.runCommand("item replace entity @a weapon.offhand with hammer_repair:repair_hammer");
    }

    private static ServerPlayer firstPlayer(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static int heldDamage(TestServerContext server) {
        return server.computeOnServer(s -> firstPlayer(s).getMainHandItem().getDamageValue());
    }
}

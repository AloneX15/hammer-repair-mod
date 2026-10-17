package com.takumistudios.hammer_repair.test.modern;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.takumistudios.hammer_repair.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Capturas para el README y las páginas de Modrinth y CurseForge. No es una prueba: solo hace algo con
 * HAMMER_REPAIR_SHOWCASE=&lt;carpeta de destino&gt; (ver docs/capturas.md). Escenario: aldea de llanuras en un mundo
 * plano, mediodía, sin chat ni avisos.
 */
public class HammerRepairShowcaseClientGameTest implements FabricClientGameTest {
    /** Receta predeterminada, fila a fila (aire = casilla vacía). */
    private static final List<Item> DEFAULT_GRID = List.of(
            Items.NETHERITE_BLOCK, Items.NETHER_STAR, Items.NETHERITE_BLOCK,
            Items.NETHERITE_INGOT, Items.HEAVY_CORE, Items.NETHERITE_INGOT,
            Items.AIR, Items.BLAZE_ROD, Items.AIR);

    private Path out;
    private ClientGameTestContext context;

    @Override
    public void runTest(ClientGameTestContext context) {
        String target = System.getenv("HAMMER_REPAIR_SHOWCASE");
        if (target == null || target.isBlank()) {
            return;
        }
        this.out = Path.of(target);
        this.context = context;
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(client -> {
            client.options.chatVisibility().set(ChatVisiblity.HIDDEN);
            client.options.bobView().set(false);
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            waitForChunks(world);
            TestServerContext server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            buildScene(server);
            context.waitTicks(40);
            waitForChunks(world);

            cooperativeRepair(world, server);
            selfRepair(world, server);
            crafting(server);
            banner(world, server);
            icon(world, server);
        }
    }

    private void shot(String name) {
        context.runOnClient(HammerRepairShowcaseClientGameTest::clearToasts);
        context.waitTicks(1);
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(out).disableCounterPrefix());
    }

    /** Aldea al sur del punto de aparición y unos árboles para que el horizonte no quede vacío. */
    private static void buildScene(TestServerContext server) {
        server.runCommand("execute as @a at @s run place structure minecraft:village_plains ~ ~ ~30");
        int[][] trees = {{-9, 14}, {10, 16}, {-14, 26}, {15, 30}, {-6, 40}, {7, 44}};
        for (int[] tree : trees) {
            server.runCommand("execute as @a at @s run place feature minecraft:fancy_oak ~" + tree[0] + " ~ ~" + tree[1]);
        }
        server.runCommand("kill @e[type=minecraft:item]");
    }

    // Diferencias de cliente entre 26.1 y 26.2+ (pantalla, HUD y avisos pasan a Minecraft.gui)
    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    private static void setHudHidden(net.minecraft.client.Minecraft client, boolean hidden) {
        //? if >=26.2 {
        if (client.gui.hud.isHidden() != hidden) {
            client.gui.hud.toggle();
        }
        //?} else {
        /*client.options.hideGui = hidden;
        *///?}
    }

    private static void clearToasts(net.minecraft.client.Minecraft client) {
        //? if >=26.2 {
        client.gui.toastManager().clear();
        //?} else {
        /*client.getToastManager().clear();
        *///?}
    }

    private static void closeScreen(net.minecraft.client.Minecraft client) {
        //? if >=26.2 {
        client.gui.setScreen(null);
        //?} else {
        /*client.setScreen(null);
        *///?}
    }

    /** Quita los mobs de la aldea que se meterían en el plano (los jugadores y los displays se quedan). */
    private static void clearMobs(TestServerContext server, double x, double z, int radius) {
        server.runCommand("kill @e[x=" + x + ",y=-60,z=" + z + ",distance=.." + radius
                + ",type=!minecraft:player,type=!minecraft:item_display,type=!minecraft:text_display]");
    }

    private static ServerPlayer first(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Jugador "real" de prueba (en la lista de jugadores, visible para el cliente) mirando en horizontal. */
    private static ServerPlayer spawnFakePlayer(MinecraftServer server, String name, double x, double y, double z, float yRot,
                                                java.util.function.Consumer<ServerPlayer> equip) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("hammer-repair-" + name).getBytes()), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        // El equipo va antes de entrar: los jugadores de prueba no hacen tick y no enviarían los cambios después
        equip.accept(player);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(x, y, z, yRot, 0.0F);
        player.setYHeadRot(yRot);
        player.setYBodyRot(yRot);
        return player;
    }

    private static void removeFakePlayers(MinecraftServer server, String... names) {
        for (String name : names) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player != null) {
                server.getPlayerList().remove(player);
            }
        }
    }

    private static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    /**
     * Plano de tres cuartos: un herrero golpea con el martillo la armadura de diamante dañada de su compañero. Los dos
     * son jugadores de prueba; el cliente mira en espectador.
     */
    private void cooperativeRepair(TestSingleplayerContext world, TestServerContext server) {
        clearMobs(server, 0.5, -10.0, 14);
        server.runCommand("gamemode spectator @a");
        server.runOnServer(s -> {
            spawnFakePlayer(s, "Partner", 2.1, -60.0, -10.0, 90.0F, partner -> {
                partner.setItemSlot(EquipmentSlot.HEAD, damaged(Items.DIAMOND_HELMET, 300));
                partner.setItemSlot(EquipmentSlot.CHEST, damaged(Items.DIAMOND_CHESTPLATE, 420));
                partner.setItemSlot(EquipmentSlot.LEGS, damaged(Items.DIAMOND_LEGGINGS, 380));
                partner.setItemSlot(EquipmentSlot.FEET, damaged(Items.DIAMOND_BOOTS, 350));
            });
            ServerPlayer smith = spawnFakePlayer(s, "Smith", -0.5, -60.0, -10.0, -90.0F,
                    player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REPAIR_HAMMER)));
            smith.setExperienceLevels(30);
        });
        String camera = server.computeOnServer(s -> first(s).getGameProfile().name());
        server.runCommand("tp " + camera + " -3.0 -59.0 -12.8 -53.6 8");
        context.runOnClient(client -> setHudHidden(client, true));
        context.getInput().lookAt(-53.6F, 8.0F);
        context.waitTicks(30);
        waitForChunks(world);

        // El herrero empieza a usar el martillo como lo haría el paquete de uso del cliente
        server.runOnServer(s -> {
            ServerPlayer smith = s.getPlayerList().getPlayerByName("Smith");
            smith.gameMode.useItem(smith, smith.level(), smith.getMainHandItem(), InteractionHand.MAIN_HAND);
        });
        context.waitTicks(21);
        shot("cooperative_repair");
        context.waitTicks(20);
        shot("cooperative_repair_2");
        server.runOnServer(s -> removeFakePlayers(s, "Smith", "Partner"));
        context.runOnClient(client -> setHudHidden(client, false));
        server.runCommand("gamemode survival @a");
        server.runCommand("xp set @a 30 levels");
    }

    /** Primera persona: pechera dañada en la mano principal y martillo en la segunda mano. */
    private void selfRepair(TestSingleplayerContext world, TestServerContext server) {
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_chestplate[damage=400]");
        server.runCommand("item replace entity @a weapon.offhand with hammer_repair:repair_hammer");
        clearMobs(server, 0.5, -10.0, 14);
        server.runCommand("tp @a 0.5 -60 -12 15 14");
        context.getInput().lookAt(15.0F, 14.0F);
        context.waitTicks(60);
        waitForChunks(world);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(21);
        shot("self_repair");
        context.waitTicks(16);
        shot("self_repair_swing");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        server.runCommand("item replace entity @a weapon.mainhand with air");
        server.runCommand("item replace entity @a weapon.offhand with air");
    }

    /** Mesa de crafteo con la receta predeterminada, sin y con el tooltip del martillo. */
    private void crafting(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = first(s);
            BlockPos table = player.blockPosition().east(2);
            player.level().setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
            player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new CraftingMenu(id, inventory, ContainerLevelAccess.create(player.level(), table)),
                    Component.translatable("container.crafting")));
            if (player.containerMenu instanceof CraftingMenu menu) {
                for (int i = 0; i < DEFAULT_GRID.size(); i++) {
                    Item item = DEFAULT_GRID.get(i);
                    menu.getSlot(1 + i).set(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
                }
            }
        });
        context.waitTicks(10);
        context.getInput().setCursorPos(10, 10);
        context.waitTicks(2);
        shot("crafting");

        // Martillo en la primera casilla del inventario (8, 84 dentro de la ventana de 176x166) y escala 3: el tooltip
        // cabe a la derecha sin tapar la receta
        server.runCommand("item replace entity @a inventory.0 with hammer_repair:repair_hammer");
        context.runOnClient(client -> {
            client.options.guiScale().set(3);
            client.resizeGui();
        });
        context.waitTicks(5);
        double[] cursor = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            int left = (client.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (client.getWindow().getGuiScaledHeight() - 166) / 2;
            return new double[] {(left + 8 + 8) * scale, (top + 84 + 8) * scale};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        shot("crafting_tooltip");
        context.runOnClient(client -> {
            closeScreen(client);
            client.options.guiScale().set(0);
            client.resizeGui();
        });
        server.runCommand("clear @a");
        server.runOnServer(s -> {
            ServerPlayer player = first(s);
            player.level().setBlockAndUpdate(player.blockPosition().east(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        });
        context.waitTicks(5);
    }

    /**
     * Banner 3:1: el martillo grande a la izquierda (display.fixed del modelo) y el título con la fuente de Minecraft,
     * sobre la aldea, sin interfaz.
     */
    private void banner(TestSingleplayerContext world, TestServerContext server) {
        server.runCommand("gamemode spectator @a");
        server.runCommand("kill @e[type=minecraft:item]");
        clearMobs(server, 0.5, -21.0, 12);
        server.runCommand("tp @a 0.5 -59 -26 0 0");
        context.waitTicks(5);
        server.runCommand("execute as @a at @s run summon minecraft:item_display ~4.6 ~1.0 ~5 "
                + "{item:{id:\"hammer_repair:repair_hammer\"},item_display:\"fixed\",Rotation:[180f,0f],"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[4.6f,4.6f,4.6f]}}");
        server.runCommand("execute as @a at @s run summon minecraft:text_display ~-2.4 ~1.55 ~5 "
                + "{text:{text:\"Hammer Repair\",color:\"gold\",bold:true},shadow:1b,background:0,Rotation:[180f,0f],"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[4.4f,4.4f,4.4f]}}");
        server.runCommand("execute as @a at @s run summon minecraft:text_display ~-2.4 ~0.7 ~5 "
                + "{text:{text:\"Repair armor with your XP\",color:\"white\"},shadow:1b,background:0,Rotation:[180f,0f],"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2.0f,2.0f,2.0f]}}");
        context.getInput().resizeWindow(1920, 640);
        context.runOnClient(client -> setHudHidden(client, true));
        context.getInput().lookAt(0.0F, -6.0F);
        context.waitTicks(20);
        waitForChunks(world);
        shot("banner_raw");
        context.runOnClient(client -> setHudHidden(client, false));
    }

    /** Icono cuadrado: el martillo grande delante de hormigón lima (croma para recortarlo después), sin interfaz. */
    private void icon(TestSingleplayerContext world, TestServerContext server) {
        server.runCommand("fill -8 -61 -47 9 -48 -47 minecraft:lime_concrete");
        server.runCommand("fill -8 -61 -46 9 -61 -36 minecraft:lime_concrete");
        server.runCommand("summon minecraft:item_display 0.5 -57.4 -42.6 "
                + "{item:{id:\"hammer_repair:repair_hammer\"},item_display:\"fixed\",Rotation:[0f,0f],"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2.2f,2.2f,2.2f]}}");
        server.runCommand("tp @a 0.5 -59 -40 180 0");
        context.getInput().resizeWindow(1024, 1024);
        context.runOnClient(client -> setHudHidden(client, true));
        context.getInput().lookAt(180.0F, 0.0F);
        context.waitTicks(20);
        waitForChunks(world);
        shot("icon_raw");
        context.runOnClient(client -> setHudHidden(client, false));
    }
}

package com.takumistudios.hammer_repair.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

//? if >=26.3 {
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
//?}
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
*///?}

/**
 * Diferencias de API entre versiones de Minecraft agrupadas en un solo sitio (ramas de Stonecutter). Los renombres
 * exactos ({@code ResourceLocation}/{@code Identifier}) los traduce Stonecutter: ver stonecutter.gradle.kts.
 */
public final class Versions {
    //? if >=26.3 {
    /** Golpe de martillo: el "whack" de vanilla algo más lento y pesado que el ataque normal. */
    private static final SwingAnimation HAMMER_SWING = new SwingAnimation(SwingAnimationType.WHACK, 10);
    //?}

    private Versions() {
    }

    /** Animación del brazo que sostiene el martillo, visible para el propio jugador y para quien lo vea. */
    public static void swingHammer(ServerPlayer player, InteractionHand hand) {
        //? if >=26.3 {
        player.swing(hand, HAMMER_SWING, true);
        //?} else {
        /*player.swing(hand, true);
        *///?}
    }

    /** Aviso en la barra de acción. */
    public static void sendOverlay(ServerPlayer player, Component message) {
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);
        *///?}
    }

    /**
     * Ranura de armadura en la que se equipa la pieza, o {@code null} si no es armadura equipable. Los élitros y
     * similares (planeadores) no cuentan.
     */
    public static EquipmentSlot armorSlot(ItemStack stack) {
        //? if >=26.1 {
        if (stack.has(DataComponents.GLIDER)) {
            return null;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
        //?} else {
        /*if (stack.getItem() instanceof ElytraItem) {
            return null;
        }
        Equipable equipable = Equipable.get(stack);
        return equipable == null ? null : equipable.getEquipmentSlot();
        *///?}
    }

    /** Propiedades base de un objeto: desde 1.21.2 deben llevar su clave de registro. */
    public static Item.Properties itemProperties(ResourceKey<Item> key) {
        //? if >=26.1 {
        return new Item.Properties().setId(key);
        //?} else {
        /*return new Item.Properties();
        *///?}
    }

    /** Añade el objeto a la pestaña "Herramientas y utilidades" del modo creativo. */
    public static void addToToolsTab(Item item) {
        //? if >=26.1 {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(item));
        //?} else {
        /*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(item));
        *///?}
    }
}

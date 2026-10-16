package com.takumistudios.hammer_repair.repair;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

//? if >=26.3 {
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
//?}

/** Diferencias de API entre versiones de Minecraft agrupadas en un solo sitio (ramas de Stonecutter). */
final class Versions {
    //? if >=26.3 {
    /** Golpe de martillo: el "whack" de vanilla algo más lento y pesado que el ataque normal. */
    private static final SwingAnimation HAMMER_SWING = new SwingAnimation(SwingAnimationType.WHACK, 10);
    //?}

    private Versions() {
    }

    /** Animación del brazo que sostiene el martillo, visible para el propio jugador y para quien lo vea. */
    static void swingHammer(ServerPlayer player, InteractionHand hand) {
        //? if >=26.3 {
        player.swing(hand, HAMMER_SWING, true);
        //?} else {
        /*player.swing(hand, true);
        *///?}
    }
}

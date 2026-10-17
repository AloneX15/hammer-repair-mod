package com.takumistudios.hammer_repair.test;

import net.minecraft.gametest.framework.GameTestHelper;

//? if <26.1 {
/*import net.minecraft.gametest.framework.GameTestAssertException;
*///?}

/** Utilidades de los gametests que cambian entre versiones de Minecraft. */
final class TestSupport {
    private TestSupport() {
    }

    /** Excepción que marca la prueba como fallida: {@code throw TestSupport.fail(helper, "...")}. */
    static RuntimeException fail(GameTestHelper helper, String message) {
        //? if >=26.1 {
        return helper.assertionException(message);
        //?} else {
        /*return new GameTestAssertException(message);
        *///?}
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

// El código se escribe con los nombres de 26.x; al compilar 1.21.1 Stonecutter los traduce. Solo renombres exactos:
// las diferencias de comportamiento van en ramas //? if (ver compat/Versions.java)
stonecutter parameters {
    val modern = current.parsed >= "26.1"
    replacements {
        // Mojang renombró ResourceLocation a Identifier en 1.21.11 (con límites de palabra: isIdentifier no cambia)
        regex(modern) {
            replace("\bResourceLocation\b", "Identifier", "\bIdentifier\b", "ResourceLocation")
        }
        // Gametests: anotación de Fabric en 26.x, la de vanilla con estructura vacía en 1.21.1
        string(modern) {
            replace(
                "import net.fabricmc.fabric.api.gametest.v1.FabricGameTest; import net.minecraft.gametest.framework.GameTest; import net.minecraft.gametest.framework.GameTestAssertException;",
                "import net.fabricmc.fabric.api.gametest.v1.GameTest;",
            )
            replace("@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = ", "@GameTest(maxTicks = ")
            replace("throw new GameTestAssertException(", "throw helper.assertionException(")
        }
    }
}

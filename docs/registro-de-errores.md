# Registro de errores

Cada error resuelto con su síntoma, causa, solución y la prueba que evita que vuelva.

## 1.0.0 (desarrollo)

### Desbordamiento de experiencia con niveles enormes

- **Síntoma:** con niveles puestos por comando (`/xp set @s 2000000000 levels`) el total de puntos desbordaba `long` y
  el cobro podía dejar al jugador en un nivel equivocado.
- **Causa:** el total de puntos de un nivel crece con el cuadrado del nivel (`9·L²`).
- **Solución:** `XpMath.subtract` descuenta nivel a nivel desde los puntos dentro del nivel actual (un golpe cuesta como
  mucho 1000 puntos) y los totales se saturan en lugar de desbordar.
- **Test:** `XpMathTest.hugeLevelsDoNotOverflow`, `XpMathTest.subtractMatchesTotalsForEveryCost`.

### Sesión de reparación que seguía sin mantener el botón

- **Síntoma (diseño):** si la predicción del cliente era más estricta que el servidor (otro alcance configurado en el
  servidor, posiciones interpoladas), el servidor empezaba la sesión pero el cliente no se consideraba usando el
  martillo y no enviaba el aviso de soltar el botón.
- **Causa:** vanilla envía siempre el paquete de uso, decida lo que decida el cliente.
- **Solución:** el servidor envía su alcance al cliente (`hammer_repair:settings`) y el cliente predice con un margen
  extra de alcance y de caja de colisión, de modo que nunca es más estricto que el servidor. Además, la sesión comprueba
  en cada tick que la mano sigue sosteniendo el martillo.
- **Test:** `HammerRepairGameTests.switchingAwayFromTheHammerCancels`, `releasingBeforeTheHitChargesNothing` y el test
  de cliente (suelta el botón a mitad de golpe, también contra un servidor dedicado).

### La receta generada no aparecía en los gametests de recetas

- **Síntoma:** `configurableRecipeLifecycle` fallaba en el primer paso: la mesa no fabricaba con la receta
  predeterminada; el log decía que un datapack definía la receta.
- **Causa:** el datapack opcional de pruebas (`datapack_override`) se activa solo al crear un mundo nuevo, y el mod
  respetaba correctamente su prioridad.
- **Solución:** el test parte de los packs seleccionados sin ese datapack y lo activa solo en el paso de sustitución.
- **Test:** el propio `HammerRecipeGameTests.configurableRecipeLifecycle`.

### Gametests que terminaban a los 20 ticks

- **Síntoma:** "Didn't succeed or fail within 20 ticks" en todas las pruebas de reparación.
- **Causa:** `@GameTest` de Fabric usa 20 ticks como máximo por defecto y un golpe dura 20 ticks.
- **Solución:** `maxTicks = 120` en las pruebas de reparación (3600 en la de recetas, que recarga los datos varias veces).

## Cambios de API entre versiones de Minecraft

| Versión | Cambio | Solución |
|---|---|---|
| 26.3 | Las recetas pasan de `RecipeManager.prepare` a un registro recargable cargado por `RegistryDataLoader`. | Dos puntos de inyección: `RecipeManagerMixin` (≤ 26.2) y `RegistryLoadTaskMixin` (26.3). Ver MIXINS.md. |
| 26.3 | `RegistryOps.RegistryInfoLookup.lookup` devuelve `HolderGetter` directamente (antes `RegistryInfo`). | Rama de Stonecutter en `RegistryLoadTaskMixin`. |
| 26.3 | `LivingEntity.swing(hand, bool)` pasa a `swing(hand, SwingAnimation, bool)`. | `Versions.swingHammer` con un golpe `WHACK` de 10 ticks en 26.3. |
| 26.x | `ItemGroupEvents` (fabric-item-group-api) pasa a `CreativeModeTabEvents` (fabric-creative-tab-api-v1). | `CreativeModeTabEvents.modifyOutputEvent(...)`, igual en todas las 26.x. |
| 26.x | `I18n.exists` ya no existe en el cliente. | `Language.getInstance().has(key)`. |

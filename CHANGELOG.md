# Changelog

## Sin publicar

- Partículas de aldeano feliz (`minecraft:happy_villager`) en cada golpe que repara.
- Golpe de martillo al pulsar el clic derecho, sin esperar a la primera reparación.
- Nuevo modelo 3D del martillo hecho en Blockbench (fuente en `art/repair_hammer.bbmodel`), con textura de 128x128
  retocada al estilo de Minecraft sin cambiar su paleta (`tools/paint-hammer-texture.js`).

## 1.0.0

- Martillo de reparación (`hammer_repair:repair_hammer`): permanente, no apilable, sin encantamientos.
- Reparación cooperativa: martillo en la mano principal y clic derecho mantenido sobre otro jugador (3 bloques, línea de
  visión); repara la pieza equipada más desgastada en cada golpe.
- Reparación propia: martillo en la segunda mano y pieza dañada en la principal, con prioridad sobre equiparla.
- Coste en puntos de experiencia del usuario del martillo (2 de durabilidad por punto, 20 por golpe, un golpe por
  segundo), sin cobrar nunca golpes incompletos ni piezas intactas. Gratis en creativo.
- Animación de golpe, sonido de yunque suave, chispas y avisos en la barra de acción.
- Receta configurable en `config/hammer_repair/recipe.json` (esquema versión 2 por casillas, con forma o sin forma,
  etiquetas, objetos de otros mods y alternativas) y `/hammerrepair reload`.
- Parámetros de reparación en `config/hammer_repair/common.json`.
- Prioridad de recetas: datapack > config > valores predeterminados.
- Handshake de red versionado con mensaje claro ante clientes incompatibles.
- Traducciones en español e inglés.
- Soporte para Minecraft 26.1.x, 26.2.x y 26.3.x.

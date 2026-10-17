# Capturas, banner e icono

Todas las imágenes de `docs/images/` salen del juego (Minecraft 26.3 con el mod) y se generan con dos comandos: no
hay retoques a mano.

## Imágenes

| Archivo | Tamaño | Peso | Qué es | Dónde se usa |
|---|---|---|---|---|
| `banner.jpg` | 1920×640 (3:1) | ~175 KB | Martillo grande y título con la fuente de Minecraft sobre una aldea | Cabecera del README y de la descripción; imagen destacada de la galería |
| `cooperative-repair.jpg` | 1600×900 (16:9) | ~240 KB | Un herrero repara la armadura de diamante dañada de su compañero | README, descripción y galería |
| `self-repair.jpg` | 1600×900 (16:9) | ~215 KB | Primera persona: martillo en la segunda mano, pechera dañada en la principal, partículas | README, descripción y galería |
| `recipe.jpg` | 740×700 | ~70 KB | Receta predeterminada en la mesa de crafteo | README, descripción y galería |
| `tooltip.jpg` | 1210×530 | ~100 KB | Tooltip del martillo | README, descripción y galería |
| `icon-512.png` | 512×512 | ~90 KB | Martillo sobre degradado dorado | Icono del proyecto en Modrinth y logo en CurseForge |
| `icon-256.png` | 256×256 | ~35 KB | Lo mismo, más pequeño | Ficha del martillo en el README |
| `hammer.png` | recorte | ~16 KB | El martillo con fondo transparente | Para componer otras imágenes |

Las capturas son JPEG de calidad 88 (sin bandas en el cielo y con el texto de la interfaz legible) y los iconos PNG.
Las de la interfaz se recortan sin reescalar, a la escala de interfaz del juego (4 y 3), para que el texto siga nítido.

## Publicar en Modrinth y CurseForge

**Descripción:** pegar [store/description.md](store/description.md) (Markdown, en inglés). Las imágenes apuntan a
`raw.githubusercontent.com/.../main/docs/images/`, así que se ven en cuanto estas imágenes están en `main`.

**Modrinth** (*Settings*):
- *Icon:* `icon-512.png`.
- *Gallery:* subir las cinco imágenes con estos títulos y marcar el banner como **Featured**:

| Imagen | Título | Descripción |
|---|---|---|
| `banner.jpg` | Hammer Repair | Repair armor with your XP |
| `cooperative-repair.jpg` | Repair a teammate | Hold right-click while aiming at another player |
| `self-repair.jpg` | Repair your own armor | Hammer in the off hand, damaged piece in the main hand |
| `recipe.jpg` | Recipe | Default recipe (configurable) |
| `tooltip.jpg` | Tooltip | How to use it, right in the game |

**CurseForge:**
- *Project avatar / logo:* `icon-512.png`.
- *Description:* el mismo Markdown (en el editor, elegir Markdown).
- *Images:* las mismas cinco imágenes con los mismos títulos.

El icono del mod dentro del juego (`src/main/resources/assets/hammer_repair/icon.png`, el que sale en Mod Menu) sigue
siendo el sprite plano antiguo de `tools/generate-textures.js`.

## Regenerarlas

1. Capturas, con el test de cliente de escaparate, que solo hace algo si existe la variable `HAMMER_REPAIR_SHOWCASE`:
   ```bash
   HAMMER_REPAIR_SHOWCASE="C:\ruta\capturas" ./gradlew :26.3:runClientGameTest
   ```
   Abre un mundo plano y coloca una aldea de llanuras con árboles. Después monta cada escena con comandos y jugadores
   de prueba, y guarda las capturas en bruto (PNG a 1920×1080, 1920×640 y 1024×1024) en esa carpeta. La escena está en
   `src/gametest/java/.../test/modern/HammerRepairShowcaseClientGameTest.java`. En la CI no hace nada.
2. Imágenes finales (Windows, sin dependencias):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools/build-showcase-images.ps1 -Source "C:\ruta\capturas"
   ```
   Recorta, comprime a JPEG y monta el icono: el martillo se renderiza delante de hormigón lima, se recorta por
   croma y se coloca sobre el degradado con una sombra suave.

Si cambia el modelo, la textura o los textos del tooltip, basta con repetir los dos pasos. Los recortes de
`build-showcase-images.ps1` están pensados para esas resoluciones: si cambias el encuadre de una escena, ajusta su
rectángulo.

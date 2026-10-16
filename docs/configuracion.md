# Configuración de Hammer Repair

Archivos en `config/hammer_repair/` de la instancia que ejecuta la partida:

| Instancia | Archivos que mandan |
|---|---|
| Servidor dedicado | Los del servidor, para todos los jugadores conectados |
| Partida individual | Los de la instancia local (servidor integrado) |
| Mundo abierto a LAN | Los del anfitrión |
| Cliente conectado a un servidor | Ninguno: su archivo local no cambia nada |

La configuración se comparte entre todos los mundos de una misma instancia (no hay configuración por mundo en 1.0.0).
JSON no admite comentarios: esta página es la documentación de cada campo. Tamaño máximo de cada archivo: 64 KiB.

## Cuándo se aplica

- **Al arrancar** el servidor (o al abrir el mundo en individual) se leen ambos archivos.
- **`/hammerrepair reload`** (operador de nivel 2 o consola) valida los dos archivos y, solo si todo es válido,
  cancela las reparaciones en curso, aplica los parámetros y recarga los datos del servidor para regenerar la receta.
  Si algo falla no se aplica nada y se conserva la última configuración válida.
- No hay vigilancia continua del archivo ni lecturas por tick. El `/reload` de vanilla vuelve a generar la receta con
  la última configuración aplicada, sin releer los archivos.

## Archivo inexistente o inválido

| Situación | `common.json` | `recipe.json` |
|---|---|---|
| No existe | Se genera con los valores predeterminados | Se genera con la receta predeterminada |
| No se puede escribir | Se usan los predeterminados en memoria (aviso en el log) | Se usa la receta predeterminada en memoria (aviso en el log) |
| Inválido al arrancar | Copia `common.json.<fecha>.bak` y se regenera con los predeterminados | **La receta de config queda desactivada** hasta corregirla y recargar. Nunca se activa la predeterminada. El archivo se conserva sin tocar |
| Inválido al recargar | Se conserva la última configuración válida | Se conserva la última receta válida |
| Esquema desconocido o antiguo | Igual que inválido | Se rechaza con un diagnóstico de versión y se conserva sin sobrescribir |

Los martillos existentes funcionan siempre, tenga o no receta, y las recetas añadidas por datapacks no se ven
afectadas. Los mensajes distinguen errores de **sintaxis** (JSON mal escrito), de **estructura** (campos, filas,
columnas, identificadores) y de **ingredientes ausentes** (objetos o etiquetas que no existen en la instancia), e
indican el campo, la fila y la columna o el número de ingrediente.

## `common.json`

```json
{
  "schema_version": 1,
  "durability_per_xp": 2,
  "max_repair_per_hit": 20,
  "hit_interval_ticks": 20,
  "max_distance": 3.0,
  "require_target_sneaking": false
}
```

| Campo | Tipo | Rango | Predeterminado | Significado |
|---|---|---|---|---|
| `schema_version` | entero | 1 | 1 | Versión del formato |
| `durability_per_xp` | entero | 1–100 | 2 | Durabilidad reparada por punto de experiencia |
| `max_repair_per_hit` | entero | 1–1000 | 20 | Máximo de durabilidad reparada en cada golpe |
| `hit_interval_ticks` | entero | 5–200 | 20 | Duración de cada golpe en ticks (20 = 1 s) |
| `max_distance` | número | 1–6 | 3.0 | Alcance de la reparación cooperativa en bloques |
| `require_target_sneaking` | booleano | | `false` | El compañero debe estar agachado para aceptar la reparación |

Un campo que falte toma su valor predeterminado (con un aviso); un campo desconocido o fuera de rango es un error.

### Coste de cada golpe

```text
reparacion = min(desgaste_actual, max_repair_per_hit, experiencia_disponible × durability_per_xp)
coste      = ceil(reparacion / durability_per_xp)
```

Con los valores predeterminados: reparar 20 cuesta 10 puntos; reparar 6 cuesta 3; si solo falta 1 punto cuesta 1. Se
usan puntos de experiencia (no niveles). En creativo no se cobra.

## `recipe.json` (esquema versión 2)

| Campo | Obligatorio | Valores |
|---|---|---|
| `schema_version` | sí | `2` |
| `enabled` | no (predeterminado `true`) | `false` desactiva solo la receta de fabricación |
| `type` | sí | `"shaped"` (con forma) o `"shapeless"` (sin forma) |
| `ingredients` | sí | Matriz de filas (con forma) o lista (sin forma) |

El resultado es siempre un `hammer_repair:repair_hammer`: no se puede cambiar el objeto ni la cantidad (un campo
`result` es un error).

### Con forma

Cada fila de `ingredients` es una fila de la mesa de crafteo y cada elemento una casilla. `null` es un hueco. Entre 1 y
3 filas, todas con la misma anchura (1 a 3 columnas) y al menos una casilla ocupada.

```json
{
  "schema_version": 2,
  "enabled": true,
  "type": "shaped",
  "ingredients": [
    [{ "item": "minecraft:netherite_block" }, { "item": "minecraft:nether_star" }, { "item": "minecraft:netherite_block" }],
    [{ "item": "minecraft:netherite_ingot" }, { "item": "minecraft:heavy_core" }, { "item": "minecraft:netherite_ingot" }],
    [null, { "item": "minecraft:blaze_rod" }, null]
  ]
}
```

### Sin forma

Lista de 1 a 9 ingredientes; `null` no se admite. Repetir un ingrediente exige varias unidades en casillas distintas.

```json
{
  "schema_version": 2,
  "enabled": true,
  "type": "shapeless",
  "ingredients": [
    { "item": "minecraft:netherite_block" },
    { "item": "minecraft:nether_star" },
    { "item": "minecraft:heavy_core" }
  ]
}
```

### Ingredientes

Cada ingrediente declara exactamente una de estas claves:

| Forma | Ejemplo | Comportamiento |
|---|---|---|
| Objeto | `{ "item": "minecraft:netherite_ingot" }` | Exige ese objeto |
| Objeto de otro mod | `{ "item": "otro_mod:lingote_especial" }` | Exige el objeto registrado por ese mod |
| Etiqueta | `{ "tag": "minecraft:planks" }` | Acepta cualquier objeto de la etiqueta |
| Alternativas | `{ "any_of": [ … ] }` | Acepta cualquiera de las alternativas disponibles |

(`otro_mod:lingote_especial` es un identificador de ejemplo.) Los objetos de otros mods se buscan en el registro del
juego, sin integraciones específicas: el mod tiene que estar instalado en la instancia que ejecuta la partida.

Alternativas: entre 1 y 32 objetos o etiquetas, sin `any_of` anidados ni `null`.

```json
{
  "any_of": [
    { "item": "minecraft:netherite_ingot" },
    { "item": "otro_mod:lingote_especial" }
  ]
}
```

- Una alternativa con un objeto ausente o una etiqueta sin objetos se **omite con un aviso**; si queda al menos una, la
  casilla funciona.
- Si una casilla se queda sin alternativas válidas, o su único objeto no existe, se **rechaza toda la configuración**.
  Ejemplo: `Fila 2, columna 1: el objeto otro_mod:lingote_especial no existe`.
- Un identificador mal formado o una estructura inválida es siempre un error, también dentro de alternativas.
- Con `enabled: false` se valida el esquema y la estructura, pero no se exige que los ingredientes existan.
- Los ingredientes con componentes personalizados no se admiten en 1.0.0.

### Formato anterior

El formato con `pattern` y `key` (como las recetas de datapack) se sustituye por la versión 2 y **no se interpreta
silenciosamente**: se rechaza con un diagnóstico y el archivo se conserva para que lo conviertas.

## Prioridad con datapacks

**datapack > receta generada desde config > valores predeterminados.**

Un datapack puede sustituir deliberadamente la receta definiendo `data/hammer_repair/recipe/repair_hammer.json`. En ese
caso la receta de config no se genera y `/hammerrepair reload` informa de que la sustitución está activa. Desactivar la
receta en config (`enabled: false`) no impide que un administrador añada otra mediante un datapack.

La receta se publica con el identificador estable `hammer_repair:repair_hammer`, se sincroniza a los clientes con el
sistema de recetas del juego y se desbloquea en el libro de recetas de todos los jugadores al entrar y tras cada recarga.

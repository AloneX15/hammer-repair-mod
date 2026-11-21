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
  "require_target_sneaking": false,
  "messages": "all",
  "show_progress": false,
  "notify_target": true,
  "particles": true
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
| `messages` | texto | `"all"`, `"none"` | `"all"` | Avisos en la barra de acción: todos o ninguno ("solo la acción") |
| `show_progress` | booleano | | `false` | Tras cada golpe, la pieza y su durabilidad (`Botas de diamante  Durabilidad: 99 / 429`) |
| `notify_target` | booleano | | `true` | Al empezar, el compañero ve `<jugador> está reparando tu armadura` |
| `particles` | booleano | | `true` | Chispas y partículas de aldeano feliz en cada golpe |

Un campo de la 1.0.x que falte toma su valor predeterminado (con un aviso); un campo desconocido, de un tipo que no
corresponde o fuera de rango es un error. Los cuatro campos de avisos (desde 1.1.0) son **opcionales**: si faltan se
usa el predeterminado sin aviso, así que un `common.json` creado con la 1.0.x sigue igual tras actualizar.

> Volver de la 1.1.x a la 1.0.x con un `common.json` que tenga los campos nuevos lo marca como inválido (campo
> desconocido): se copia a `common.json.<fecha>.bak` y se regenera. Basta con borrar esos campos antes de volver.

### Avisos y efectos

Todos los avisos salen en la **barra de acción** (encima de la barra rápida), como los de vanilla al intentar dormir o
al montar: una línea corta que se borra sola y nunca va al chat. Los que se repiten (sin experiencia, consentimiento…)
no se vuelven a mostrar en 2 segundos.

| Aviso | Lo ve | Se controla con |
|---|---|---|
| Sin experiencia, sin armadura dañada, consentimiento, reparación completada | Reparador | `messages` |
| Durabilidad tras cada golpe | Reparador | `messages` y `show_progress` |
| `<jugador> está reparando tu armadura` (una vez al empezar) | Compañero | `messages` y `notify_target` |
| Martillo desactivado por errores repetidos | Reparador | Siempre visible: indica un fallo del servidor |

- **Solo la acción:** `"messages": "none"`. Se repara y se cobra igual; el jugador ve el golpe del brazo, la barra de
  experiencia bajando y, al reparar su propia pieza, la barra de durabilidad del objeto.
- **Sin partículas:** `"particles": false`. El golpe y el sonido se mantienen. Cada jugador puede además reducir las
  partículas o el volumen con las opciones de vanilla (*Gráficos → Partículas*, *Música y sonidos → Jugadores*).
- La durabilidad usa la traducción de vanilla (`item.durability`), así que sale en el idioma de cada jugador.
- Un cliente 1.0.x conectado a un servidor 1.1.x ve el aviso al compañero en inglés (no tiene esa traducción) y el
  resto igual.

### Tooltip

El tooltip del martillo muestra el coste real del servidor al que estás conectado («1 punto cada N de durabilidad»).
Se actualiza al entrar y tras `/hammerrepair reload`. En el menú principal, o conectado a un servidor 1.0.x, muestra el
texto genérico («Gasta la experiencia de quien usa el martillo»).

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

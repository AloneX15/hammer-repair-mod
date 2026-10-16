# Mixins

Hammer Repair usa las APIs de Fabric para todo lo posible: objeto, interacción (`UseItemCallback` para dar prioridad a
la reparación propia sobre equipar la pieza), ticks, comandos, red y libro de recetas. La animación del golpe es la de
vanilla (`swing`), sin mixins de render.

Solo hay mixins para **añadir la receta generada desde config** al cargar los datos del servidor, porque Fabric API no
ofrece una forma de inyectar recetas construidas en código con la prioridad de datapacks que exige el mod. Los tres
son inyecciones `@Inject` (sin `@Overwrite` ni `@Redirect`), solo actúan sobre el identificador
`hammer_repair:repair_hammer` y nunca modifican recetas de otros. Si su lógica falla, se registra el error y la carga de
datos sigue sin la receta del martillo.

| Mixin | Objetivo | Versiones | Motivo | Riesgo de conflicto |
|---|---|---|---|---|
| `RecipeManagerMixin` | `RecipeManager.prepare` (al volver) | 26.1.x y 26.2.x | Hasta 26.2 las recetas se leen aquí. Si ningún datapack definió `hammer_repair:repair_hammer`, se devuelve el mapa de recetas con la nuestra añadida. En 26.3 el mixin está vacío. | Bajo: solo envuelve el valor devuelto. Otro mod que reemplace el mapa después tendría prioridad, igual que un datapack. |
| `ResourceManagerRegistryLoadTaskMixin` | `ResourceManagerRegistryLoadTask.load` (al empezar) | Todas (solo tiene efecto en 26.3) | Guarda el contexto de carga (objetos y etiquetas nuevas) para el mixin siguiente. Solo lee un argumento. | Muy bajo. |
| `RegistryLoadTaskMixin` | `RegistryLoadTask.registerElements` (al terminar) | Todas (solo tiene efecto en 26.3) | Desde 26.3 las recetas son un registro recargable. Tras registrar las de los datapacks, y antes de congelar el registro, añade la nuestra si ningún datapack la definió. Solo actúa sobre el registro de recetas. | Bajo: no toca otros registros ni otras entradas. |

`defaultRequire` es 1: si una actualización de Minecraft cambia los objetivos, los gametests de la CI fallan en lugar
de dejar el mod sin receta en silencio.

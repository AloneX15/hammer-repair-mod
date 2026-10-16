package com.takumistudios.hammer_repair.recipe;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.config.ConfigResult;
import com.takumistudios.hammer_repair.config.Diagnostic;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.config.RecipeConfig;
import com.takumistudios.hammer_repair.registry.ModItems;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * Convierte la receta de {@code config/hammer_repair/recipe.json} en una receta del juego con el identificador estable
 * {@code hammer_repair:repair_hammer}.
 *
 * <p>Prioridad: <b>datapack &gt; receta generada desde config &gt; valores predeterminados</b>. El mod no incluye ningún
 * JSON de receta, así que si al cargar los datos ya existe ese identificador lo ha puesto un datapack y se respeta.
 * Los puntos de inyección (mixins) dependen de la versión: {@code RecipeManager.prepare} hasta 26.2 y el registro
 * recargable de recetas desde 26.3.
 */
public final class HammerRecipes {
    public static final ResourceKey<Recipe<?>> RECIPE_KEY =
            ResourceKey.create(Registries.RECIPE, HammerRepair.id("repair_hammer"));

    /** Si la última carga de datos encontró la receta definida por un datapack. */
    private static volatile boolean datapackOverride;
    /** Si la última carga de datos generó la receta desde config. */
    private static volatile boolean generated;
    /** Últimos diagnósticos de ingredientes escritos en el log, para no repetirlos en cada recarga. */
    private static volatile List<Diagnostic> lastReported = List.of();

    private HammerRecipes() {
    }

    public static boolean isDatapackOverride() {
        return datapackOverride;
    }

    public static boolean isGenerated() {
        return generated;
    }

    /**
     * Llamado durante la carga de datos del servidor, después de leer las recetas de los datapacks.
     *
     * @param datapackDefinesIt si un datapack ya definió {@code hammer_repair:repair_hammer}
     * @param items             objetos y etiquetas que se están cargando (las etiquetas aún sin contenido)
     * @return la receta a añadir, o vacío si no hay que añadir nada
     */
    public static Optional<Recipe<?>> onRecipesLoading(boolean datapackDefinesIt, HolderGetter<Item> items) {
        generated = false;
        datapackOverride = datapackDefinesIt;
        try {
            // Primera carga de la partida: lee ambos archivos (también si un datapack sustituye la receta)
            HammerConfig.RecipeState state = HammerConfig.recipe();
            if (datapackDefinesIt) {
                LOGGER.info("La receta {} la define un datapack: tiene prioridad sobre config/hammer_repair/recipe.json",
                        RECIPE_KEY.identifier());
                return Optional.empty();
            }
            if (!state.generatesRecipe()) {
                return Optional.empty();
            }
            ConfigResult<RecipeConfig.Resolved> resolved = state.config().resolve(new LoadingLookup(items));
            if (!resolved.isValid()) {
                HammerConfig.log("recipe.json", resolved.diagnostics());
                LOGGER.error("No se pudo generar la receta del martillo con los objetos disponibles; queda desactivada");
                return Optional.empty();
            }
            Recipe<?> recipe = build(resolved.orThrow(), items);
            generated = true;
            return Optional.of(recipe);
        } catch (RuntimeException e) {
            // Nunca romper la carga de datos del servidor por la receta del martillo
            LOGGER.error("Error al generar la receta del martillo; se desactiva hasta la próxima recarga", e);
            return Optional.empty();
        }
    }

    /** Construye la receta vanilla equivalente. */
    public static Recipe<?> build(RecipeConfig.Resolved resolved, HolderGetter<Item> items) {
        Recipe.CommonInfo common = new Recipe.CommonInfo(true);
        CraftingRecipe.CraftingBookInfo book = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.EQUIPMENT, "");
        ItemStackTemplate result = new ItemStackTemplate(ModItems.REPAIR_HAMMER);

        if (resolved.type() == RecipeConfig.Type.SHAPELESS) {
            List<Ingredient> ingredients = new ArrayList<>();
            for (List<RecipeConfig.Simple> slot : resolved.slots()) {
                ingredients.add(toIngredient(slot, items));
            }
            return new ShapelessRecipe(common, book, result, ingredients);
        }

        Map<Character, Ingredient> key = new LinkedHashMap<>();
        List<String> pattern = new ArrayList<>();
        char symbol = 'A';
        for (int row = 0; row < resolved.height(); row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < resolved.width(); column++) {
                List<RecipeConfig.Simple> slot = resolved.slots().get(row * resolved.width() + column);
                if (slot == null) {
                    line.append(ShapedRecipePattern.EMPTY_SLOT);
                } else {
                    key.put(symbol, toIngredient(slot, items));
                    line.append(symbol);
                    symbol++;
                }
            }
            pattern.add(line.toString());
        }
        return new ShapedRecipe(common, book, ShapedRecipePattern.of(key, pattern), result);
    }

    private static Ingredient toIngredient(List<RecipeConfig.Simple> alternatives, HolderGetter<Item> items) {
        List<Item> directItems = new ArrayList<>();
        List<Ingredient> parts = new ArrayList<>();
        for (RecipeConfig.Simple alternative : alternatives) {
            Identifier id = Identifier.parse(alternative.id());
            if (alternative instanceof RecipeConfig.ItemRef) {
                Item item = items.getOrThrow(ResourceKey.create(Registries.ITEM, id)).value();
                if (!directItems.contains(item)) {
                    directItems.add(item);
                }
            } else {
                HolderSet<Item> tag = items.getOrThrow(TagKey.create(Registries.ITEM, id));
                parts.add(Ingredient.of(tag));
            }
        }
        if (!directItems.isEmpty()) {
            parts.addFirst(Ingredient.of(directItems.stream()));
        }
        // Etiquetas y objetos mezclados: ingrediente "cualquiera de" de Fabric API (se evalúa al usarlo)
        return parts.size() == 1 ? parts.getFirst() : DefaultCustomIngredients.any(parts.toArray(Ingredient[]::new));
    }

    /**
     * Consulta durante la carga de datos: las etiquetas existen pero su contenido aún no está enlazado, así que aquí
     * solo se comprueba su existencia. Las vacías se detectan después con {@link #verifyLoaded(MinecraftServer)}.
     */
    private record LoadingLookup(HolderGetter<Item> items) implements RecipeConfig.IngredientLookup {
        @Override
        public boolean itemExists(String id) {
            return itemExistsIn(items, id);
        }

        @Override
        public boolean tagHasItems(String id) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier != null && items.get(TagKey.create(Registries.ITEM, identifier)).isPresent();
        }
    }

    /** Consulta completa sobre los registros ya cargados del servidor (con etiquetas enlazadas). */
    public static RecipeConfig.IngredientLookup liveLookup(MinecraftServer server) {
        HolderGetter<Item> items = server.registryAccess().lookupOrThrow(Registries.ITEM);
        return new RecipeConfig.IngredientLookup() {
            @Override
            public boolean itemExists(String id) {
                return itemExistsIn(items, id);
            }

            @Override
            public boolean tagHasItems(String id) {
                Identifier identifier = Identifier.tryParse(id);
                if (identifier == null) {
                    return false;
                }
                return items.get(TagKey.create(Registries.ITEM, identifier))
                        .map(set -> set.stream().anyMatch(holder -> holder.value() != Items.AIR))
                        .orElse(false);
            }
        };
    }

    private static boolean itemExistsIn(HolderGetter<Item> items, String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return false;
        }
        return items.get(ResourceKey.create(Registries.ITEM, identifier))
                .filter(holder -> holder.value() != Items.AIR)
                .isPresent();
    }

    /**
     * Tras cargar los datos (con las etiquetas ya enlazadas) comprueba la receta generada y registra avisos de
     * alternativas vacías. Si una casilla se ha quedado sin objetos la receta no se puede fabricar: se informa.
     */
    public static ConfigResult<RecipeConfig.Resolved> verifyLoaded(MinecraftServer server) {
        HammerConfig.RecipeState state = HammerConfig.recipe();
        if (!generated || !state.generatesRecipe()) {
            return null;
        }
        ConfigResult<RecipeConfig.Resolved> result = state.config().resolve(liveLookup(server));
        if (!result.diagnostics().isEmpty() && !result.diagnostics().equals(lastReported)) {
            HammerConfig.log("recipe.json", result.diagnostics());
        }
        lastReported = result.diagnostics();
        if (!result.isValid()) {
            LOGGER.error("La receta del martillo tiene casillas sin objetos válidos y no se podrá fabricar; corrige recipe.json y usa /hammerrepair reload");
        }
        return result;
    }

    /** El comando de recarga ya ha mostrado estos diagnósticos: la verificación posterior no los repite. */
    public static void markReported(List<Diagnostic> diagnostics) {
        lastReported = List.copyOf(diagnostics);
    }

    /** Desbloquea la receta en el libro de recetas de los jugadores indicados. */
    public static void unlockFor(MinecraftServer server, Iterable<ServerPlayer> players) {
        Optional<RecipeHolder<?>> holder = server.getRecipeManager().byKey(RECIPE_KEY);
        if (holder.isEmpty()) {
            return;
        }
        List<RecipeHolder<?>> list = List.of(holder.get());
        for (ServerPlayer player : players) {
            player.awardRecipes(list);
        }
    }
}

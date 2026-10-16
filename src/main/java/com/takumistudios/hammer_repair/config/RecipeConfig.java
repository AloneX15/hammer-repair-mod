package com.takumistudios.hammer_repair.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Receta del martillo en el esquema propio versión 2 ({@code config/hammer_repair/recipe.json}).
 *
 * <p>Este es el resultado de validar la estructura; los objetos y etiquetas aún no se han comprobado contra los
 * registros del juego (eso lo hace {@link #resolve(IngredientLookup)} cuando el servidor ha cargado sus etiquetas).
 *
 * @param slots en recetas con forma, las casillas fila a fila ({@code null} = hueco); sin forma, los ingredientes
 */
public record RecipeConfig(boolean enabled, Type type, int width, int height, List<IngredientSpec> slots) {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_ALTERNATIVES = 32;

    private static final Set<String> KNOWN_FIELDS = Set.of("schema_version", "enabled", "type", "ingredients");

    public static final String DEFAULT_JSON = """
            {
              "schema_version": 2,
              "enabled": true,
              "type": "shaped",
              "ingredients": [
                [
                  { "item": "minecraft:netherite_block" },
                  { "item": "minecraft:nether_star" },
                  { "item": "minecraft:netherite_block" }
                ],
                [
                  { "item": "minecraft:netherite_ingot" },
                  { "item": "minecraft:heavy_core" },
                  { "item": "minecraft:netherite_ingot" }
                ],
                [
                  null,
                  { "item": "minecraft:blaze_rod" },
                  null
                ]
              ]
            }
            """;

    public RecipeConfig {
        slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public enum Type { SHAPED, SHAPELESS }

    /** Ingrediente de una casilla: objeto, etiqueta o alternativas (que solo contienen objetos y etiquetas). */
    public sealed interface IngredientSpec permits Simple, AnyOf {
    }

    /** Alternativa simple: objeto o etiqueta. */
    public sealed interface Simple extends IngredientSpec permits ItemRef, TagRef {
        String id();
    }

    public record ItemRef(String id) implements Simple {
    }

    public record TagRef(String id) implements Simple {
    }

    public record AnyOf(List<Simple> alternatives) implements IngredientSpec {
        public AnyOf {
            alternatives = List.copyOf(alternatives);
        }
    }

    /** Consulta de registros: se implementa sobre los del juego o con datos falsos en los tests. */
    public interface IngredientLookup {
        boolean itemExists(String id);

        /** {@code true} si la etiqueta existe y contiene al menos un objeto. */
        boolean tagHasItems(String id);
    }

    /** Receta lista para construir: cada casilla tiene al menos una alternativa válida. */
    public record Resolved(Type type, int width, int height, List<List<Simple>> slots) {
        public Resolved {
            List<List<Simple>> copy = new ArrayList<>();
            for (List<Simple> slot : slots) {
                copy.add(slot == null ? null : List.copyOf(slot));
            }
            slots = Collections.unmodifiableList(copy);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Validación de estructura
    // ---------------------------------------------------------------------------------------------------------------

    public static ConfigResult<RecipeConfig> parse(String text) {
        List<Diagnostic> out = new ArrayList<>();
        JsonElement root = JsonSupport.parse(text, out);
        if (root == null) {
            return ConfigResult.failure(out);
        }
        if (!root.isJsonObject()) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.FILE, "root_not_object"));
            return ConfigResult.failure(out);
        }
        JsonObject json = root.getAsJsonObject();

        // Esquema: el formato anterior (pattern + key) nunca se interpreta como el nuevo
        boolean legacy = json.has("pattern") || json.has("key");
        Integer schema = JsonSupport.asExactInt(json.get("schema_version"));
        if (schema == null || schema != SCHEMA_VERSION) {
            if (legacy) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.FILE, "legacy_format"));
            } else if (!json.has("schema_version")) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("schema_version"), "missing_field"));
            } else {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("schema_version"),
                        "schema_unsupported", json.get("schema_version"), SCHEMA_VERSION));
            }
            return ConfigResult.failure(out);
        }

        for (String key : json.keySet()) {
            if (key.equals("pattern") || key.equals("key")) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field(key), "legacy_format"));
            } else if (key.equals("result")) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field(key), "result_fixed"));
            } else if (!KNOWN_FIELDS.contains(key)) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field(key), "unknown_field"));
            }
        }

        boolean enabled = true;
        if (json.has("enabled")) {
            Boolean value = JsonSupport.asBoolean(json.get("enabled"));
            if (value == null) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("enabled"), "expected_boolean"));
            } else {
                enabled = value;
            }
        }

        Type type = null;
        if (!json.has("type")) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("type"), "missing_field"));
        } else {
            String value = JsonSupport.asString(json.get("type"));
            if ("shaped".equals(value)) {
                type = Type.SHAPED;
            } else if ("shapeless".equals(value)) {
                type = Type.SHAPELESS;
            } else {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("type"),
                        "invalid_recipe_type", value != null ? value : json.get("type")));
            }
        }

        if (!json.has("ingredients")) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("ingredients"), "missing_field"));
            return ConfigResult.failure(out);
        }
        if (!json.get("ingredients").isJsonArray()) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("ingredients"), "expected_array"));
            return ConfigResult.failure(out);
        }
        if (type == null) {
            return ConfigResult.failure(out);
        }

        JsonArray ingredients = json.getAsJsonArray("ingredients");
        return type == Type.SHAPED ? parseShaped(enabled, ingredients, out) : parseShapeless(enabled, ingredients, out);
    }

    private static ConfigResult<RecipeConfig> parseShaped(boolean enabled, JsonArray rows, List<Diagnostic> out) {
        Diagnostic.Location field = Diagnostic.Location.field("ingredients");
        if (rows.isEmpty() || rows.size() > 3) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, field, "shaped_rows", rows.size()));
            return ConfigResult.failure(out);
        }
        int width = -1;
        List<IngredientSpec> slots = new ArrayList<>();
        boolean anyOccupied = false;
        for (int r = 0; r < rows.size(); r++) {
            JsonElement rowElement = rows.get(r);
            if (!rowElement.isJsonArray()) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, field.child(String.valueOf(r + 1)), "expected_array"));
                return ConfigResult.failure(out);
            }
            JsonArray row = rowElement.getAsJsonArray();
            if (row.isEmpty() || row.size() > 3) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, field.child(String.valueOf(r + 1)), "shaped_columns", row.size()));
                return ConfigResult.failure(out);
            }
            if (width < 0) {
                width = row.size();
            } else if (row.size() != width) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, field, "shaped_uneven"));
                return ConfigResult.failure(out);
            }
            for (int c = 0; c < row.size(); c++) {
                JsonElement cell = row.get(c);
                if (cell.isJsonNull()) {
                    slots.add(null);
                    continue;
                }
                anyOccupied = true;
                slots.add(parseIngredient(cell, Diagnostic.Location.slot(r + 1, c + 1), true, out));
            }
        }
        if (!anyOccupied) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, field, "shaped_empty"));
        }
        return ConfigResult.of(new RecipeConfig(enabled, Type.SHAPED, width, rows.size(), slots), out);
    }

    private static ConfigResult<RecipeConfig> parseShapeless(boolean enabled, JsonArray list, List<Diagnostic> out) {
        if (list.isEmpty() || list.size() > 9) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("ingredients"), "shapeless_count", list.size()));
            return ConfigResult.failure(out);
        }
        List<IngredientSpec> slots = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JsonElement cell = list.get(i);
            Diagnostic.Location where = Diagnostic.Location.ingredient(i + 1);
            if (cell.isJsonNull()) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "null_not_allowed"));
                continue;
            }
            slots.add(parseIngredient(cell, where, true, out));
        }
        return ConfigResult.of(new RecipeConfig(enabled, Type.SHAPELESS, 0, 0, slots), out);
    }

    /** Devuelve el ingrediente o {@code null} si es inválido (en ese caso ya se ha añadido el error). */
    private static IngredientSpec parseIngredient(JsonElement element, Diagnostic.Location where, boolean allowAnyOf,
                                                  List<Diagnostic> out) {
        if (element.isJsonNull()) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "null_not_allowed"));
            return null;
        }
        if (!element.isJsonObject()) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "expected_object"));
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        if (json.has("components")) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "components_unsupported"));
            return null;
        }
        int forms = (json.has("item") ? 1 : 0) + (json.has("tag") ? 1 : 0) + (json.has("any_of") ? 1 : 0);
        if (forms != 1) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "ingredient_one_key"));
            return null;
        }
        boolean valid = true;
        for (String key : json.keySet()) {
            if (!key.equals("item") && !key.equals("tag") && !key.equals("any_of")) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where.child(key), "unknown_field"));
                valid = false;
            }
        }
        if (json.has("any_of")) {
            if (!allowAnyOf) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "nested_any_of"));
                return null;
            }
            JsonElement value = json.get("any_of");
            if (!value.isJsonArray()) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where.child("any_of"), "expected_array"));
                return null;
            }
            JsonArray array = value.getAsJsonArray();
            if (array.isEmpty() || array.size() > MAX_ALTERNATIVES) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "any_of_size", array.size()));
                return null;
            }
            List<Simple> alternatives = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                IngredientSpec alternative = parseIngredient(array.get(i), where.alternative(i + 1), false, out);
                if (alternative instanceof Simple simple) {
                    alternatives.add(simple);
                } else {
                    valid = false;
                }
            }
            return valid ? new AnyOf(alternatives) : null;
        }
        String key = json.has("item") ? "item" : "tag";
        String id = JsonSupport.asString(json.get(key));
        if (id == null) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where.child(key), "expected_string"));
            return null;
        }
        if (id.startsWith("#") && key.equals("tag")) {
            id = id.substring(1); // se acepta "#minecraft:planks" por costumbre de los datapacks
        }
        if (!JsonSupport.isIdentifier(id)) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "invalid_id", id));
            return null;
        }
        if (!valid) {
            return null;
        }
        return key.equals("item") ? new ItemRef(id) : new TagRef(id);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Resolución contra los registros del juego
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Comprueba objetos y etiquetas. Las alternativas ausentes se omiten con un aviso; una casilla sin ninguna
     * alternativa válida (o cuyo único objeto no existe) invalida la configuración completa.
     */
    public ConfigResult<Resolved> resolve(IngredientLookup lookup) {
        List<Diagnostic> out = new ArrayList<>();
        List<List<Simple>> resolved = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            IngredientSpec spec = slots.get(i);
            if (spec == null) {
                resolved.add(null);
                continue;
            }
            Diagnostic.Location where = type == Type.SHAPED
                    ? Diagnostic.Location.slot(i / width + 1, i % width + 1)
                    : Diagnostic.Location.ingredient(i + 1);
            resolved.add(resolveSlot(spec, where, lookup, out));
        }
        return ConfigResult.of(new Resolved(type, width, height, resolved), out);
    }

    private static List<Simple> resolveSlot(IngredientSpec spec, Diagnostic.Location where, IngredientLookup lookup,
                                            List<Diagnostic> out) {
        switch (spec) {
            case ItemRef item -> {
                if (!lookup.itemExists(item.id())) {
                    out.add(Diagnostic.error(Diagnostic.Category.MISSING, where, "item_missing", item.id()));
                    return List.of();
                }
                return List.of(item);
            }
            case TagRef tag -> {
                if (!lookup.tagHasItems(tag.id())) {
                    out.add(Diagnostic.error(Diagnostic.Category.MISSING, where, "tag_missing", tag.id()));
                    return List.of();
                }
                return List.of(tag);
            }
            case AnyOf anyOf -> {
                List<Simple> valid = new ArrayList<>();
                for (int a = 0; a < anyOf.alternatives().size(); a++) {
                    Simple alternative = anyOf.alternatives().get(a);
                    Diagnostic.Location at = where.alternative(a + 1);
                    if (alternative instanceof ItemRef && !lookup.itemExists(alternative.id())) {
                        out.add(Diagnostic.warning(Diagnostic.Category.MISSING, at, "alt_item_missing", alternative.id()));
                    } else if (alternative instanceof TagRef && !lookup.tagHasItems(alternative.id())) {
                        out.add(Diagnostic.warning(Diagnostic.Category.MISSING, at, "alt_tag_missing", alternative.id()));
                    } else if (!valid.contains(alternative)) {
                        valid.add(alternative);
                    }
                }
                if (valid.isEmpty()) {
                    out.add(Diagnostic.error(Diagnostic.Category.MISSING, where, "slot_no_valid"));
                }
                return valid;
            }
        }
    }
}

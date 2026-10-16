package com.takumistudios.hammer_repair.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeConfigTest {
    /** Registro falso: vanilla más un mod "otro_mod" que se puede quitar. */
    private static final RecipeConfig.IngredientLookup VANILLA = lookup(
            Set.of("minecraft:netherite_block", "minecraft:nether_star", "minecraft:netherite_ingot", "minecraft:heavy_core",
                    "minecraft:blaze_rod", "minecraft:oak_planks"),
            Set.of("minecraft:planks"));
    private static final RecipeConfig.IngredientLookup WITH_MOD = lookup(
            Set.of("minecraft:netherite_ingot", "minecraft:blaze_rod", "otro_mod:lingote_especial"),
            Set.of("minecraft:planks", "otro_mod:lingotes"));

    private static RecipeConfig.IngredientLookup lookup(Set<String> items, Set<String> tagsWithItems) {
        return new RecipeConfig.IngredientLookup() {
            @Override
            public boolean itemExists(String id) {
                return items.contains(id);
            }

            @Override
            public boolean tagHasItems(String id) {
                return tagsWithItems.contains(id);
            }
        };
    }

    private static ConfigResult<RecipeConfig> parse(String json) {
        return RecipeConfig.parse(json);
    }

    private static String only(ConfigResult<?> result) {
        List<Diagnostic> errors = result.errors();
        assertEquals(1, errors.size(), () -> "errores: " + result.diagnostics());
        return errors.getFirst().describe();
    }

    @Test
    void defaultRecipeIsValidAndResolves() {
        ConfigResult<RecipeConfig> result = parse(RecipeConfig.DEFAULT_JSON);
        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        RecipeConfig config = result.orThrow();
        assertTrue(config.enabled());
        assertEquals(RecipeConfig.Type.SHAPED, config.type());
        assertEquals(3, config.width());
        assertEquals(3, config.height());
        assertNull(config.slots().get(6));
        assertEquals(new RecipeConfig.ItemRef("minecraft:heavy_core"), config.slots().get(4));
        ConfigResult<RecipeConfig.Resolved> resolved = config.resolve(VANILLA);
        assertTrue(resolved.isValid());
        assertTrue(resolved.diagnostics().isEmpty());
    }

    @Test
    void shapelessRecipe() {
        ConfigResult<RecipeConfig> result = parse("""
                {"schema_version": 2, "enabled": true, "type": "shapeless", "ingredients": [
                  {"item": "minecraft:netherite_block"}, {"item": "minecraft:nether_star"}, {"item": "minecraft:heavy_core"},
                  {"item": "minecraft:heavy_core"}]}
                """);
        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        assertEquals(4, result.orThrow().slots().size(), "repetir un ingrediente exige varias unidades");
        assertTrue(result.orThrow().resolve(VANILLA).isValid());
    }

    @Test
    void tagsAndHashPrefixedTags() {
        ConfigResult<RecipeConfig> result = parse("""
                {"schema_version": 2, "type": "shapeless", "ingredients": [{"tag": "minecraft:planks"}, {"tag": "#minecraft:planks"}]}
                """);
        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        assertEquals(new RecipeConfig.TagRef("minecraft:planks"), result.orThrow().slots().get(1));
        assertTrue(result.orThrow().resolve(VANILLA).isValid());
    }

    @Test
    void missingModItemGivesPreciseDiagnostic() {
        RecipeConfig config = parse("""
                {"schema_version": 2, "type": "shaped", "ingredients": [
                  [null, {"item": "minecraft:netherite_ingot"}],
                  [{"item": "otro_mod:lingote_especial"}, null]]}
                """).orThrow();
        ConfigResult<RecipeConfig.Resolved> resolved = config.resolve(VANILLA);
        assertFalse(resolved.isValid());
        assertEquals("Fila 2, columna 1: el objeto otro_mod:lingote_especial no existe", only(resolved));
        assertEquals(Diagnostic.Category.MISSING, resolved.errors().getFirst().category());
        // Con el mod instalado la misma configuración funciona
        assertTrue(config.resolve(WITH_MOD).isValid());
    }

    @Test
    void anyOfSkipsMissingAlternativesWithWarning() {
        RecipeConfig config = parse("""
                {"schema_version": 2, "type": "shapeless", "ingredients": [
                  {"any_of": [{"item": "minecraft:netherite_ingot"}, {"item": "otro_mod:lingote_especial"}, {"tag": "otro_mod:lingotes"}]}]}
                """).orThrow();
        ConfigResult<RecipeConfig.Resolved> resolved = config.resolve(VANILLA);
        assertTrue(resolved.isValid());
        assertEquals(2, resolved.warnings().size());
        assertEquals(List.of(new RecipeConfig.ItemRef("minecraft:netherite_ingot")), resolved.orThrow().slots().getFirst());
        assertEquals("Ingrediente 1, alternativa 2: se omite la alternativa otro_mod:lingote_especial: el objeto no existe",
                resolved.warnings().getFirst().describe());

        ConfigResult<RecipeConfig.Resolved> withMod = config.resolve(WITH_MOD);
        assertTrue(withMod.isValid());
        assertEquals(3, withMod.orThrow().slots().getFirst().size());
    }

    @Test
    void anyOfWithoutValidAlternativesRejectsWholeConfig() {
        RecipeConfig config = parse("""
                {"schema_version": 2, "type": "shaped", "ingredients": [[
                  {"item": "minecraft:blaze_rod"},
                  {"any_of": [{"item": "otro_mod:a"}, {"tag": "otro_mod:vacia"}]}]]}
                """).orThrow();
        ConfigResult<RecipeConfig.Resolved> resolved = config.resolve(VANILLA);
        assertFalse(resolved.isValid());
        assertEquals("Fila 1, columna 2: ninguna alternativa es válida", only(resolved));
    }

    @Test
    void emptyTagAloneIsAnError() {
        RecipeConfig config = parse("""
                {"schema_version": 2, "type": "shapeless", "ingredients": [{"tag": "otro_mod:vacia"}]}
                """).orThrow();
        assertEquals("Ingrediente 1: la etiqueta otro_mod:vacia no existe o no contiene objetos", only(config.resolve(VANILLA)));
    }

    @Test
    void malformedIdentifierIsAlwaysAnErrorEvenInsideAlternatives() {
        ConfigResult<RecipeConfig> result = parse("""
                {"schema_version": 2, "type": "shapeless", "ingredients": [
                  {"any_of": [{"item": "minecraft:netherite_ingot"}, {"item": "Otro Mod:Lingote"}]}]}
                """);
        assertFalse(result.isValid());
        assertEquals("Ingrediente 1, alternativa 2: identificador «Otro Mod:Lingote» mal formado (usa namespace:objeto)", only(result));
        assertEquals(Diagnostic.Category.STRUCTURE, result.errors().getFirst().category());
    }

    @Test
    void matrixWithGapsAndSmallerShapes() {
        ConfigResult<RecipeConfig> result = parse("""
                {"schema_version": 2, "type": "shaped", "ingredients": [[{"item": "minecraft:blaze_rod"}, null], [null, {"item": "minecraft:blaze_rod"}]]}
                """);
        assertTrue(result.isValid());
        assertEquals(2, result.orThrow().width());
        assertEquals(2, result.orThrow().height());
    }

    @Test
    void shapedStructureErrors() {
        assertEquals("Campo «ingredients»: todas las filas deben tener la misma anchura",
                only(parse("{\"schema_version\":2,\"type\":\"shaped\",\"ingredients\":[[null,{\"item\":\"minecraft:stick\"}],[null]]}")));
        assertEquals("Campo «ingredients»: una receta con forma necesita de 1 a 3 filas (hay 4)",
                only(parse("{\"schema_version\":2,\"type\":\"shaped\",\"ingredients\":[[null],[null],[null],[null]]}")));
        assertEquals("Campo «ingredients»: la receta necesita al menos una casilla ocupada",
                only(parse("{\"schema_version\":2,\"type\":\"shaped\",\"ingredients\":[[null,null]]}")));
        assertEquals("Campo «ingredients.1»: cada fila necesita de 1 a 3 columnas (hay 4)",
                only(parse("{\"schema_version\":2,\"type\":\"shaped\",\"ingredients\":[[null,null,null,null]]}")));
    }

    @Test
    void shapelessStructureErrors() {
        assertEquals("Ingrediente 2: null solo representa una casilla vacía en una receta con forma",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"},null]}")));
        assertEquals("Campo «ingredients»: una receta sin forma necesita de 1 a 9 ingredientes (hay 0)",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[]}")));
        String ten = "{\"item\":\"minecraft:stick\"},".repeat(10);
        assertEquals("Campo «ingredients»: una receta sin forma necesita de 1 a 9 ingredientes (hay 10)",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[" + ten.substring(0, ten.length() - 1) + "]}")));
    }

    @Test
    void ingredientShapeErrors() {
        assertEquals("Ingrediente 1: cada ingrediente debe tener exactamente una de las claves item, tag o any_of",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\",\"tag\":\"minecraft:planks\"}]}")));
        assertEquals("Ingrediente 1, alternativa 1: any_of no admite alternativas anidadas",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"any_of\":[{\"any_of\":[{\"item\":\"minecraft:stick\"}]}]}]}")));
        assertEquals("Ingrediente 1, alternativa 2: null solo representa una casilla vacía en una receta con forma",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"any_of\":[{\"item\":\"minecraft:stick\"},null]}]}")));
        assertEquals("Ingrediente 1: any_of necesita entre 1 y 32 alternativas (hay 0)",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"any_of\":[]}]}")));
        String many = "{\"item\":\"minecraft:stick\"},".repeat(33);
        assertEquals("Ingrediente 1: any_of necesita entre 1 y 32 alternativas (hay 33)",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"any_of\":[" + many.substring(0, many.length() - 1) + "]}]}")));
        assertEquals("Ingrediente 1: los ingredientes con componentes personalizados no se admiten en 1.0.0",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\",\"components\":{}}]}")));
        assertEquals("Ingrediente 1: debe ser un objeto",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[\"minecraft:stick\"]}")));
    }

    @Test
    void legacyFormatIsRejectedNotReinterpreted() {
        ConfigResult<RecipeConfig> result = parse("""
                {"type": "shaped", "pattern": ["BSB", "ICI", " R "], "key": {"B": {"item": "minecraft:netherite_block"}}}
                """);
        assertFalse(result.isValid());
        assertEquals("El formato anterior con «pattern» y «key» ya no se admite; usa el esquema versión 2 por casillas", only(result));
        // Aunque declare la versión 2, pattern/key siguen siendo un error
        assertFalse(parse("{\"schema_version\":2,\"type\":\"shaped\",\"pattern\":[\"A\"],\"ingredients\":[[{\"item\":\"minecraft:stick\"}]]}").isValid());
    }

    @Test
    void unknownSchemaVersionsAreRejected() {
        assertEquals("Campo «schema_version»: versión de esquema 3 no soportada; se esperaba 2",
                only(parse("{\"schema_version\":3,\"type\":\"shapeless\",\"ingredients\":[]}")));
        assertEquals("Campo «schema_version»: falta este campo obligatorio",
                only(parse("{\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}]}")));
    }

    @Test
    void unknownFieldsTypeAndResultAreRejected() {
        assertEquals("Campo «extra»: campo desconocido",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"extra\":1,\"ingredients\":[{\"item\":\"minecraft:stick\"}]}")));
        assertEquals("Campo «result»: el resultado es fijo (un hammer_repair:repair_hammer) y no se puede configurar",
                only(parse("{\"schema_version\":2,\"type\":\"shapeless\",\"result\":{\"id\":\"minecraft:diamond\"},\"ingredients\":[{\"item\":\"minecraft:stick\"}]}")));
        assertEquals("Campo «type»: tipo «smithing» no válido; usa «shaped» o «shapeless»",
                only(parse("{\"schema_version\":2,\"type\":\"smithing\",\"ingredients\":[]}")));
        assertEquals("Campo «enabled»: debe ser true o false",
                only(parse("{\"schema_version\":2,\"enabled\":\"no\",\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}]}")));
    }

    @Test
    void disabledRecipeValidatesStructureOnly() {
        ConfigResult<RecipeConfig> result = parse("""
                {"schema_version": 2, "enabled": false, "type": "shapeless", "ingredients": [{"item": "otro_mod:no_instalado"}]}
                """);
        assertTrue(result.isValid(), "con la receta desactivada no se exige resolver ingredientes");
        assertFalse(result.orThrow().enabled());
        assertFalse(parse("{\"schema_version\":2,\"enabled\":false,\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"MAL\"}]}").isValid());
    }

    @Test
    void syntaxErrorsAreReportedAsSyntax() {
        ConfigResult<RecipeConfig> comments = parse("{ // comentario\n \"schema_version\": 2 }");
        assertFalse(comments.isValid());
        assertEquals(Diagnostic.Category.SYNTAX, comments.errors().getFirst().category());
        ConfigResult<RecipeConfig> trailing = parse("{\"schema_version\": 2,}");
        assertEquals(Diagnostic.Category.SYNTAX, trailing.errors().getFirst().category());
        assertEquals(Diagnostic.Category.STRUCTURE, parse("[]").errors().getFirst().category());
    }

    @Test
    void oversizedFileIsRejected() {
        String huge = "{\"schema_version\":2,\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"minecraft:stick\"}],\"x\":\""
                + "a".repeat(JsonSupport.MAX_FILE_BYTES) + "\"}";
        ConfigResult<RecipeConfig> result = parse(huge);
        assertFalse(result.isValid());
        assertEquals("too_large", result.errors().getFirst().code());
    }
}

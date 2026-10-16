package com.takumistudios.hammer_repair.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de archivo inexistente, inválido y recarga sobre un directorio temporal. */
class HammerConfigTest {
    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        HammerConfig.setDirectory(dir);
        HammerConfig.reset();
    }

    @AfterEach
    void tearDown() {
        HammerConfig.reset();
    }

    private String read(String name) throws IOException {
        return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void missingFilesAreGeneratedWithDefaults() throws IOException {
        HammerConfig.RecipeState state = HammerConfig.recipe();
        assertTrue(state.generatesRecipe());
        assertEquals(RecipeConfig.DEFAULT_JSON, read("recipe.json"));
        assertEquals(CommonConfig.DEFAULT_JSON, read("common.json"));
        assertEquals(CommonConfig.DEFAULTS, HammerConfig.common());
    }

    @Test
    void invalidRecipeAtStartupDisablesRecipeAndKeepsFile() throws IOException {
        String broken = "{\"schema_version\": 2, \"type\": \"shaped\", \"ingredients\": [[{\"item\": \"BAD ID\"}]]}";
        Files.writeString(dir.resolve("recipe.json"), broken);
        HammerConfig.RecipeState state = HammerConfig.recipe();
        assertNull(state.config(), "nunca se activa la receta predeterminada con un archivo inválido");
        assertFalse(state.generatesRecipe());
        assertFalse(state.diagnostics().isEmpty());
        assertEquals(broken, read("recipe.json"), "el archivo original se conserva");
    }

    @Test
    void unknownSchemaIsKeptWithoutOverwriting() throws IOException {
        String future = "{\"schema_version\": 9, \"new_feature\": true}";
        Files.writeString(dir.resolve("recipe.json"), future);
        assertFalse(HammerConfig.recipe().generatesRecipe());
        assertEquals(future, read("recipe.json"));
    }

    @Test
    void invalidCommonConfigIsBackedUpAndRegenerated() throws IOException {
        Files.writeString(dir.resolve("common.json"), "{\"schema_version\": 1, \"durability_per_xp\": 0}");
        HammerConfig.recipe();
        assertEquals(CommonConfig.DEFAULTS, HammerConfig.common());
        assertEquals(CommonConfig.DEFAULT_JSON, read("common.json"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".bak")).count());
        }
    }

    @Test
    void invalidReloadKeepsLastValidConfiguration() throws IOException {
        Files.writeString(dir.resolve("common.json"), CommonConfig.DEFAULT_JSON.replace("\"durability_per_xp\": 2", "\"durability_per_xp\": 5"));
        HammerConfig.RecipeState before = HammerConfig.recipe();
        assertEquals(5, HammerConfig.common().durabilityPerXp());

        // El usuario rompe ambos archivos: la lectura los rechaza y el estado no cambia
        Files.writeString(dir.resolve("common.json"), "{\"schema_version\": 1, \"durability_per_xp\": 500}");
        Files.writeString(dir.resolve("recipe.json"), "{ esto no es json");
        HammerConfig.Loaded loaded = HammerConfig.readFromDisk();
        assertFalse(loaded.common().isValid());
        assertFalse(loaded.recipe().isValid());
        assertEquals(Diagnostic.Category.SYNTAX, loaded.recipe().errors().getFirst().category());
        assertEquals(5, HammerConfig.common().durabilityPerXp());
        assertEquals(before, HammerConfig.recipe());
    }

    @Test
    void validReloadReplacesConfiguration() throws IOException {
        HammerConfig.recipe();
        Files.writeString(dir.resolve("recipe.json"), """
                {"schema_version": 2, "enabled": false, "type": "shapeless", "ingredients": [{"item": "minecraft:stick"}]}
                """);
        HammerConfig.Loaded loaded = HammerConfig.readFromDisk();
        assertTrue(loaded.recipe().isValid());
        HammerConfig.apply(loaded.common().orThrow(), loaded.recipe().orThrow(), loaded.recipe().diagnostics());
        assertFalse(HammerConfig.recipe().generatesRecipe(), "enabled: false desactiva solo la receta");
    }

    @Test
    void oversizedFileIsNotRead() throws IOException {
        Files.writeString(dir.resolve("recipe.json"), " ".repeat(JsonSupport.MAX_FILE_BYTES + 10));
        ConfigFiles.Read read = ConfigFiles.read(dir.resolve("recipe.json"));
        assertEquals(ConfigFiles.Read.Status.FAILED, read.status());
        assertEquals("too_large", read.diagnostics().getFirst().code());
    }

    @Test
    void byteOrderMarkIsAccepted() throws IOException {
        Files.writeString(dir.resolve("common.json"), "﻿" + CommonConfig.DEFAULT_JSON);
        HammerConfig.recipe();
        assertEquals(CommonConfig.DEFAULT_JSON, read("common.json").substring(1), "no se regenera un archivo válido");
    }
}

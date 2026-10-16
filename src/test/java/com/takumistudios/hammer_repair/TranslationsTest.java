package com.takumistudios.hammer_repair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.hammer_repair.config.Diagnostic;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Traducciones completas: mismas claves, mismos argumentos y todos los diagnósticos traducidos. */
class TranslationsTest {
    private static JsonObject load(String language) throws Exception {
        try (InputStream in = TranslationsTest.class.getResourceAsStream("/assets/hammer_repair/lang/" + language + ".json")) {
            assertNotNull(in, language);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static int placeholders(String text) {
        Matcher matcher = Pattern.compile("%s").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void sameKeysAndPlaceholders() throws Exception {
        JsonObject en = load("en_us");
        JsonObject es = load("es_es");
        assertEquals(en.keySet(), es.keySet());
        for (String key : en.keySet()) {
            assertEquals(placeholders(en.get(key).getAsString()), placeholders(es.get(key).getAsString()), key);
        }
    }

    @Test
    void everyDiagnosticIsTranslated() throws Exception {
        JsonObject es = load("es_es");
        JsonObject en = load("en_us");
        for (String code : Diagnostic.knownCodes()) {
            assertTrue(es.has("hammer_repair.diagnostic." + code), code);
            assertTrue(en.has("hammer_repair.diagnostic." + code), code);
        }
        for (Diagnostic.Category category : Diagnostic.Category.values()) {
            assertTrue(en.has("hammer_repair.diagnostic.category." + category.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Test
    void logTextMatchesSpanishTranslation() throws Exception {
        JsonObject es = load("es_es");
        for (String code : Diagnostic.knownCodes()) {
            Object[] args = new Object[placeholders(es.get("hammer_repair.diagnostic." + code).getAsString())];
            java.util.Arrays.fill(args, "x");
            Diagnostic diagnostic = Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("f"), code, args);
            String expected = "Campo «f»: " + String.format(java.util.Locale.ROOT, es.get(diagnostic.translationKey()).getAsString(), args);
            assertEquals(expected, diagnostic.describe(), code);
        }
    }
}

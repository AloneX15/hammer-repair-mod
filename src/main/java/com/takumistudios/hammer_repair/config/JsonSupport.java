package com.takumistudios.hammer_repair.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Lectura estricta de JSON y utilidades comunes de validación. */
public final class JsonSupport {
    /** Tamaño máximo de un archivo de configuración: 64 KiB. */
    public static final int MAX_FILE_BYTES = 64 * 1024;

    /** Mismas reglas que {@code Identifier} de Minecraft: namespace y ruta en minúsculas. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private JsonSupport() {
    }

    /** Parsea texto JSON estricto (sin comentarios ni comas sobrantes) o añade un error de sintaxis. */
    public static JsonElement parse(String text, List<Diagnostic> out) {
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_FILE_BYTES) {
            out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "too_large", bytes, MAX_FILE_BYTES));
            return null;
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "syntax", "contenido sobrante tras el JSON"));
                return null;
            }
            return element;
        } catch (JsonParseException | IOException | IllegalStateException e) {
            String detail = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
            out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "syntax", detail));
            return null;
        }
    }

    public static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    /** Entero exacto representable como {@code int}, o {@code null} si el valor no lo es. */
    public static Integer asExactInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber()) {
            return null;
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public static Double asNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber()) {
            return null;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) ? value : null;
    }

    public static Boolean asBoolean(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isBoolean()) {
            return null;
        }
        return element.getAsBoolean();
    }

    public static String asString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isString()) {
            return null;
        }
        return element.getAsString();
    }
}

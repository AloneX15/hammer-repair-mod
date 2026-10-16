package com.takumistudios.hammer_repair.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un aviso o error de configuración con su ubicación (campo, fila/columna o ingrediente).
 *
 * <p>{@link #describe()} da el texto en español para el log; {@link #translationKey()} y {@link #args()} permiten
 * mostrarlo traducido al jugador u operador (claves {@code hammer_repair.diagnostic.*} y {@code hammer_repair.location.*}).
 */
public record Diagnostic(Severity severity, Category category, Location location, String code, List<String> args) {
    public enum Severity { ERROR, WARNING }

    /** Distingue errores de sintaxis, de estructura y de ingredientes ausentes, como pide la especificación. */
    public enum Category { SYNTAX, STRUCTURE, MISSING, RANGE }

    /** Plantillas en español (las mismas que en es_es.json) para el log del servidor. */
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("syntax", "error de sintaxis JSON: %s"),
            Map.entry("too_large", "el archivo ocupa %s bytes; el máximo es %s"),
            Map.entry("read_error", "no se pudo leer el archivo: %s"),
            Map.entry("root_not_object", "la raíz debe ser un objeto JSON"),
            Map.entry("missing_field", "falta este campo obligatorio"),
            Map.entry("missing_field_default", "falta este campo; se usa el valor predeterminado %s"),
            Map.entry("unknown_field", "campo desconocido"),
            Map.entry("result_fixed", "el resultado es fijo (un hammer_repair:repair_hammer) y no se puede configurar"),
            Map.entry("expected_integer", "debe ser un número entero"),
            Map.entry("expected_number", "debe ser un número"),
            Map.entry("expected_boolean", "debe ser true o false"),
            Map.entry("expected_string", "debe ser un texto"),
            Map.entry("expected_array", "debe ser una lista"),
            Map.entry("expected_object", "debe ser un objeto"),
            Map.entry("schema_unsupported", "versión de esquema %s no soportada; se esperaba %s"),
            Map.entry("legacy_format", "el formato anterior con «pattern» y «key» ya no se admite; usa el esquema versión 2 por casillas"),
            Map.entry("invalid_recipe_type", "tipo «%s» no válido; usa «shaped» o «shapeless»"),
            Map.entry("shaped_rows", "una receta con forma necesita de 1 a 3 filas (hay %s)"),
            Map.entry("shaped_columns", "cada fila necesita de 1 a 3 columnas (hay %s)"),
            Map.entry("shaped_uneven", "todas las filas deben tener la misma anchura"),
            Map.entry("shaped_empty", "la receta necesita al menos una casilla ocupada"),
            Map.entry("shapeless_count", "una receta sin forma necesita de 1 a 9 ingredientes (hay %s)"),
            Map.entry("null_not_allowed", "null solo representa una casilla vacía en una receta con forma"),
            Map.entry("ingredient_one_key", "cada ingrediente debe tener exactamente una de las claves item, tag o any_of"),
            Map.entry("components_unsupported", "los ingredientes con componentes personalizados no se admiten en 1.0.0"),
            Map.entry("invalid_id", "identificador «%s» mal formado (usa namespace:objeto)"),
            Map.entry("any_of_size", "any_of necesita entre 1 y 32 alternativas (hay %s)"),
            Map.entry("nested_any_of", "any_of no admite alternativas anidadas"),
            Map.entry("item_missing", "el objeto %s no existe"),
            Map.entry("tag_missing", "la etiqueta %s no existe o no contiene objetos"),
            Map.entry("alt_item_missing", "se omite la alternativa %s: el objeto no existe"),
            Map.entry("alt_tag_missing", "se omite la alternativa %s: la etiqueta no existe o no contiene objetos"),
            Map.entry("slot_no_valid", "ninguna alternativa es válida"),
            Map.entry("out_of_range", "el valor %s está fuera del rango %s–%s")
    );

    public static Diagnostic error(Category category, Location location, String code, Object... args) {
        return new Diagnostic(Severity.ERROR, category, location, code, stringify(args));
    }

    public static Diagnostic warning(Category category, Location location, String code, Object... args) {
        return new Diagnostic(Severity.WARNING, category, location, code, stringify(args));
    }

    private static List<String> stringify(Object... args) {
        return java.util.Arrays.stream(args).map(String::valueOf).toList();
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String translationKey() {
        return "hammer_repair.diagnostic." + code;
    }

    /** Texto en español con ubicación, p. ej. "Fila 2, columna 1: el objeto otro_mod:lingote_especial no existe". */
    public String describe() {
        String template = TEMPLATES.getOrDefault(code, code);
        String message = String.format(Locale.ROOT, template, args.toArray());
        String where = location.describe();
        return where.isEmpty() ? capitalize(message) : where + ": " + message;
    }

    /** Códigos con plantilla; los tests comprueban que todos tienen traducción. */
    public static java.util.Set<String> knownCodes() {
        return TEMPLATES.keySet();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Ubicación de un diagnóstico. Filas, columnas, índices y alternativas empiezan en 1; 0 significa "no aplica".
     */
    public record Location(String field, int row, int column, int index, int alternative) {
        public static final Location FILE = new Location("", 0, 0, 0, 0);

        public static Location field(String field) {
            return new Location(field, 0, 0, 0, 0);
        }

        public static Location slot(int row, int column) {
            return new Location("", row, column, 0, 0);
        }

        public static Location ingredient(int index) {
            return new Location("", 0, 0, index, 0);
        }

        public Location alternative(int alternative) {
            return new Location(field, row, column, index, alternative);
        }

        public Location child(String name) {
            return new Location(field.isEmpty() ? name : field + "." + name, row, column, index, alternative);
        }

        /** Clave de traducción de la ubicación; los argumentos van en {@link #translationArgs()}. */
        public String translationKey() {
            String base;
            if (row > 0) {
                base = "slot";
            } else if (index > 0) {
                base = "ingredient";
            } else if (!field.isEmpty()) {
                base = "field";
            } else {
                return "";
            }
            if (alternative > 0) {
                base += "_alternative";
            }
            return "hammer_repair.location." + base;
        }

        public List<String> translationArgs() {
            List<String> args = new java.util.ArrayList<>();
            if (row > 0) {
                args.add(String.valueOf(row));
                args.add(String.valueOf(column));
            } else if (index > 0) {
                args.add(String.valueOf(index));
            } else if (!field.isEmpty()) {
                args.add(field);
            }
            if (alternative > 0 && !args.isEmpty()) {
                args.add(String.valueOf(alternative));
            }
            return args;
        }

        public String describe() {
            String text;
            if (row > 0) {
                text = "Fila " + row + ", columna " + column;
            } else if (index > 0) {
                text = "Ingrediente " + index;
            } else if (!field.isEmpty()) {
                text = "Campo «" + field + "»";
            } else {
                return "";
            }
            return alternative > 0 ? text + ", alternativa " + alternative : text;
        }
    }
}

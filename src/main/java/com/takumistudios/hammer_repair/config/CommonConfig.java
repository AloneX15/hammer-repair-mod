package com.takumistudios.hammer_repair.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parámetros de reparación ({@code config/hammer_repair/common.json}). */
public record CommonConfig(
        int durabilityPerXp,
        int maxRepairPerHit,
        int hitIntervalTicks,
        double maxDistance,
        boolean requireTargetSneaking
) {
    public static final int SCHEMA_VERSION = 1;
    public static final CommonConfig DEFAULTS = new CommonConfig(2, 20, 20, 3.0, false);

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "schema_version", "durability_per_xp", "max_repair_per_hit", "hit_interval_ticks", "max_distance",
            "require_target_sneaking");

    public static final String DEFAULT_JSON = """
            {
              "schema_version": 1,
              "durability_per_xp": 2,
              "max_repair_per_hit": 20,
              "hit_interval_ticks": 20,
              "max_distance": 3.0,
              "require_target_sneaking": false
            }
            """;

    public static ConfigResult<CommonConfig> parse(String text) {
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

        if (!json.has("schema_version")) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("schema_version"), "missing_field"));
            return ConfigResult.failure(out);
        }
        Integer schema = JsonSupport.asExactInt(json.get("schema_version"));
        if (schema == null || schema != SCHEMA_VERSION) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("schema_version"), "schema_unsupported",
                    json.get("schema_version"), SCHEMA_VERSION));
            return ConfigResult.failure(out);
        }
        for (String key : json.keySet()) {
            if (!KNOWN_FIELDS.contains(key)) {
                out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field(key), "unknown_field"));
            }
        }

        int efficiency = readInt(json, "durability_per_xp", DEFAULTS.durabilityPerXp, 1, 100, out);
        int maxPerHit = readInt(json, "max_repair_per_hit", DEFAULTS.maxRepairPerHit, 1, 1000, out);
        int interval = readInt(json, "hit_interval_ticks", DEFAULTS.hitIntervalTicks, 5, 200, out);
        double distance = readDouble(json, "max_distance", DEFAULTS.maxDistance, 1.0, 6.0, out);
        boolean sneaking = readBoolean(json, "require_target_sneaking", DEFAULTS.requireTargetSneaking, out);
        return ConfigResult.of(new CommonConfig(efficiency, maxPerHit, interval, distance, sneaking), out);
    }

    private static int readInt(JsonObject json, String key, int fallback, int min, int max, List<Diagnostic> out) {
        Diagnostic.Location where = Diagnostic.Location.field(key);
        if (!json.has(key)) {
            out.add(Diagnostic.warning(Diagnostic.Category.STRUCTURE, where, "missing_field_default", fallback));
            return fallback;
        }
        Integer value = JsonSupport.asExactInt(json.get(key));
        if (value == null) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "expected_integer"));
            return fallback;
        }
        if (value < min || value > max) {
            out.add(Diagnostic.error(Diagnostic.Category.RANGE, where, "out_of_range", value, min, max));
            return fallback;
        }
        return value;
    }

    private static double readDouble(JsonObject json, String key, double fallback, double min, double max, List<Diagnostic> out) {
        Diagnostic.Location where = Diagnostic.Location.field(key);
        if (!json.has(key)) {
            out.add(Diagnostic.warning(Diagnostic.Category.STRUCTURE, where, "missing_field_default", fallback));
            return fallback;
        }
        Double value = JsonSupport.asNumber(json.get(key));
        if (value == null) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "expected_number"));
            return fallback;
        }
        if (value < min || value > max) {
            out.add(Diagnostic.error(Diagnostic.Category.RANGE, where, "out_of_range",
                    String.format(Locale.ROOT, "%s", value), min, max));
            return fallback;
        }
        return value;
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback, List<Diagnostic> out) {
        Diagnostic.Location where = Diagnostic.Location.field(key);
        if (!json.has(key)) {
            out.add(Diagnostic.warning(Diagnostic.Category.STRUCTURE, where, "missing_field_default", fallback));
            return fallback;
        }
        Boolean value = JsonSupport.asBoolean(json.get(key));
        if (value == null) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, where, "expected_boolean"));
            return fallback;
        }
        return value;
    }
}

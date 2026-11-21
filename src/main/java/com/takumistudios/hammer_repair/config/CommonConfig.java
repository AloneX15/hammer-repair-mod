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
        boolean requireTargetSneaking,
        MessageMode messages,
        boolean showProgress,
        boolean notifyTarget,
        boolean particles
) {
    public static final int SCHEMA_VERSION = 1;
    public static final CommonConfig DEFAULTS = new CommonConfig(2, 20, 20, 3.0, false, MessageMode.ALL, false, true, true);

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "schema_version", "durability_per_xp", "max_repair_per_hit", "hit_interval_ticks", "max_distance",
            "require_target_sneaking", "messages", "show_progress", "notify_target", "particles");

    public static final String DEFAULT_JSON = """
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
            """;

    /** Avisos en la barra de acción: todos, o ninguno ("solo la acción": golpe, sonido y partículas). */
    public enum MessageMode {
        ALL("all"), NONE("none");

        private final String id;

        MessageMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static MessageMode byId(String id) {
            for (MessageMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /** Si se envían avisos de feedback en la barra de acción (los de fallo del mod se envían siempre). */
    public boolean sendsMessages() {
        return messages == MessageMode.ALL;
    }

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
        // Campos de 1.1.0: opcionales y sin aviso si faltan, para que un common.json de 1.0.x cargue igual que antes
        MessageMode messages = readMessageMode(json, out);
        boolean progress = readOptionalBoolean(json, "show_progress", DEFAULTS.showProgress, out);
        boolean notifyTarget = readOptionalBoolean(json, "notify_target", DEFAULTS.notifyTarget, out);
        boolean particles = readOptionalBoolean(json, "particles", DEFAULTS.particles, out);
        return ConfigResult.of(new CommonConfig(efficiency, maxPerHit, interval, distance, sneaking, messages, progress,
                notifyTarget, particles), out);
    }

    private static MessageMode readMessageMode(JsonObject json, List<Diagnostic> out) {
        if (!json.has("messages")) {
            return DEFAULTS.messages;
        }
        String id = JsonSupport.asString(json.get("messages"));
        MessageMode mode = id == null ? null : MessageMode.byId(id);
        if (mode == null) {
            out.add(Diagnostic.error(Diagnostic.Category.STRUCTURE, Diagnostic.Location.field("messages"), "invalid_choice",
                    json.get("messages"), "\"all\", \"none\""));
            return DEFAULTS.messages;
        }
        return mode;
    }

    private static boolean readOptionalBoolean(JsonObject json, String key, boolean fallback, List<Diagnostic> out) {
        return json.has(key) ? readBoolean(json, key, fallback, out) : fallback;
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

package com.takumistudios.hammer_repair.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonConfigTest {
    @Test
    void defaultsMatchSpecification() {
        ConfigResult<CommonConfig> result = CommonConfig.parse(CommonConfig.DEFAULT_JSON);
        assertTrue(result.isValid());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(CommonConfig.DEFAULTS, result.orThrow());
        assertEquals(new CommonConfig(2, 20, 20, 3.0, false, CommonConfig.MessageMode.ALL, false, true, true), CommonConfig.DEFAULTS);
    }

    @Test
    void customValuesAtRangeLimits() {
        ConfigResult<CommonConfig> result = CommonConfig.parse("""
                {"schema_version": 1, "durability_per_xp": 100, "max_repair_per_hit": 1, "hit_interval_ticks": 5,
                 "max_distance": 6, "require_target_sneaking": true, "messages": "none", "show_progress": true,
                 "notify_target": false, "particles": false}
                """);
        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        assertEquals(new CommonConfig(100, 1, 5, 6.0, true, CommonConfig.MessageMode.NONE, true, false, false), result.orThrow());
        assertFalse(result.orThrow().sendsMessages());
    }

    /** Un common.json de 1.0.x (sin los campos de 1.1.0) carga igual que antes y sin avisos nuevos en el log. */
    @Test
    void configFrom100LoadsWithoutNewWarnings() {
        ConfigResult<CommonConfig> result = CommonConfig.parse("""
                {"schema_version": 1, "durability_per_xp": 4, "max_repair_per_hit": 20, "hit_interval_ticks": 20,
                 "max_distance": 3.0, "require_target_sneaking": true}
                """);
        assertTrue(result.isValid());
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        CommonConfig config = result.orThrow();
        assertEquals(4, config.durabilityPerXp());
        assertTrue(config.requireTargetSneaking());
        assertEquals(CommonConfig.MessageMode.ALL, config.messages());
        assertFalse(config.showProgress());
        assertTrue(config.notifyTarget());
        assertTrue(config.particles());
    }

    @Test
    void invalidFeedbackValuesAreRejected() {
        for (String value : new String[] {"\"few\"", "\"ALL\"", "true", "0", "null"}) {
            ConfigResult<CommonConfig> result = CommonConfig.parse("{\"schema_version\":1,\"messages\":" + value + "}");
            assertFalse(result.isValid(), "messages=" + value);
            assertEquals("invalid_choice", result.errors().getFirst().code());
        }
        assertEquals("expected_boolean", CommonConfig.parse("{\"schema_version\":1,\"show_progress\":1}").errors().getFirst().code());
        assertEquals("expected_boolean", CommonConfig.parse("{\"schema_version\":1,\"notify_target\":\"yes\"}").errors().getFirst().code());
        assertEquals("expected_boolean", CommonConfig.parse("{\"schema_version\":1,\"particles\":null}").errors().getFirst().code());
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertRange("durability_per_xp", 0);
        assertRange("durability_per_xp", 101);
        assertRange("max_repair_per_hit", 1001);
        assertRange("hit_interval_ticks", 4);
        assertRange("hit_interval_ticks", 201);
        assertRange("max_distance", 0.5);
        assertRange("max_distance", 6.5);
    }

    private static void assertRange(String field, Object value) {
        ConfigResult<CommonConfig> result = CommonConfig.parse("{\"schema_version\":1,\"" + field + "\":" + value + "}");
        assertFalse(result.isValid(), field + "=" + value);
        assertEquals(1, result.errors().size());
        assertEquals("out_of_range", result.errors().getFirst().code());
        assertEquals(Diagnostic.Category.RANGE, result.errors().getFirst().category());
    }

    @Test
    void wrongTypesAreRejected() {
        assertEquals("expected_integer", CommonConfig.parse("{\"schema_version\":1,\"durability_per_xp\":2.5}").errors().getFirst().code());
        assertEquals("expected_integer", CommonConfig.parse("{\"schema_version\":1,\"hit_interval_ticks\":\"20\"}").errors().getFirst().code());
        assertEquals("expected_boolean", CommonConfig.parse("{\"schema_version\":1,\"require_target_sneaking\":1}").errors().getFirst().code());
    }

    @Test
    void unknownSchemaAndFieldsAreRejected() {
        assertEquals("schema_unsupported", CommonConfig.parse("{\"schema_version\":2}").errors().getFirst().code());
        assertEquals("missing_field", CommonConfig.parse("{}").errors().getFirst().code());
        assertEquals("unknown_field", CommonConfig.parse("{\"schema_version\":1,\"speed\":3}").errors().getFirst().code());
    }

    @Test
    void missingFieldsUseDefaultsWithWarning() {
        ConfigResult<CommonConfig> result = CommonConfig.parse("{\"schema_version\":1,\"durability_per_xp\":4}");
        assertTrue(result.isValid());
        assertEquals(4, result.orThrow().durabilityPerXp());
        assertEquals(CommonConfig.DEFAULTS.maxRepairPerHit(), result.orThrow().maxRepairPerHit());
        assertEquals(4, result.warnings().size());
    }
}

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
        assertEquals(new CommonConfig(2, 20, 20, 3.0, false), CommonConfig.DEFAULTS);
    }

    @Test
    void customValuesAtRangeLimits() {
        ConfigResult<CommonConfig> result = CommonConfig.parse("""
                {"schema_version": 1, "durability_per_xp": 100, "max_repair_per_hit": 1, "hit_interval_ticks": 5,
                 "max_distance": 6, "require_target_sneaking": true}
                """);
        assertTrue(result.isValid(), () -> result.diagnostics().toString());
        assertEquals(new CommonConfig(100, 1, 5, 6.0, true), result.orThrow());
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

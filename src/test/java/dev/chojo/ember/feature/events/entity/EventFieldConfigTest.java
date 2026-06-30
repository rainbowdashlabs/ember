/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventFieldConfigTest {

    @Test
    void parseEmptyReturnsEmpty() {
        var empty = EventFieldConfig.parse("{}");
        assertNull(empty.options());
        assertNull(empty.groupId());
        assertNull(empty.userType());
        assertNull(empty.tagId());
        assertFalse(empty.selfRegistration());
        assertSame(empty, EventFieldConfig.parse(null));
        assertSame(empty, EventFieldConfig.parse(""));
        assertSame(empty, EventFieldConfig.parse("  "));
    }

    @Test
    void parseInvalidReturnsEmpty() {
        var fallback = EventFieldConfig.parse("not json");
        assertSame(fallback, EventFieldConfig.parse("{"));
    }

    @Test
    void roundTripGroupConfig() {
        var cfg = new EventFieldConfig(null, 7, null, null, true);
        var parsed = EventFieldConfig.parse(cfg.toJson());
        assertEquals(7, parsed.groupId());
        assertTrue(parsed.selfRegistration());
        assertNull(parsed.userType());
        assertNull(parsed.tagId());
    }

    @Test
    void roundTripUserTypeConfig() {
        var cfg = new EventFieldConfig(null, null, StationUserType.TEAM, null, true);
        var parsed = EventFieldConfig.parse(cfg.toJson());
        assertEquals(StationUserType.TEAM, parsed.userType());
    }

    @Test
    void roundTripTagConfig() {
        var cfg = new EventFieldConfig(null, null, null, 12, false);
        var parsed = EventFieldConfig.parse(cfg.toJson());
        assertEquals(12, parsed.tagId());
        assertFalse(parsed.selfRegistration());
    }

    @Test
    void roundTripEnumOptions() {
        var cfg = new EventFieldConfig(List.of("a", "b"), null, null, null, false);
        var parsed = EventFieldConfig.parse(cfg.toJson());
        assertEquals(List.of("a", "b"), parsed.options());
    }

    @Test
    void unknownKeysAreIgnored() {
        var parsed = EventFieldConfig.parse("{\"options\":[\"x\"],\"unknownKey\":42}");
        assertEquals(List.of("x"), parsed.options());
    }
}

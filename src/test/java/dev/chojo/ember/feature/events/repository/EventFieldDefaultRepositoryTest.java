/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFieldDefaultRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Field Default Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void replaceAndReadDefaults() {
        var template = attendanceRepo.createTemplate(station.id(), "FDTemplate");
        attendanceRepo.createTemplateField(
                template.id(), "SignedBy", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        int templateFieldId =
                attendanceRepo.findTemplateFields(template.id()).getFirst().id();

        var event = eventRepo.create(
                station.id(),
                "FieldDefault Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-12-01T10:00:00Z"),
                Instant.parse("2026-12-01T12:00:00Z"),
                template.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            eventFieldDefaultRepo.replaceForEvent(
                    event.id(), List.of(new EventFieldDefault(event.id(), templateFieldId, "VALUE", "value1")));
            var found = eventFieldDefaultRepo.findByEvent(event.id());
            assertFalse(found.isEmpty());
            assertEquals(templateFieldId, found.getFirst().fieldId());
            assertEquals("VALUE", found.getFirst().source());
            assertEquals("value1", found.getFirst().value());

            eventFieldDefaultRepo.replaceForEvent(event.id(), List.of());
            assertTrue(eventFieldDefaultRepo.findByEvent(event.id()).isEmpty());
        } finally {
            eventRepo.delete(event.id());
            attendanceRepo.deleteTemplate(template.id());
        }
    }

    @Test
    void findForUnknownEventIsEmpty() {
        assertTrue(eventFieldDefaultRepo.findByEvent(99999).isEmpty());
    }
}

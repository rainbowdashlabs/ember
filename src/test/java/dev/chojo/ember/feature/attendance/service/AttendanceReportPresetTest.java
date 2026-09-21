/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A saved report filter comes back as it was saved: every user type and every group, and never a
 * filter that selects nobody.
 */
class AttendanceReportPresetTest extends RepositoryTestBase {

    private static AttendanceReportService service;
    private static Station station;
    private static int firstGroupId;
    private static int secondGroupId;

    @BeforeAll
    static void setup() {
        service = new AttendanceReportService(
                attendanceRepo, stationMemberRepo, accountRepo, stationRepo, memberGroupRepo, new Api());
        station = stationRepo.create("Preset Station");
        firstGroupId = memberGroupRepo.create(station.id(), "First").id();
        secondGroupId = memberGroupRepo.create(station.id(), "Second").id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void twoUserTypesAndTwoGroupsComeBackWhole() {
        var saved = service.createPreset(
                station.id(),
                "Both",
                List.of(StationUserType.MEMBER, StationUserType.GUARDIAN),
                List.of(firstGroupId, secondGroupId),
                "quarter",
                "round");

        var read = service.findPresets(station.id()).stream()
                .filter(p -> p.id() == saved.id())
                .findFirst()
                .orElseThrow();

        assertEquals(List.of(StationUserType.MEMBER, StationUserType.GUARDIAN), read.userTypes());
        assertEquals(List.of(firstGroupId, secondGroupId), read.groupIds());
        assertEquals("quarter", read.period());
        assertEquals("round", read.rounding());
    }

    @Test
    void aPresetOfGroupsAloneNeedsNoUserType() {
        var saved = service.createPreset(station.id(), "Groups", null, List.of(firstGroupId), "month", "exact");

        assertTrue(saved.userTypes().isEmpty());
        assertEquals(List.of(firstGroupId), saved.groupIds());
    }

    @Test
    void aGroupNamedTwiceIsKeptOnce() {
        var saved = service.createPreset(
                station.id(), "Twice", List.of(), List.of(firstGroupId, firstGroupId), "month", "exact");

        assertEquals(List.of(firstGroupId), saved.groupIds());
    }

    @Test
    void aPresetSelectingNobodyIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.createPreset(station.id(), "Nobody", List.of(), List.of(), "month", "exact"));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createPreset(station.id(), "Nobody", null, null, "month", "exact"));
    }

    @Test
    void anEmptyEntryInASelectionIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.createPreset(
                        station.id(),
                        "Hole",
                        Arrays.asList(StationUserType.MEMBER, null),
                        List.of(),
                        "month",
                        "exact"));
    }
}

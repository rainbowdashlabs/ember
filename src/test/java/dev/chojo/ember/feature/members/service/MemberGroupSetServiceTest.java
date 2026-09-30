/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.members.entity.MemberGroupSet;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Naming, renaming and deleting the sets of groups a station keeps. */
class MemberGroupSetServiceTest extends RepositoryTestBase {
    private final MemberGroupSetService service = new MemberGroupSetService(new MemberGroupSetRepository());
    private Station station;

    @BeforeEach
    void freshStation() {
        station = stationRepo.create("Set Station " + System.nanoTime());
    }

    private static Refusal refusalOf(Runnable call) {
        return assertThrows(RefusalResponse.class, call::run).refusal();
    }

    @Test
    void aSetIsCreatedRenamedAndDeleted() {
        var set = service.create(station.id(), "  Stufen ");
        assertEquals("Stufen", set.name());

        assertEquals(
                new MemberGroupSet(set.id(), station.id(), "Level"), service.rename(station.id(), set.id(), "Level"));
        assertEquals(
                List.of("Level"),
                service.findByStation(station.id()).stream()
                        .map(MemberGroupSet::name)
                        .toList());

        service.delete(station.id(), set.id());
        assertEquals(List.of(), service.findByStation(station.id()));
    }

    @Test
    void aSetNeedsAFreeName() {
        var set = service.create(station.id(), "Stufen");
        service.create(station.id(), "Andere");

        assertEquals(Refusal.GROUP_SET_NAME_MISSING_ON_CREATE, refusalOf(() -> service.create(station.id(), " ")));
        assertEquals(Refusal.GROUP_SET_NAME_MISSING_ON_CREATE, refusalOf(() -> service.create(station.id(), null)));
        assertEquals(Refusal.GROUP_SET_NAME_TAKEN_ON_CREATE, refusalOf(() -> service.create(station.id(), "Stufen")));
        assertEquals(
                Refusal.GROUP_SET_NAME_MISSING_ON_CHANGE,
                refusalOf(() -> service.rename(station.id(), set.id(), null)));
        assertEquals(
                Refusal.GROUP_SET_NAME_TAKEN_ON_CHANGE,
                refusalOf(() -> service.rename(station.id(), set.id(), "Andere")));
    }

    @Test
    void aSetOfAnotherStationCannotBeReached() {
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        var foreign = service.create(elsewhere.id(), "Fremd");

        assertEquals(
                Refusal.GROUP_SET_NOT_HERE_ON_CHANGE,
                refusalOf(() -> service.rename(station.id(), foreign.id(), "Meins")));
        assertEquals(Refusal.GROUP_SET_NOT_HERE_ON_DELETE, refusalOf(() -> service.delete(station.id(), foreign.id())));
        stationRepo.delete(elsewhere.id());
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What an association's profile question takes as an answer today, and how it keeps it.
 *
 * <p>Nothing measures these answers yet: whatever arrives is kept as it was sent. Written down before
 * the field types are brought together, so the change that starts checking them shows exactly what
 * it turned away.
 */
class AssociationAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private int clusterId;
    private Station station;
    private int memberId;

    @BeforeEach
    void freshAssociation() {
        clusterId = clusterService
                .create("Verband Antworten " + NAMES.incrementAndGet(), null)
                .id();
        station = clusterService.createStation(clusterId, "Wache Antworten " + NAMES.incrementAndGet());
        var account =
                accountRepo.create("association-answers" + NAMES.incrementAndGet() + "@test.com", "Vera", "Verband");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @AfterEach
    void releaseStation() {
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    private String stored(ProfileFieldType type, String config, String answer) {
        var field = clusterProfileFieldService.create(
                clusterId,
                type.name() + " " + NAMES.incrementAndGet(),
                type,
                ProfileFieldConfig.parse(config),
                false,
                false,
                null,
                false,
                false,
                null);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), answer), memberId);
        return clusterProfileFieldService.findValues(clusterId, memberId).get(field.id());
    }

    @Test
    void aNumberKeepsTextThatIsNoNumber() {
        assertEquals("\"zwei\"", stored(ProfileFieldType.NUMBER, "{}", "\"zwei\""));
        assertEquals("2.5", stored(ProfileFieldType.NUMBER, "{}", "2.5"));
    }

    @Test
    void aDateKeepsADayWrittenTheGermanWay() {
        assertEquals("\"01.09.2011\"", stored(ProfileFieldType.DATE, "{}", "\"01.09.2011\""));
    }

    @Test
    void aChoiceKeepsAnAnswerItDoesNotOffer() {
        assertEquals("\"XL\"", stored(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "\"XL\""));
    }

    @Test
    void aYesOrNoKeepsAnyWord() {
        assertEquals("\"vielleicht\"", stored(ProfileFieldType.BOOLEAN, "{}", "\"vielleicht\""));
    }

    @Test
    void anAgeKeepsWhatIsWrittenUnderIt() {
        assertEquals("15", stored(ProfileFieldType.AGE, "{}", "15"));
    }
}

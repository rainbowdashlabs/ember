/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What an association's profile question takes as an answer, and how it keeps it.
 *
 * <p>Measured the way a station's own question is, whether the association writes the answer or the
 * station does, and kept in the same shape.
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

    private ClusterProfileField ask(ProfileFieldType type, String config) {
        return clusterProfileFieldService.create(
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
    }

    private String stored(ProfileFieldType type, String config, String answer) {
        var field = ask(type, config);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), answer), memberId);
        return clusterProfileFieldService.findValues(clusterId, memberId).get(field.id());
    }

    private void refused(ProfileFieldType type, String config, String answer) {
        var field = ask(type, config);
        assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), answer), memberId),
                answer + " under " + type);
    }

    @Test
    void aNumberIsWholeAndKeptAsANumber() {
        assertEquals("2", stored(ProfileFieldType.NUMBER, "{}", "\"2\""));
        refused(ProfileFieldType.NUMBER, "{}", "\"zwei\"");
        refused(ProfileFieldType.NUMBER, "{}", "2.5");
    }

    @Test
    void aDateTakesAnIsoDayOnly() {
        assertEquals("\"2011-09-01\"", stored(ProfileFieldType.DATE, "{}", "\"2011-09-01\""));
        refused(ProfileFieldType.DATE, "{}", "\"01.09.2011\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals("\"M\"", stored(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "\"M\""));
        refused(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "\"XL\"");
    }

    @Test
    void aYesOrNoKeepsABooleanAndRefusesAnyOtherWord() {
        assertEquals("true", stored(ProfileFieldType.BOOLEAN, "{}", "\"1\""));
        refused(ProfileFieldType.BOOLEAN, "{}", "\"vielleicht\"");
    }

    @Test
    void anAgeTakesNoAnswer() {
        var field = ask(ProfileFieldType.AGE, "{}");

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "15"), memberId));

        assertEquals(Refusal.PROFILE_AGE_TAKES_NO_ANSWER, refusal.refusal());
    }

    @Test
    void aQuestionCannotStartFromAnAnswerItRefuses() {
        assertThrows(
                BadRequestResponse.class,
                () -> ask(ProfileFieldType.ENUM, "{\"options\":[\"S\"],\"defaultValue\":\"XL\"}"));
    }

    @Test
    void nothingSaidIsNothingKept() {
        assertNull(stored(ProfileFieldType.TEXT, "{}", "\"\""));
    }

    /** The station writing an answer to its association's question is measured the same way. */
    @Test
    void theStationWritingAnAnswerIsMeasuredToo() {
        var field = ask(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}");

        assertThrows(
                BadRequestResponse.class,
                () -> profileFieldService.setValues(
                        memberId, List.of(new FieldValueEntry(field.id(), "\"XL\"", FieldOrigin.CLUSTER)), memberId));

        profileFieldService.setValues(
                memberId, List.of(new FieldValueEntry(field.id(), "\"M\"", FieldOrigin.CLUSTER)), memberId);
        assertEquals(
                "\"M\"",
                clusterProfileFieldService.findValues(clusterId, memberId).get(field.id()));
    }
}

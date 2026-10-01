/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
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

    private ClusterProfileField ask(FieldType type, String config) {
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

    private String stored(FieldType type, String config, String answer) {
        var field = ask(type, config);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), answer), memberId);
        return clusterProfileFieldService.findValues(clusterId, memberId).get(field.id());
    }

    private void refused(FieldType type, String config, String answer) {
        var field = ask(type, config);
        assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), answer), memberId),
                answer + " under " + type);
    }

    @Test
    void aNumberIsWholeAndKeptAsANumber() {
        assertEquals("2", stored(FieldType.NUMBER, "{}", "\"2\""));
        refused(FieldType.NUMBER, "{}", "\"zwei\"");
        refused(FieldType.NUMBER, "{}", "2.5");
    }

    @Test
    void aDateTakesAnIsoDayOnly() {
        assertEquals("\"2011-09-01\"", stored(FieldType.DATE, "{}", "\"2011-09-01\""));
        refused(FieldType.DATE, "{}", "\"01.09.2011\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals("\"M\"", stored(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "\"M\""));
        refused(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "\"XL\"");
    }

    @Test
    void aYesOrNoKeepsABooleanAndRefusesAnyOtherWord() {
        assertEquals("true", stored(FieldType.BOOLEAN, "{}", "\"1\""));
        refused(FieldType.BOOLEAN, "{}", "\"vielleicht\"");
    }

    @Test
    void anAgeTakesNoAnswer() {
        var field = ask(FieldType.AGE, "{}");

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "15"), memberId));

        assertEquals(MemberRefusal.PROFILE_AGE_TAKES_NO_ANSWER, refusal.refusal());
    }

    @Test
    void aQuestionCannotStartFromAnAnswerItRefuses() {
        assertThrows(
                RefusalResponse.class, () -> ask(FieldType.CHOICE, "{\"options\":[\"S\"],\"defaultValue\":\"XL\"}"));
    }

    @Test
    void nothingSaidIsNothingKept() {
        assertNull(stored(FieldType.TEXT, "{}", "\"\""));
    }

    /** The station writing an answer to its association's question is measured the same way. */
    @Test
    void theStationWritingAnAnswerIsMeasuredToo() {
        var field = ask(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}");
        clusterProfileFieldService.assignToRole(clusterId, field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        assertThrows(
                RefusalResponse.class,
                () -> profileFieldService.setValues(
                        memberId, List.of(new FieldValueEntry(field.id(), "\"XL\"", FieldOrigin.CLUSTER)), memberId));

        profileFieldService.setValues(
                memberId, List.of(new FieldValueEntry(field.id(), "\"M\"", FieldOrigin.CLUSTER)), memberId);
        assertEquals(
                "\"M\"",
                clusterProfileFieldService.findValues(clusterId, memberId).get(field.id()));
    }
}

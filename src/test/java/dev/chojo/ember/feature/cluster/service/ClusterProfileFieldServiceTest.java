/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The questions a cluster asks, what it may not ask, and what happens to the answers.
 */
class ClusterProfileFieldServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Fragen " + NAMES.incrementAndGet(), null)
                .id();
    }

    private Station stationOf(int clusterId) {
        return clusterService.createStation(clusterId, "Wache Fragen " + NAMES.incrementAndGet());
    }

    private int memberAt(Station station) {
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusterfield" + n + "@test.com", "Feld", "Wert" + n);
        return stationMemberRepo.create(station.id(), account.id()).id();
    }

    /** Twenty questions moved by one drag is one write, not twenty. */
    @Test
    void anOrderIsWrittenInOneGo() {
        int clusterId = freshCluster();
        var first = clusterProfileFieldService.create(
                clusterId,
                "Erste",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        var second = clusterProfileFieldService.create(
                clusterId,
                "Zweite",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                1,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        clusterProfileFieldService.reorder(clusterId, List.of(second.id(), first.id()));

        var ordered = clusterProfileFieldService.findByCluster(clusterId);
        assertEquals(second.id(), ordered.getFirst().id(), "the order given is the order stored");

        // Nothing to move is not an error, and writes nothing
        clusterProfileFieldService.reorder(clusterId, List.of());
        assertEquals(
                second.id(),
                clusterProfileFieldService.findByCluster(clusterId).getFirst().id());
    }

    @Test
    void aClustersQuestionsReachItsStations() {
        int clusterId = freshCluster();
        var station = stationOf(clusterId);

        clusterProfileFieldService.create(
                clusterId,
                "Führerscheinklasse",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        var reaching = clusterProfileFieldService.findForStation(station.id(), ProfileFieldScope.MEMBER);
        assertEquals(1, reaching.size());
        assertEquals("Führerscheinklasse", reaching.getFirst().name());
        assertTrue(reaching.getFirst().stationReadonly());

        // And they appear in the station's own profile beside its own fields, marked as somebody else's
        var merged = profileFieldService.findMergedFields(station.id(), ProfileFieldScope.MEMBER);
        assertTrue(merged.stream()
                .anyMatch(f -> f.origin() == FieldOrigin.CLUSTER && f.name().equals("Führerscheinklasse")));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aStationWithoutAClusterIsAskedNothing() {
        var station = stationRepo.create("Wache Frei " + NAMES.incrementAndGet());

        assertTrue(clusterProfileFieldService
                .findForStation(station.id(), ProfileFieldScope.MEMBER)
                .isEmpty());

        stationRepo.delete(station.id());
    }

    @Test
    void aClusterCannotAskAGroupScopedQuestion() {
        int clusterId = freshCluster();

        var refused = assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.create(
                        clusterId,
                        "Gruppenfrage",
                        ProfileFieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        0,
                        ProfileFieldScope.GROUP,
                        true,
                        false,
                        null));
        assertTrue(refused.getMessage().contains("cannot see"));
    }

    /**
     * A question pointed at a group reaches the stations filed under it and nobody else, which is the whole
     * of the feature: an association's stations do different work.
     */
    @Test
    void aTargetedQuestionReachesTheStationsInItsGroupAndNoOthers() {
        int clusterId = freshCluster();
        var inside = clusterService.createStation(clusterId, "Wache Innen " + NAMES.incrementAndGet());
        var outside = clusterService.createStation(clusterId, "Wache Aussen " + NAMES.incrementAndGet());
        var group = clusterStationGroupService.create(clusterId, "Atemschutz " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, group.id(), List.of(inside.uid()));

        clusterProfileFieldService.create(
                clusterId,
                "Atemschutztauglich",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                group.id());

        assertEquals(
                1,
                clusterProfileFieldService
                        .findForStation(inside.id(), ProfileFieldScope.MEMBER)
                        .size());
        assertTrue(
                clusterProfileFieldService
                        .findForStation(outside.id(), ProfileFieldScope.MEMBER)
                        .isEmpty(),
                "a station outside the filing is never asked");

        assertTrue(
                profileFieldService.findMergedFields(inside.id(), ProfileFieldScope.MEMBER).stream()
                        .anyMatch(f ->
                                f.origin() == FieldOrigin.CLUSTER && f.name().equals("Atemschutztauglich")),
                "and the union everybody reads carries it too");
        assertTrue(
                profileFieldService.findMergedFields(outside.id(), ProfileFieldScope.MEMBER).stream()
                        .noneMatch(f -> f.origin() == FieldOrigin.CLUSTER),
                "while the station outside sees nothing of it");

        clusterStationGroupService.setStations(clusterId, group.id(), List.of());
        assertTrue(clusterProfileFieldService
                .findForStation(inside.id(), ProfileFieldScope.MEMBER)
                .isEmpty());

        clusterService.releaseStation(clusterId, inside.id());
        clusterService.releaseStation(clusterId, outside.id());
        stationRepo.delete(inside.id());
        stationRepo.delete(outside.id());
        clusterService.delete(clusterId);
    }

    /**
     * Two questions of one name may never land on the same profile, and may both exist when they cannot meet.
     */
    @Test
    void twoQuestionsOfOneNameMayNotReachTheSameStation() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Doppelt " + NAMES.incrementAndGet());
        var reaching = clusterStationGroupService.create(clusterId, "Erreicht " + NAMES.incrementAndGet());
        var empty = clusterStationGroupService.create(clusterId, "Leer " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, reaching.id(), List.of(station.uid()));

        clusterProfileFieldService.create(
                clusterId,
                "Funkrufname",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        var refused = assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.create(
                        clusterId,
                        "Funkrufname",
                        ProfileFieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        1,
                        ProfileFieldScope.MEMBER,
                        true,
                        false,
                        reaching.id()));
        assertTrue(refused.getMessage().contains("already reaches"));

        clusterProfileFieldService.create(
                clusterId,
                "Funkrufname",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                1,
                ProfileFieldScope.MEMBER,
                true,
                false,
                empty.id());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
        clusterService.delete(clusterId);
    }

    @Test
    void aClusterCannotAskForADateOfBirth() {
        int clusterId = freshCluster();

        var refused = assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.create(
                        clusterId,
                        "Geburtstag",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.empty(),
                        0,
                        ProfileFieldScope.MEMBER,
                        true,
                        false,
                        null));
        assertTrue(refused.getMessage().contains("collide"));
    }

    @Test
    void aQuestionNeedsAName() {
        int clusterId = freshCluster();

        assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.create(
                        clusterId,
                        "  ",
                        ProfileFieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        0,
                        ProfileFieldScope.MEMBER,
                        true,
                        false,
                        null));
    }

    @Test
    void anAnswerIsWrittenAndRead() {
        int clusterId = freshCluster();
        var station = stationOf(clusterId);
        int memberId = memberAt(station);
        var field = clusterProfileFieldService.create(
                clusterId,
                "Atemschutz",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "true"), memberId);

        assertEquals(
                "true",
                clusterProfileFieldService.findValues(clusterId, memberId).get(field.id()));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    /**
     * An answer to a question the station has stopped being asked is shown nowhere and cannot be written,
     * but it is not thrown away: putting the station back into the group brings it back.
     */
    @Test
    void anAnswerWaitsOutTheTimeItsQuestionDoesNotReachTheStation() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Wartet " + NAMES.incrementAndGet());
        int memberId = memberAt(station);
        var group = clusterStationGroupService.create(clusterId, "Atemschutz " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, group.id(), List.of(station.uid()));
        var field = clusterProfileFieldService.create(
                clusterId,
                "Atemschutztauglich",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                group.id());
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "true"), memberId);

        clusterStationGroupService.setStations(clusterId, group.id(), List.of());

        assertTrue(
                clusterProfileFieldService.findValues(clusterId, memberId).isEmpty(),
                "an answer nobody is asked for any more is shown nowhere");
        assertThrows(
                BadRequestResponse.class,
                () -> clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "false"), memberId),
                "and nobody may write one either");

        clusterStationGroupService.setStations(clusterId, group.id(), List.of(station.uid()));
        assertEquals(
                "true",
                clusterProfileFieldService.findValues(clusterId, memberId).get(field.id()),
                "and it is there again when the station is");

        clusterProfileFieldService.delete(clusterId, field.id());
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
        clusterService.delete(clusterId);
    }

    @Test
    void releasingAStationTakesTheAnswersAndLeavesTheHistory() {
        int clusterId = freshCluster();
        var station = stationOf(clusterId);
        int memberId = memberAt(station);
        var field = clusterProfileFieldService.create(
                clusterId,
                "Atemschutz",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "true"), memberId);

        clusterService.releaseStation(clusterId, station.id());

        assertTrue(clusterProfileFieldRepo.findValues(memberId).isEmpty(), "the answers went with the membership");
        assertFalse(
                profileFieldChangeRepo.findByMember(memberId).isEmpty(),
                "the record of who changed what outlives the membership");

        stationRepo.delete(station.id());
    }

    @Test
    void oneClusterCannotAskAboutAnothersPeople() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var elsewhere = stationOf(otherClusterId);
        int memberId = memberAt(elsewhere);

        assertThrows(NotFoundResponse.class, () -> clusterProfileFieldService.findValues(clusterId, memberId));

        clusterService.releaseStation(otherClusterId, elsewhere.id());
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void aQuestionCanBeChangedAndRemoved() {
        int clusterId = freshCluster();
        var field = clusterProfileFieldService.create(
                clusterId,
                "Vorläufig",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        clusterProfileFieldService.update(
                clusterId,
                field.id(),
                "Endgültig",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                1,
                ProfileFieldScope.TEAM,
                false,
                true,
                null);

        var updated = clusterProfileFieldService.findByCluster(clusterId).getFirst();
        assertEquals("Endgültig", updated.name());
        assertEquals(ProfileFieldScope.TEAM, updated.scope());
        assertFalse(updated.stationReadonly());

        clusterProfileFieldService.delete(clusterId, field.id());
        assertTrue(clusterProfileFieldService.findByCluster(clusterId).isEmpty());
    }

    @Test
    void oneClusterCannotChangeAnothersQuestion() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var field = clusterProfileFieldService.create(
                otherClusterId,
                "Fremd",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        assertThrows(NotFoundResponse.class, () -> clusterProfileFieldService.delete(clusterId, field.id()));
    }

    @Test
    void oneAnswerCanBeClearedOnItsOwn() {
        int clusterId = freshCluster();
        var station = stationOf(clusterId);
        int memberId = memberAt(station);
        var field = clusterProfileFieldService.create(
                clusterId,
                "Einzeln",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "\"da\""), memberId);

        assertTrue(clusterProfileFieldRepo.deleteValue(memberId, field.id()));
        assertFalse(clusterProfileFieldRepo.deleteValue(memberId, field.id()), "clearing twice changes nothing");
        assertTrue(clusterProfileFieldRepo.findValues(memberId).isEmpty());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aQuestionIsFoundByItsOwnId() {
        int clusterId = freshCluster();
        var field = clusterProfileFieldService.create(
                clusterId,
                "Nachschlagen",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);

        assertEquals(
                "Nachschlagen",
                clusterProfileFieldRepo.findById(field.id()).orElseThrow().name());
        assertTrue(clusterProfileFieldRepo.findById(999_999).isEmpty());
    }

    @Test
    void writingTheSameAnswerAgainRecordsNothing() {
        int clusterId = freshCluster();
        var station = stationOf(clusterId);
        int memberId = memberAt(station);
        var field = clusterProfileFieldService.create(
                clusterId,
                "Unverändert",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "\"gleich\""), memberId);
        int after = profileFieldChangeRepo.findByMember(memberId).size();

        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "\"gleich\""), memberId);

        assertEquals(after, profileFieldChangeRepo.findByMember(memberId).size());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }
}

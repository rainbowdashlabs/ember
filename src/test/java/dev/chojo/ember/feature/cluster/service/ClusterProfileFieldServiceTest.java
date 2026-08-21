/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

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
                false);

        var reaching = clusterProfileFieldService.findForStation(station.id(), ProfileFieldScope.MEMBER);
        assertEquals(1, reaching.size());
        assertEquals("Führerscheinklasse", reaching.getFirst().name());
        assertTrue(reaching.getFirst().stationReadonly());

        // And they appear in the station's own profile beside its own fields, marked as somebody else's
        var merged = profileFieldService.findMergedFields(station.id(), ProfileFieldScope.MEMBER);
        assertTrue(merged.stream()
                .anyMatch(f -> f.origin() == ProfileFieldService.FieldOrigin.CLUSTER
                        && f.name().equals("Führerscheinklasse")));

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
                        false));
        assertTrue(refused.getMessage().contains("cannot see"));
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
                        false));
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
                        false));
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
                false);

        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "true"), memberId);

        assertEquals(
                "true",
                clusterProfileFieldService.findValues(clusterId, memberId).get(field.id()));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
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
                false);
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
                false);

        clusterProfileFieldService.update(
                clusterId,
                field.id(),
                "Endgültig",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                1,
                ProfileFieldScope.TEAM,
                false,
                true);

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
                false);

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
                false);
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
                false);

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
                false);
        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "\"gleich\""), memberId);
        int after = profileFieldChangeRepo.findByMember(memberId).size();

        clusterProfileFieldService.setValues(clusterId, memberId, Map.of(field.id(), "\"gleich\""), memberId);

        assertEquals(after, profileFieldChangeRepo.findByMember(memberId).size());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }
}

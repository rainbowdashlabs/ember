/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The locks an association's question carries against the station that writes its answers.
 *
 * <p>The station writes such an answer the way it writes one to its own question: only where the
 * question is put to the member, never where the association keeps it to itself, and where the
 * question is read only for the member, only as the member management.
 */
class ProfileAnswerLocksTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private int clusterId;
    private Station station;
    private int memberId;

    @BeforeEach
    void freshAssociation() {
        clusterId = clusterService
                .create("Verband Sperren " + NAMES.incrementAndGet(), null)
                .id();
        station = clusterService.createStation(clusterId, "Wache Sperren " + NAMES.incrementAndGet());
        var account = accountRepo.create("answer-locks" + NAMES.incrementAndGet() + "@test.com", "Lea", "Sperre");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @AfterEach
    void releaseStation() {
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    private ClusterProfileField asked(int cluster, boolean readonly, boolean stationReadonly, @Nullable Integer group) {
        var field = clusterProfileFieldService.create(
                cluster,
                "Frage " + NAMES.incrementAndGet(),
                FieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                readonly,
                null,
                stationReadonly,
                false,
                group);
        clusterProfileFieldService.assignToRole(cluster, field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        return field;
    }

    private @Nullable String written(ClusterProfileField field, ProfileWriter writer) {
        profileFieldService.setValues(
                memberId, List.of(new FieldValueEntry(field.id(), "\"Ja\"", FieldOrigin.CLUSTER)), memberId, writer);
        return clusterProfileFieldRepo.findValues(memberId).stream()
                .filter(value -> value.fieldId() == field.id())
                .map(ProfileFieldValue::value)
                .findFirst()
                .orElse(null);
    }

    @Test
    void aReadonlyQuestionIsWrittenByTheMemberManagementOnly() {
        var field = asked(clusterId, true, false, null);

        assertNull(written(field, ProfileWriter.station(false)));
        assertEquals("\"Ja\"", written(field, ProfileWriter.station(true)));
    }

    @Test
    void anOverrideOnTheMembersRoleOpensAReadonlyQuestion() {
        var field = asked(clusterId, true, false, null);
        clusterProfileFieldService.assignToRole(clusterId, field.id(), ProfileFieldScope.MEMBER, 0, null, false, null);

        assertEquals("\"Ja\"", written(field, ProfileWriter.station(false)));
    }

    @Test
    void whatTheAssociationKeepsToItselfIsNotWrittenByTheStation() {
        var field = asked(clusterId, false, true, null);

        assertNull(written(field, ProfileWriter.station(true)));
        assertEquals("\"Ja\"", written(field, ProfileWriter.association()));
    }

    @Test
    void aQuestionNotPutToTheMembersRoleIsNotWritten() {
        var field = asked(clusterId, false, false, null);
        clusterProfileFieldService.unassignRole(clusterId, field.id(), ProfileFieldScope.MEMBER);
        clusterProfileFieldService.assignToRole(clusterId, field.id(), ProfileFieldScope.TEAM, 0, null, null, null);

        assertNull(written(field, ProfileWriter.station(true)));
    }

    @Test
    void aQuestionOfAnotherAssociationIsRefused() {
        int otherCluster = clusterService
                .create("Verband Fremd " + NAMES.incrementAndGet(), null)
                .id();
        var field = asked(otherCluster, false, false, null);

        var refusal = assertThrows(RefusalResponse.class, () -> written(field, ProfileWriter.station(true)));

        assertEquals(MemberRefusal.PROFILE_ASSOCIATION_FIELD_NOT_HERE_ON_ANSWER, refusal.refusal());
    }

    @Test
    void aQuestionForOtherStationsOfTheAssociationIsRefused() {
        var group = clusterStationGroupService.create(clusterId, "Andere Wachen " + NAMES.incrementAndGet());
        var field = asked(clusterId, false, false, group.id());

        var refusal = assertThrows(RefusalResponse.class, () -> written(field, ProfileWriter.station(true)));

        assertEquals(MemberRefusal.PROFILE_ASSOCIATION_FIELD_NOT_HERE_ON_ANSWER, refusal.refusal());
    }
}

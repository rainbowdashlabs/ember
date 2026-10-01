/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a profile is complete, as the member list reads it for every row and the reminder reads it for
 * one member. Both ask the same statement, so every case is asked both ways and the two have to agree.
 */
class ProfileCompletenessSqlTest extends RepositoryTestBase {

    private Station station;
    private Account account;
    private StationMember member;
    private Cluster cluster;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Completeness Station");
        account = accountRepo.create("completeness-" + station.id() + "@test.com", "Clara", "Voll");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterEach
    void cleanup() {
        if (cluster != null) {
            stationRepo.setCluster(station.id(), null);
            clusterRepo.delete(cluster.id());
        }
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** What the member list says about the member, and what the single lookup says; they have to agree. */
    private boolean complete() {
        boolean listed = stationMemberRepo.findRichMembers(station.id(), false).stream()
                .filter(row -> row.id() == member.id())
                .map(RichMember::profileComplete)
                .findFirst()
                .orElseThrow();
        assertEquals(listed, profileFieldRepo.isProfileComplete(member.id()), "the list and the lookup agree");
        return listed;
    }

    private int requiredOf(ProfileFieldScope role) {
        var field = profileFieldRepo.create(
                station.id(), "Pflicht " + role, FieldType.TEXT, ProfileFieldConfig.parse("{}"), true, false, null);
        profileFieldRepo.assignToRole(field.id(), role, 0, null, null, null);
        return field.id();
    }

    @Test
    void aMemberAskedNothingIsComplete() {
        assertTrue(complete());
    }

    @Test
    void aRequiredQuestionLeftOpenMakesTheProfileIncomplete() {
        int fieldId = requiredOf(ProfileFieldScope.MEMBER);

        assertFalse(complete());

        profileFieldRepo.setValue(member.id(), fieldId, StringNode.valueOf(""));
        assertFalse(complete(), "an empty answer is no answer");

        profileFieldRepo.setValue(member.id(), fieldId, StringNode.valueOf("Antwort"));
        assertTrue(complete());
    }

    /** An age counts itself from a date and takes no answer, so even a required one is never left open. */
    @Test
    void aRequiredAgeIsNeverLeftOpen() {
        var age = profileFieldRepo.create(
                station.id(), "Alter", FieldType.AGE, ProfileFieldConfig.parse("{}"), true, false, null);
        profileFieldRepo.assignToRole(age.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        assertTrue(complete());
    }

    @Test
    void aRequiredQuestionPutToAnotherKindOfMemberDoesNotCount() {
        requiredOf(ProfileFieldScope.TEAM);

        assertTrue(complete(), "the question is the team's, not theirs");
    }

    @Test
    void aRequiredQuestionOfTheAssociationCounts() {
        cluster = clusterRepo.create("Completeness Verband " + station.id(), null, station.id());
        stationRepo.setCluster(station.id(), cluster.id());
        var field = clusterProfileFieldRepo.create(
                cluster.id(),
                "Verbandsnummer",
                FieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                true,
                false,
                null,
                false,
                false,
                null);
        clusterProfileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        assertFalse(complete());

        clusterProfileFieldRepo.setValue(member.id(), field.id(), StringNode.valueOf("V-17"));
        assertTrue(complete());
    }

    @Test
    void aMemberThatDoesNotExistIsNotAskedAnything() {
        assertTrue(profileFieldRepo.isProfileComplete(-1));
    }
}

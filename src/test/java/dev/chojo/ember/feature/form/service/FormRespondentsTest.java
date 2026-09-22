/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is known about the members behind a form's results, read from the station's own data.
 */
class FormRespondentsTest extends RepositoryTestBase {
    private static FormRespondents respondents;
    private static Station station;
    private static Account youngAccount;
    private static Account unknownAccount;
    private static StationMember young;
    private static StationMember unknownAge;
    private static int groupId;
    private static int tagId;
    private static int shirtFieldId;

    @BeforeAll
    static void setup() {
        respondents =
                new FormRespondents(stationMemberRepo, memberGroupRepo, userTagRepo, profileFieldRepo, stationRepo);
        station = stationRepo.create("FormRespondentsStation");
        youngAccount = accountRepo.create("respondent-young@test.com", "Yara", "Young");
        unknownAccount = accountRepo.create("respondent-unknown@test.com", "Uwe", "Unknown");
        young = stationMemberRepo.create(station.id(), youngAccount.id());
        unknownAge = stationMemberRepo.create(station.id(), unknownAccount.id());
        stationMemberRepo.setUserType(young.id(), StationUserType.TRIAL);

        groupId = memberGroupRepo.create(station.id(), "Jugend").id();
        memberGroupRepo.addMember(groupId, young.id());
        tagId = userTagRepo.create(station.id(), "Maschinist").id();
        userTagRepo.addMember(tagId, young.id());

        var shirt = profileFieldRepo.create(
                station.id(),
                "Größe",
                ProfileFieldType.ENUM,
                ProfileFieldConfig.parse("{\"options\":[\"S\",\"M\"]}"),
                false,
                false,
                null);
        shirtFieldId = shirt.id();
        profileFieldRepo.setValue(young.id(), shirtFieldId, StringNode.valueOf("M"));
        profileFieldRepo.setValue(unknownAge.id(), shirtFieldId, StringNode.valueOf(" "));

        var born = profileFieldRepo.create(
                station.id(),
                "Geburtstag",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.setValue(young.id(), born.id(), StringNode.valueOf("2012-06-15"));
        profileFieldRepo.setValue(unknownAge.id(), born.id(), StringNode.valueOf("kein Datum"));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(youngAccount.id());
        accountRepo.delete(unknownAccount.id());
    }

    @Test
    void aResponseCarriesItsMembersTypeGroupsTagsAnswersAndAgeOnTheDay() {
        var answered = Instant.parse("2026-06-14T10:00:00Z");
        var described = respondents
                .ofResponses(station.id(), List.of(response(1, young.id(), answered)))
                .getFirst();

        assertEquals(StationUserType.TRIAL, described.userType());
        assertEquals(Set.of(groupId), described.groupIds());
        assertEquals(Set.of(tagId), described.tagIds());
        assertEquals(Map.of(shirtFieldId, "M"), described.fieldValues());
        assertEquals(13, described.age(), "a day before the fourteenth birthday");
    }

    @Test
    void aResponseWithoutAMemberIsNobody() {
        var anonymous = respondents
                .ofResponses(station.id(), List.of(response(2, null, Instant.now())))
                .getFirst();

        assertNull(anonymous.userType());
        assertTrue(anonymous.groupIds().isEmpty());
        assertNull(anonymous.age());
    }

    @Test
    void aMemberWhoHasNotAnsweredIsCountedToToday() {
        var described = respondents.ofMembers(station.id(), List.of(young.id(), unknownAge.id(), 999_999));

        assertEquals(2, described.size(), "a member of no station is not described");
        var today = LocalDate.now(ZoneOffset.UTC);
        int expected = Period.between(LocalDate.of(2012, 6, 15), today).getYears();
        assertTrue(Math.abs(described.getFirst().age() - expected) <= 1);
        assertNull(described.get(1).age(), "an unreadable date of birth is an unknown age");
        assertTrue(described.get(1).fieldValues().isEmpty(), "a blank answer is no answer");
    }

    @Test
    void nobodyToDescribeReadsNothing() {
        assertTrue(respondents.ofMembers(station.id(), List.of()).isEmpty());
    }

    private static FormResponse response(int id, Integer memberId, Instant submittedAt) {
        return new FormResponse(id, 1, memberId, memberId, submittedAt, submittedAt, null, null, null);
    }
}

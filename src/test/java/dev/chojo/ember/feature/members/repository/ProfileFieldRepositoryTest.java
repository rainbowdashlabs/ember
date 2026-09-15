/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.node.StringNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileFieldRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fieldId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Profile Station");
        account = accountRepo.create("profile@test.com", "Profile", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * The order of one audience's form is written in one statement, and only for the station that
     * owns it. The order belongs to the assignment, so ordering the managers' form leaves every
     * other form alone.
     */
    @Test
    @Order(0)
    void anOrderIsWrittenInOneStatementAndStaysWithinTheStation() {
        var other = stationRepo.create("Profile Station Nebenan");
        var first = askOf(station.id(), "Erst", ProfileFieldScope.MANAGER, 0);
        var second = askOf(station.id(), "Zweit", ProfileFieldScope.MANAGER, 1);
        var elsewhere = askOf(other.id(), "Fremd", ProfileFieldScope.MANAGER, 0);

        assertEquals(
                0,
                profileFieldRepo.applyOrder(station.id(), ProfileFieldScope.MANAGER, List.of()),
                "nothing to move moves nothing");

        int moved = profileFieldRepo.applyOrder(
                station.id(), ProfileFieldScope.MANAGER, List.of(second.id(), first.id(), elsewhere.id()));
        assertEquals(2, moved, "a station cannot reorder another station's fields");

        var ordered = profileFieldRepo.findByStationAndScope(station.id(), ProfileFieldScope.MANAGER);
        assertEquals(second.id(), ordered.getFirst().field().id());
        assertEquals(first.id(), ordered.get(1).field().id());
        assertEquals(
                0, profileFieldRepo.findAssignments(elsewhere.id()).getFirst().position());

        profileFieldRepo.delete(first.id());
        profileFieldRepo.delete(second.id());
        profileFieldRepo.delete(elsewhere.id());
        stationRepo.delete(other.id());
    }

    /**
     * A question put to two audiences is one definition, so the lookup by type answers with one.
     *
     * <p>This used to answer with several, because the date of birth was written once per kind of
     * member. That is what made a manager, who is asked the team's questions as well as their own,
     * meet it twice.
     */
    @Test
    @Order(0)
    void aQuestionAskedOfTwoAudiencesIsStillOneField() {
        var birthDate = profileFieldRepo.create(
                station.id(),
                "Geburtstag",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(birthDate.id(), ProfileFieldScope.TEAM, 0, null, null, null);
        profileFieldRepo.assignToRole(birthDate.id(), ProfileFieldScope.GUARDIAN, 0, null, null, null);

        var found = profileFieldRepo.findAllByStationAndType(station.id(), ProfileFieldType.BIRTH_DATE);
        assertEquals(1, found.size(), "one question, however many audiences are asked it");
        assertEquals(2, profileFieldRepo.findAssignments(birthDate.id()).size());

        profileFieldRepo.delete(birthDate.id());
    }

    /** Dropping an audience leaves the question and every other audience alone. */
    @Test
    @Order(0)
    void unassigningOneAudienceKeepsTheQuestion() {
        var field = askOf(station.id(), "Schuhgröße", ProfileFieldScope.MEMBER, 0);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.TEAM, 1, null, null, null);

        assertTrue(profileFieldRepo.unassignRole(field.id(), ProfileFieldScope.TEAM));

        assertTrue(profileFieldRepo.findById(field.id()).isPresent(), "the question stays");
        assertEquals(1, profileFieldRepo.findAssignments(field.id()).size());
        assertTrue(profileFieldRepo
                .findByStationAndScope(station.id(), ProfileFieldScope.TEAM)
                .isEmpty());

        profileFieldRepo.delete(field.id());
    }

    /**
     * A group is an audience of its own, and dropping it leaves the question where it stands.
     *
     * <p>The group used to live inside the question's settings, so a question that lost it belonged
     * nowhere and was shown nowhere. It is a row beside the roles now, removed the same way.
     */
    @Test
    @Order(0)
    void aGroupIsAnAudienceOfItsOwnAndCanBeDropped() {
        var group = memberGroupRepo.create(station.id(), "Fahrer " + station.id());
        var field = askOf(station.id(), "Führerscheinklasse", ProfileFieldScope.MEMBER, 0);
        profileFieldRepo.assignToGroup(field.id(), group.id(), 1, null, null, null);

        assertEquals(
                1,
                profileFieldRepo
                        .findByStationAndGroups(station.id(), List.of(group.id()))
                        .size());

        assertTrue(profileFieldRepo.unassignGroup(field.id(), group.id()));
        assertFalse(
                profileFieldRepo.unassignGroup(field.id(), group.id()),
                "a group that is no longer asked is not dropped twice");

        assertTrue(profileFieldRepo.findById(field.id()).isPresent(), "the question stays");
        assertTrue(profileFieldRepo
                .findByStationAndGroups(station.id(), List.of(group.id()))
                .isEmpty());

        profileFieldRepo.delete(field.id());
        memberGroupRepo.delete(group.id());
    }

    /**
     * Every assignment of a station's questions, which is what the editor builds each form from, and
     * which stops at the station that owns them.
     */
    @Test
    @Order(0)
    void theAssignmentsOfAStationAreItsOwn() {
        var other = stationRepo.create("Profile Station Daneben");
        var mine = askOf(station.id(), "Konfektionsgröße", ProfileFieldScope.MEMBER, 0);
        profileFieldRepo.assignToRole(mine.id(), ProfileFieldScope.TEAM, 1, null, null, null);
        var elsewhere = askOf(other.id(), "Fremd", ProfileFieldScope.MEMBER, 0);

        var assignments = profileFieldRepo.findAssignmentsByStation(station.id());

        assertEquals(2, assignments.size(), "both audiences of the one question");
        assertTrue(assignments.stream().allMatch(a -> a.fieldId() == mine.id()), "and nothing of another station's");

        profileFieldRepo.delete(mine.id());
        profileFieldRepo.delete(elsewhere.id());
        stationRepo.delete(other.id());
    }

    /** An assignment may demand an answer the definition does not, and only of its own audience. */
    @Test
    @Order(0)
    void anAssignmentMayOverrideWhetherAnAnswerIsExpected() {
        var field = profileFieldRepo.create(
                station.id(), "Allergien", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.GUARDIAN, 0, null, null, true);

        var forMembers = profileFieldRepo
                .findByStationAndScope(station.id(), ProfileFieldScope.MEMBER)
                .getFirst();
        var forGuardians = profileFieldRepo
                .findByStationAndScope(station.id(), ProfileFieldScope.GUARDIAN)
                .getFirst();

        assertFalse(forMembers.required(), "the definition does not expect one");
        assertTrue(forGuardians.required(), "and this audience does");

        profileFieldRepo.delete(field.id());
    }

    @Test
    @Order(1)
    void create() {
        ProfileField field = profileFieldRepo.create(
                station.id(), "Phone", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 1, null, null, null);
        assertNotNull(field);
        assertEquals("Phone", field.name());
        assertFalse(field.required());
        fieldId = field.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(profileFieldRepo.findById(fieldId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, profileFieldRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void findByStationAndScope() {
        assertEquals(
                1,
                profileFieldRepo
                        .findByStationAndScope(station.id(), ProfileFieldScope.MEMBER)
                        .size());
        assertTrue(profileFieldRepo
                .findByStationAndScope(station.id(), ProfileFieldScope.TEAM)
                .isEmpty());
    }

    @Test
    @Order(5)
    void update() {
        assertTrue(profileFieldRepo.update(
                fieldId, "Email", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), true, false, null, false));
        ProfileField updated = profileFieldRepo.findById(fieldId).orElseThrow();
        assertEquals("Email", updated.name());
        assertTrue(updated.required());
    }

    private static ProfileField askOf(int stationId, String name, ProfileFieldScope role, int position) {
        var field = profileFieldRepo.create(
                stationId, name, ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(field.id(), role, position, null, null, null);
        return field;
    }

    // -- Values --

    @Test
    @Order(10)
    void setAndFindValue() {
        profileFieldRepo.setValue(member.id(), fieldId, StringNode.valueOf("test@test.com"));
        var values = profileFieldRepo.findValues(member.id());
        assertEquals(1, values.size());
        assertEquals("\"test@test.com\"", values.getFirst().value());
    }

    @Test
    @Order(11)
    void findValue() {
        assertTrue(profileFieldRepo.findValue(member.id(), fieldId).isPresent());
        assertTrue(profileFieldRepo.findValue(member.id(), 99999).isEmpty());
    }

    @Test
    @Order(12)
    void upsertValue() {
        profileFieldRepo.setValue(member.id(), fieldId, StringNode.valueOf("updated@test.com"));
        assertEquals(
                "\"updated@test.com\"",
                profileFieldRepo.findValue(member.id(), fieldId).orElseThrow().value());
    }

    @Test
    @Order(13)
    void deleteValue() {
        assertTrue(profileFieldRepo.deleteValue(member.id(), fieldId));
        assertTrue(profileFieldRepo.findValue(member.id(), fieldId).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(profileFieldRepo.delete(fieldId));
        assertTrue(profileFieldRepo.findById(fieldId).isEmpty());
    }
}

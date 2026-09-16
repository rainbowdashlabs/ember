/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldTarget;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileFieldServiceTest extends RepositoryTestBase {
    private static ProfileFieldService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fieldId;

    @BeforeAll
    static void setup() {
        service = new ProfileFieldService(
                profileFieldRepo,
                profileFieldChangeRepo,
                mock(NotificationService.class),
                stationMemberRepo,
                accountRepo,
                clusterProfileFieldRepo,
                memberGroupRepo,
                memberPermissionResolver);
        station = stationRepo.create("ProfileField Station");
        account = accountRepo.create("pfield-svc@test.com", "Profile", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * Writes a question down and puts it to one audience, which is what every test here wants.
     *
     * @return the definition, so a test can put it to somebody else as well
     */
    private static ProfileField ask(String name, ProfileFieldType type, String config, ProfileFieldScope role, int at) {
        return ask(name, type, config, role, at, false, false);
    }

    /**
     * The same, where an answer is expected or the audience may only read it.
     *
     * @param required whether the question expects an answer of everybody asked
     * @param readonly whether this audience may read the answer but not write it
     */
    private static ProfileField ask(
            String name,
            ProfileFieldType type,
            String config,
            ProfileFieldScope role,
            int at,
            boolean required,
            boolean readonly) {
        var field =
                service.create(station.id(), name, type, ProfileFieldConfig.parse(config), required, readonly, null);
        service.assignToRole(field.id(), role, at, null, null, null);
        return field;
    }

    @Test
    @Order(1)
    void create() {
        var field = ask("Phone", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 1);
        assertNotNull(field);
        assertEquals("Phone", field.name());
        assertEquals(
                ProfileFieldScope.MEMBER,
                service.findAssignments(field.id()).getFirst().role());
        fieldId = field.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(fieldId).isPresent());
        assertTrue(service.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var fields = service.findByStation(station.id());
        assertFalse(fields.isEmpty());
        assertTrue(fields.stream().anyMatch(f -> f.id() == fieldId));
    }

    @Test
    @Order(4)
    void findByStationAndScope() {
        var memberFields = service.findByStationAndScope(station.id(), ProfileFieldScope.MEMBER);
        assertFalse(memberFields.isEmpty());
        var teamFields = service.findByStationAndScope(station.id(), ProfileFieldScope.TEAM);
        assertTrue(teamFields.isEmpty());
    }

    /**
     * Stays optional on purpose: this field outlives the test, and a required one nobody answers
     * would make every later profile incomplete.
     */
    @Test
    @Order(5)
    void update() {
        var updated = service.update(
                fieldId, "Mobile", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null, false);
        assertTrue(updated.isPresent());
        assertEquals("Mobile", updated.get().name());
        assertFalse(updated.get().required());
    }

    @Test
    @Order(6)
    void updateNonExistent() {
        var result = service.update(
                99999, "X", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null, false);
        assertTrue(result.isEmpty());
    }

    // -- Values --

    @Test
    @Order(10)
    void findValuesEmpty() {
        var values = service.findValues(member.id());
        assertTrue(values.isEmpty());
    }

    @Test
    @Order(11)
    void setValuesAndFind() {
        var entries = List.of(new FieldValueEntry(fieldId, "\"555-1234\""));
        var result = service.setValues(member.id(), entries, member.id());
        assertFalse(result.isEmpty());
        assertEquals("\"555-1234\"", result.getFirst().value());
    }

    @Test
    @Order(12)
    void setValuesTwiceRecordsChange() {
        // Second call with a different value should record a change
        var entries = List.of(new FieldValueEntry(fieldId, "\"555-9999\""));
        var result = service.setValues(member.id(), entries, member.id());
        assertFalse(result.isEmpty());
        assertEquals("\"555-9999\"", result.getFirst().value());
    }

    @Test
    @Order(13)
    void findChanges() {
        var changes = service.findChanges(member.id());
        // At least one change was recorded (initial set)
        assertFalse(changes.isEmpty());
    }

    @Test
    @Order(14)
    void findChangesByStation() {
        var paged = service.findChangesByStation(station.id(), 10, 0);
        assertNotNull(paged);
        assertTrue(paged.total() > 0);
    }

    @Test
    @Order(15)
    void findChangesByStationEmpty() {
        var paged = service.findChangesByStation(station.id(), 10, 9999);
        assertNotNull(paged);
        assertTrue(paged.changes().isEmpty());
    }

    @Test
    @Order(16)
    void acknowledge() {
        var changes = service.findChanges(member.id());
        if (!changes.isEmpty()) {
            var ack = service.acknowledge(changes.getFirst().id(), member.id(), "OK");
            assertNotNull(ack);
            assertEquals(member.id(), ack.acknowledgedBy());
        }
    }

    @Test
    @Order(17)
    void acknowledgeAll() {
        // Set a value again to create a new unacknowledged change
        var entries = List.of(new FieldValueEntry(fieldId, "\"555-0000\""));
        service.setValues(member.id(), entries, member.id());

        var acks = service.acknowledgeAll(member.id(), member.id(), "Bulk ack");
        assertNotNull(acks);
    }

    @Test
    @Order(18)
    void findUnacknowledgedSummary() {
        var summary = service.findUnacknowledgedSummary(station.id(), member.id());
        assertNotNull(summary);
    }

    // -- Profile completeness --

    @Test
    @Order(21)
    void isProfileCompleteWithNothingRequired() {
        // The member's one field is not required, so nothing is outstanding
        assertTrue(service.isProfileComplete(member.id()));
    }

    @Test
    @Order(22)
    void isProfileCompleteRequiredFieldMissing() {
        // Create a required field with no value for a new member
        var reqField = ask("Required Field", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 10, true, false);
        var account2 = accountRepo.create("pfield-empty@test.com", "Empty", "Member");
        var member2 = stationMemberRepo.create(station.id(), account2.id());

        assertFalse(service.isProfileComplete(member2.id()));

        // Cleanup
        service.delete(reqField.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
    }

    /**
     * A selection left on its blank entry is an answer that says nothing, and it reaches the column
     * as a document null rather than as an empty column. Read back it is the four letters
     * {@code null}, which is neither empty nor the empty string, so a profile could be called
     * complete on the strength of an answer nobody gave.
     */
    @Test
    @Order(22)
    void aSelectionLeftEmptyIsNotAnAnswer() {
        var chooser = ask(
                "Chosen thing",
                ProfileFieldType.ENUM,
                "{\"options\":[\"A\",\"B\"]}",
                ProfileFieldScope.MEMBER,
                11,
                true,
                false);
        var account3 = accountRepo.create("pfield-blank@test.com", "Blank", "Chooser");
        var member3 = stationMemberRepo.create(station.id(), account3.id());

        for (String saidNothing : List.of("null", "\"\"")) {
            service.setValues(
                    member3.id(),
                    List.of(new FieldValueEntry(chooser.id(), saidNothing, FieldOrigin.STATION)),
                    member3.id());
            assertFalse(service.isProfileComplete(member3.id()), "answered with " + saidNothing);
        }

        service.setValues(
                member3.id(), List.of(new FieldValueEntry(chooser.id(), "\"A\"", FieldOrigin.STATION)), member3.id());
        assertTrue(service.isProfileComplete(member3.id()));

        service.delete(chooser.id());
        stationMemberRepo.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    // -- findApplicableFields / scopeForUserType --

    @Test
    @Order(23)
    void findApplicableFieldsForMember() {
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
        var fields = service.findApplicableFields(member.id());
        assertNotNull(fields);
        // Should return MEMBER-scope fields since member has MEMBER user type
    }

    /**
     * A station can ask something of one group alone: whoever drives is asked for a licence class,
     * and nobody else is asked at all. Such a field was declared, stored and listed in the
     * configuration screen, and then reached nobody, because only the member's kind was read.
     */
    @Test
    @Order(23)
    void aFieldAskedOfAGroupReachesTheMembersOfThatGroup() {
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
        var drivers = memberGroupRepo.create(station.id(), "Fahrer " + member.id());
        var others = memberGroupRepo.create(station.id(), "Andere " + member.id());
        var forDrivers = service.create(
                station.id(),
                "Führerscheinklasse",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.empty(),
                false,
                false,
                null);
        service.assignToGroup(forDrivers.id(), drivers.id(), 30, null, null, null);
        var forOthers = service.create(
                station.id(), "Etwas anderes", ProfileFieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);
        service.assignToGroup(forOthers.id(), others.id(), 31, null, null, null);

        assertTrue(
                service.findApplicableFields(member.id()).stream().noneMatch(f -> f.id() == forDrivers.id()),
                "outside the group it is asked of nobody");

        memberGroupRepo.addMember(drivers.id(), member.id());
        var asked = service.findApplicableFields(member.id());

        assertTrue(asked.stream().anyMatch(f -> f.id() == forDrivers.id()), "in the group it is asked");
        assertTrue(
                asked.stream().noneMatch(f -> f.id() == forOthers.id()), "and another group's question still is not");

        service.delete(forDrivers.id());
        service.delete(forOthers.id());
        memberGroupRepo.delete(drivers.id());
        memberGroupRepo.delete(others.id());
    }

    @Test
    @Order(23)
    void findApplicableFieldsForGuardian() {
        stationMemberRepo.setUserType(member.id(), StationUserType.GUARDIAN);
        var guardianField = ask("GuardianField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.GUARDIAN, 20);
        var fields = service.findApplicableFields(member.id());
        assertTrue(fields.stream().anyMatch(f -> f.id() == guardianField.id()));
        service.delete(guardianField.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(23)
    void findApplicableFieldsForTeam() {
        stationMemberRepo.setUserType(member.id(), StationUserType.TEAM);
        var teamField = ask("TeamField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.TEAM, 20);
        var fields = service.findApplicableFields(member.id());
        assertTrue(fields.stream().anyMatch(f -> f.id() == teamField.id()));
        service.delete(teamField.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    /**
     * A manager is asked what a manager is assigned, and a team question only where it says so.
     *
     * <p>Managers used to be handed the team's scope as well as their own, which is how the same
     * question reached them twice. A station that wants both now says so on the question itself.
     */
    @Test
    @Order(23)
    void findApplicableFieldsForManager() {
        stationMemberRepo.setUserType(member.id(), StationUserType.MANAGER);
        var mgrField = ask("ManagerField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MANAGER, 20);
        var teamOnly = ask("TeamOnlyField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.TEAM, 21);
        var both = ask("AskedOfBoth", ProfileFieldType.TEXT, "{}", ProfileFieldScope.TEAM, 22);
        service.assignToRole(both.id(), ProfileFieldScope.MANAGER, 22, null, null, null);

        var fields = service.findApplicableFields(member.id());

        assertTrue(fields.stream().anyMatch(f -> f.id() == mgrField.id()));
        assertTrue(
                fields.stream().noneMatch(f -> f.id() == teamOnly.id()),
                "a question put to the team alone is not put to a manager behind the station's back");
        assertEquals(
                1,
                fields.stream().filter(f -> f.id() == both.id()).count(),
                "a question put to both is asked once, not once per audience");

        service.delete(mgrField.id());
        service.delete(teamOnly.id());
        service.delete(both.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    /** A trial member is asked what trials are assigned, which is its own decision to make. */
    @Test
    @Order(23)
    void findApplicableFieldsForTrial() {
        stationMemberRepo.setUserType(member.id(), StationUserType.TRIAL);
        var forTrials = ask("TrialField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.TRIAL, 24);
        var forMembers = ask("MemberOnlyField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 25);

        var fields = service.findApplicableFields(member.id());

        assertTrue(fields.stream().anyMatch(f -> f.id() == forTrials.id()));
        assertTrue(
                fields.stream().noneMatch(f -> f.id() == forMembers.id()),
                "a trial member is no longer quietly shown the member's form");

        service.delete(forTrials.id());
        service.delete(forMembers.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    /**
     * A question put to a member's role and to one of their groups is still one question.
     *
     * <p>The two reads are separate, so without a check the member would be asked it twice on the
     * same form, which is the shape of the bug this model exists to remove.
     */
    @Test
    @Order(23)
    void aQuestionReachingBothARoleAndAGroupIsAskedOnce() {
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
        var crew = memberGroupRepo.create(station.id(), "Besatzung " + member.id());
        memberGroupRepo.addMember(crew.id(), member.id());
        var field = ask("Schuhgröße", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 26);
        service.assignToGroup(field.id(), crew.id(), 26, null, null, null);

        var fields = service.findApplicableFields(member.id());

        assertEquals(
                1,
                fields.stream().filter(f -> f.id() == field.id()).count(),
                "reached by role and by group, asked once");

        service.delete(field.id());
        memberGroupRepo.delete(crew.id());
    }

    @Test
    @Order(23)
    void findApplicableFieldsNonExistentMember() {
        var fields = service.findApplicableFields(99999);
        assertTrue(fields.isEmpty());
    }

    // -- What counts as a complete profile --

    @Test
    @Order(24)
    void isProfileCompleteReadonlyRequired() {
        // Readonly + required fields should be skipped
        var readonlyReqField =
                ask("ReadonlyReq", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 30, true, true);
        var account3 = accountRepo.create("pfield-readonly@test.com", "Readonly", "Test");
        var member3 = stationMemberRepo.create(station.id(), account3.id());
        // Should be complete - readonly required fields are skipped
        assertTrue(service.isProfileComplete(member3.id()));
        service.delete(readonlyReqField.id());
        stationMemberRepo.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(24)
    void isProfileCompleteWithEmptyValue() {
        // Empty string and "\"\"" should count as missing
        var reqField = ask("EmptyValField", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MEMBER, 31, true, false);
        var account4 = accountRepo.create("pfield-emptyval@test.com", "Empty", "Val");
        var member4 = stationMemberRepo.create(station.id(), account4.id());
        // Set empty quoted value
        service.setValues(member4.id(), List.of(new FieldValueEntry(reqField.id(), "\"\"")), member4.id());
        assertFalse(service.isProfileComplete(member4.id()));
        service.delete(reqField.id());
        stationMemberRepo.delete(member4.id());
        accountRepo.delete(account4.id());
    }

    /**
     * A question put to a group counts against the members of that group.
     *
     * <p>It used to be thrown away here, so somebody in the instructors' group could be missing an
     * answer the instructors are required to give and still be told their profile was complete. The
     * gap reached neither the task list nor the reminder on the dashboard.
     */
    @Test
    @Order(24)
    void aGroupsQuestionIsRequiredOfItsMembers() {
        var group = memberGroupRepo.create(station.id(), "Ausbilder " + java.util.UUID.randomUUID());
        var groupField = service.create(
                station.id(), "GroupField", ProfileFieldType.TEXT, ProfileFieldConfig.empty(), true, false, null);
        service.assignToGroup(groupField.id(), group.id(), 32, null, null, null);
        var account5 = accountRepo.create("pfield-group@test.com", "Group", "Member");
        var member5 = stationMemberRepo.create(station.id(), account5.id());

        assertTrue(service.isProfileComplete(member5.id()), "the question is not theirs before they are in the group");

        memberGroupRepo.addMember(group.id(), member5.id());
        assertFalse(service.isProfileComplete(member5.id()), "in the group, the group's question is theirs to answer");

        service.setValues(member5.id(), List.of(new FieldValueEntry(groupField.id(), "\"Ja\"")), member5.id());
        assertTrue(service.isProfileComplete(member5.id()));

        service.delete(groupField.id());
        stationMemberRepo.delete(member5.id());
        accountRepo.delete(account5.id());
        memberGroupRepo.delete(group.id());
    }

    /**
     * The group a question is asked of is an assignment, so it survives as a row of its own.
     *
     * <p>It used to live inside the field's config, where a field that lost it belonged nowhere and
     * was shown nowhere. A group that is deleted now takes its assignment with it and leaves the
     * question standing.
     */
    @Test
    @Order(24)
    void groupFieldKeepsTheGroupItNames() {
        var group = memberGroupRepo.create(station.id(), "Namensgruppe " + java.util.UUID.randomUUID());
        var groupField = service.create(
                station.id(), "GroupOwned", ProfileFieldType.TEXT, ProfileFieldConfig.empty(), false, false, null);
        service.assignToGroup(groupField.id(), group.id(), 33, null, null, null);

        var assignment = service.findAssignments(groupField.id()).getFirst();
        assertEquals(ProfileFieldTarget.GROUP, assignment.targetKind());
        assertEquals(group.id(), assignment.groupId(), "the group survives being written and read back");

        service.delete(groupField.id());
        memberGroupRepo.delete(group.id());
    }

    // -- acknowledgeAll with changes --

    @Test
    @Order(24)
    void acknowledgeAllWithPendingChanges() {
        // Create a new field change by setting a value
        var tmpField =
                ask("AckAllField", ProfileFieldType.TEXT, "{\"notifyOnChange\":true}", ProfileFieldScope.MEMBER, 40);
        service.setValues(member.id(), List.of(new FieldValueEntry(tmpField.id(), "\"initial\"")), member.id());
        service.setValues(member.id(), List.of(new FieldValueEntry(tmpField.id(), "\"changed\"")), member.id());

        // Now acknowledgeAll from a different acknowledger
        var account5 = accountRepo.create("pfield-ack@test.com", "Ack", "User");
        var member5 = stationMemberRepo.create(station.id(), account5.id());
        var acks = service.acknowledgeAll(member.id(), member5.id(), "Batch ack");
        assertNotNull(acks);

        service.delete(tmpField.id());
        stationMemberRepo.delete(member5.id());
        accountRepo.delete(account5.id());
    }

    /**
     * What the station changed itself is not put back in front of the station to confirm.
     *
     * <p>Acknowledging exists so that what a member alters about themselves is seen by somebody who
     * looks after the roll. Where that person made the change, it has already been seen, and the list
     * of things to look at filled up with entries whose only reader was the one who wrote them.
     */
    @Test
    @Order(24)
    void whatAManagerChangesNeedsNoAcknowledgement() {
        var watched = ask(
                "SelbstGeaendert", ProfileFieldType.TEXT, "{\"notifyOnChange\":true}", ProfileFieldScope.MEMBER, 41);
        var changesRole = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_CHANGES)
                .orElseThrow();
        var managerAccount = accountRepo.create("pfield-manager@test.com", "Rolle", "Fuehrung");
        var manager = stationMemberRepo.create(station.id(), managerAccount.id());
        stationMemberRepo.grantPermission(manager.id(), changesRole.id());

        service.setValues(member.id(), List.of(new FieldValueEntry(watched.id(), "\"vom Mitglied\"")), member.id());
        service.setValues(member.id(), List.of(new FieldValueEntry(watched.id(), "\"vom Vorstand\"")), manager.id());

        var changes = service.findChanges(member.id()).stream()
                .filter(change -> change.fieldId() == watched.id())
                .toList();
        assertEquals(2, changes.size(), "both are still recorded, which is what the history is for");
        assertTrue(
                changes.stream().anyMatch(ProfileFieldChange::requiresAcknowledgement),
                "the member's own change still waits to be seen");
        assertTrue(
                changes.stream().anyMatch(change -> !change.requiresAcknowledgement()),
                "and the one the manager made does not");

        service.delete(watched.id());
        stationMemberRepo.delete(manager.id());
        accountRepo.delete(managerAccount.id());
    }

    @Test
    @Order(25)
    void deleteValue() {
        assertTrue(service.deleteValue(member.id(), fieldId));
        assertTrue(service.findValues(member.id()).isEmpty());
    }

    /**
     * Nobody is two kinds of member at once, so asking the team for a date of birth and asking the
     * guardians for one are two questions no single person can answer twice. A group is the exception:
     * a member belongs to any number of them and to a kind besides, so one of those blocks every other.
     */
    /**
     * A station asks for a date of birth once, and assigns it to everybody it wants it from.
     *
     * <p>There used to be one per kind of member, with a rule about which of them could coexist.
     * That rule is what a manager fell through: asked as team and as manager, they met two dates and
     * answered both. One definition cannot disagree with itself, so the rule is now simply one.
     */
    @Test
    @Order(29)
    void aStationAsksForADateOfBirthOnce() {
        var birthDate = ask("Geburtstag", ProfileFieldType.BIRTH_DATE, "{}", ProfileFieldScope.TEAM, 60);

        service.assignToRole(birthDate.id(), ProfileFieldScope.GUARDIAN, 61, null, null, null);
        service.assignToRole(birthDate.id(), ProfileFieldScope.MANAGER, 62, null, null, null);
        assertEquals(3, service.findAssignments(birthDate.id()).size(), "one question, three audiences");

        var refused = assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Noch ein Geburtstag",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        false,
                        false,
                        null),
                "a second date of birth is a duplicate, not a different question");
        assertTrue(refused.getMessage().contains("already asks"));

        profileFieldRepo.delete(birthDate.id());
    }

    /** Twenty fields moved by one drag is one request, not twenty, and moves only that form. */
    @Test
    @Order(29)
    void anOrderIsWrittenInOneGo() {
        var first = ask("Erstes", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MANAGER, 80);
        var second = ask("Zweites", ProfileFieldType.TEXT, "{}", ProfileFieldScope.MANAGER, 81);

        service.reorder(station.id(), ProfileFieldScope.MANAGER, List.of(second.id(), first.id()));

        // Nothing to move is not an error, and writes nothing
        service.reorder(station.id(), ProfileFieldScope.MANAGER, List.of());

        var ordered = service.findByStationAndScope(station.id(), ProfileFieldScope.MANAGER);
        assertEquals(second.id(), ordered.getFirst().field().id(), "the order given is the order stored");
        assertEquals(first.id(), ordered.get(1).field().id());

        profileFieldRepo.delete(first.id());
        profileFieldRepo.delete(second.id());
    }

    @Test
    @Order(30)
    void onlyOneBirthDateFieldPerStation() {
        var birthDate = ask("Geburtsdatum", ProfileFieldType.BIRTH_DATE, "{}", ProfileFieldScope.MEMBER, 50);

        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Zweites Geburtsdatum",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        false,
                        false,
                        null),
                "a station may declare one birth date field");

        var plain = ask("Eintrittsdatum", ProfileFieldType.DATE, "{}", ProfileFieldScope.MEMBER, 52);
        assertThrows(
                BadRequestResponse.class,
                () -> service.update(
                        plain.id(),
                        plain.name(),
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        false,
                        false,
                        null,
                        false),
                "turning a second field into the birth date is the same clash");

        assertTrue(
                service.update(
                                birthDate.id(),
                                "Geburtstag",
                                ProfileFieldType.BIRTH_DATE,
                                ProfileFieldConfig.parse("{}"),
                                false,
                                false,
                                null,
                                false)
                        .isPresent(),
                "the field that already is the birth date does not clash with itself");

        service.delete(plain.id());
        service.delete(birthDate.id());
    }

    @Test
    @Order(31)
    void aBirthDateFieldCanBeReplacedAfterTheFirstIsGone() {
        var first = ask("Geburtsdatum", ProfileFieldType.BIRTH_DATE, "{}", ProfileFieldScope.MEMBER, 60);
        service.delete(first.id());

        var second = ask("Geburtsdatum neu", ProfileFieldType.BIRTH_DATE, "{}", ProfileFieldScope.GUARDIAN, 61);
        assertEquals(ProfileFieldType.BIRTH_DATE, second.fieldType());
        service.delete(second.id());
    }

    /**
     * The profile is one form of two origins. What the cluster keeps to itself is readable and not
     * writable; what it leaves open the station answers, and the answer lands in the cluster's own table
     * rather than the station's.
     */
    @Test
    @Order(80)
    void aProfileCarriesTheClustersQuestionsBesideTheStationsOwn() {
        var home = stationRepo.create("Träger Profil");
        var cluster = clusterRepo.create("Kreisverband Profil", null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());

        var kept = clusterProfileFieldRepo.create(
                cluster.id(),
                "Führerscheinklasse",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null,
                true,
                false,
                null);
        clusterProfileFieldRepo.assignToRole(kept.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        var open = clusterProfileFieldRepo.create(
                cluster.id(),
                "Funkrufname",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null,
                false,
                false,
                null);
        clusterProfileFieldRepo.assignToRole(open.id(), ProfileFieldScope.MEMBER, 1, null, null, null);

        var fields = service.findApplicableFields(member.id());
        assertTrue(
                fields.stream().anyMatch(f -> f.origin() == FieldOrigin.STATION),
                "the station's own questions are still there");
        var keptField = fields.stream()
                .filter(f -> f.origin() == FieldOrigin.CLUSTER && f.name().equals("Führerscheinklasse"))
                .findFirst()
                .orElseThrow();
        assertTrue(keptField.readonlyAtStation(), "the cluster kept that one to itself");

        service.setValues(
                member.id(),
                List.of(
                        new FieldValueEntry(kept.id(), "\"C1\"", FieldOrigin.CLUSTER),
                        new FieldValueEntry(open.id(), "\"Florian 1\"", FieldOrigin.CLUSTER)),
                member.id());

        var answers = service.findValues(member.id());
        assertTrue(
                answers.stream().anyMatch(v -> v.origin() == FieldOrigin.CLUSTER && v.fieldId() == open.id()),
                "the station answered the question the cluster left open");
        assertTrue(
                answers.stream().noneMatch(v -> v.origin() == FieldOrigin.CLUSTER && v.fieldId() == kept.id()),
                "and left alone the one it did not");

        stationRepo.setCluster(station.id(), null);
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    /**
     * Opening a form and saving it is not a change to every question on it.
     *
     * <p>A question nobody has answered is stored as absent, and one answered with an empty box as
     * an empty string. They are different strings and the same thing, so saying nothing twice was
     * recorded as a change and put in front of somebody to confirm.
     */
    @Test
    @Order(60)
    void sayingNothingTwiceIsNotAChange() {
        var field =
                ask("Nothing said", ProfileFieldType.TEXT, "{\"notifyOnChange\":true}", ProfileFieldScope.MEMBER, 60);

        service.setValues(member.id(), List.of(new FieldValueEntry(field.id(), "\"\"")), member.id());

        assertTrue(
                profileFieldChangeRepo.findByMember(member.id()).stream()
                        .noneMatch(c -> c.fieldId() != null && c.fieldId() == field.id()),
                "nothing was said before and nothing was said now");
    }

    /**
     * An age counts itself, so nobody has changed one.
     *
     * <p>It was recorded as a change like any other and somebody was asked to confirm a number that
     * the passing of time had produced.
     */
    @Test
    @Order(61)
    void anAgeCountingItselfIsNotAChangeAnybodyMade() {
        var field = ask("Alter", ProfileFieldType.AGE, "{\"notifyOnChange\":true}", ProfileFieldScope.MEMBER, 61);

        service.setValues(member.id(), List.of(new FieldValueEntry(field.id(), "15")), member.id());

        assertTrue(
                profileFieldChangeRepo.findByMember(member.id()).stream()
                        .noneMatch(c -> c.fieldId() != null && c.fieldId() == field.id()),
                "a number nobody wrote is not a change anybody made");
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(fieldId));
        assertTrue(service.findById(fieldId).isEmpty());
    }
}

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
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
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

    @Test
    @Order(1)
    void create() {
        var field = service.create(
                station.id(),
                "Phone",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.MEMBER);
        assertNotNull(field);
        assertEquals("Phone", field.name());
        assertEquals(ProfileFieldScope.MEMBER, field.scope());
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

    @Test
    @Order(5)
    void update() {
        var updated =
                service.update(fieldId, "Mobile", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), 2, false);
        assertTrue(updated.isPresent());
        assertEquals("Mobile", updated.get().name());
        assertEquals(2, updated.get().position());
    }

    @Test
    @Order(6)
    void updateNonExistent() {
        var result = service.update(99999, "X", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), 1, false);
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
    @Order(20)
    void isProfileCompleteEmptyRoles() {
        // No roles → no applicable scopes → complete by default
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of()));
    }

    @Test
    @Order(21)
    void isProfileCompleteWithMemberRole() {
        // Member has a MEMBER-scope field with value - should be complete
        // The field has default config (not required), so should be complete
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("MEMBER")));
    }

    @Test
    @Order(22)
    void isProfileCompleteRequiredFieldMissing() {
        // Create a required field with no value for a new member
        var reqField = service.create(
                station.id(),
                "Required Field",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"required\":true}"),
                10,
                ProfileFieldScope.MEMBER);
        var account2 = accountRepo.create("pfield-empty@test.com", "Empty", "Member");
        var member2 = stationMemberRepo.create(station.id(), account2.id());

        assertFalse(service.isProfileComplete(member2.id(), station.id(), List.of("MEMBER")));

        // Cleanup
        service.delete(reqField.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
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
                ProfileFieldConfig.parse("{\"groupId\": " + drivers.id() + "}"),
                30,
                ProfileFieldScope.GROUP);
        var forOthers = service.create(
                station.id(),
                "Etwas anderes",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"groupId\": " + others.id() + "}"),
                31,
                ProfileFieldScope.GROUP);

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
        var guardianField = service.create(
                station.id(),
                "GuardianField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                20,
                ProfileFieldScope.GUARDIAN);
        var fields = service.findApplicableFields(member.id());
        assertTrue(fields.stream().anyMatch(f -> f.id() == guardianField.id()));
        service.delete(guardianField.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(23)
    void findApplicableFieldsForTeam() {
        stationMemberRepo.setUserType(member.id(), StationUserType.TEAM);
        var teamField = service.create(
                station.id(),
                "TeamField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                20,
                ProfileFieldScope.TEAM);
        var fields = service.findApplicableFields(member.id());
        assertTrue(fields.stream().anyMatch(f -> f.id() == teamField.id()));
        service.delete(teamField.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(23)
    void findApplicableFieldsForManager() {
        stationMemberRepo.setUserType(member.id(), StationUserType.MANAGER);
        var mgrField = service.create(
                station.id(),
                "ManagerField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                20,
                ProfileFieldScope.MANAGER);
        var teamField = service.create(
                station.id(),
                "TeamFieldForManager",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                21,
                ProfileFieldScope.TEAM);

        var fields = service.findApplicableFields(member.id());

        assertTrue(fields.stream().anyMatch(f -> f.id() == mgrField.id()));
        assertTrue(
                fields.stream().anyMatch(f -> f.id() == teamField.id()),
                "a manager is staff first, so the team's questions are put to them too");

        service.delete(mgrField.id());
        service.delete(teamField.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(23)
    void findApplicableFieldsForTrial() {
        stationMemberRepo.setUserType(member.id(), StationUserType.TRIAL);
        var fields = service.findApplicableFields(member.id());
        assertNotNull(fields);
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(23)
    void findApplicableFieldsNonExistentMember() {
        var fields = service.findApplicableFields(99999);
        assertTrue(fields.isEmpty());
    }

    // -- isProfileComplete with various role scopes --

    @Test
    @Order(24)
    void isProfileCompleteWithGuardianRole() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("GUARDIAN")));
    }

    @Test
    @Order(24)
    void isProfileCompleteWithTeamRole() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("TEAM")));
    }

    @Test
    @Order(24)
    void isProfileCompleteWithManagerRole() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("MANAGER")));
    }

    @Test
    @Order(24)
    void isProfileCompleteWithAdminRole() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("ADMIN")));
    }

    @Test
    @Order(24)
    void isProfileCompleteWithAttendanceManagerRole() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("ATTENDANCE_MANAGER")));
    }

    @Test
    @Order(24)
    void isProfileCompleteWithMultipleRoles() {
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("MEMBER", "TEAM", "MANAGER", "ADMIN")));
    }

    @Test
    @Order(24)
    void isProfileCompleteReadonlyRequired() {
        // Readonly + required fields should be skipped
        var readonlyReqField = service.create(
                station.id(),
                "ReadonlyReq",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"required\":true,\"readonly\":true}"),
                30,
                ProfileFieldScope.MEMBER);
        var account3 = accountRepo.create("pfield-readonly@test.com", "Readonly", "Test");
        var member3 = stationMemberRepo.create(station.id(), account3.id());
        // Should be complete - readonly required fields are skipped
        assertTrue(service.isProfileComplete(member3.id(), station.id(), List.of("MEMBER")));
        service.delete(readonlyReqField.id());
        stationMemberRepo.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(24)
    void isProfileCompleteWithEmptyValue() {
        // Empty string and "\"\"" should count as missing
        var reqField = service.create(
                station.id(),
                "EmptyValField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"required\":true}"),
                31,
                ProfileFieldScope.MEMBER);
        var account4 = accountRepo.create("pfield-emptyval@test.com", "Empty", "Val");
        var member4 = stationMemberRepo.create(station.id(), account4.id());
        // Set empty quoted value
        service.setValues(member4.id(), List.of(new FieldValueEntry(reqField.id(), "\"\"")), member4.id());
        assertFalse(service.isProfileComplete(member4.id(), station.id(), List.of("MEMBER")));
        service.delete(reqField.id());
        stationMemberRepo.delete(member4.id());
        accountRepo.delete(account4.id());
    }

    @Test
    @Order(24)
    void isProfileCompleteGroupScopeSkipped() {
        // GROUP scope fields should be skipped
        var groupField = service.create(
                station.id(),
                "GroupField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"required\":true}"),
                32,
                ProfileFieldScope.GROUP);
        assertTrue(service.isProfileComplete(member.id(), station.id(), List.of("MEMBER")));
        service.delete(groupField.id());
    }

    /**
     * A field of group scope is only ever shown at its group, so the group it names has to survive
     * being stored. Dropping it silently leaves a field that belongs nowhere and is shown nowhere.
     */
    @Test
    @Order(24)
    void groupFieldKeepsTheGroupItNames() {
        var groupField = service.create(
                station.id(),
                "GroupOwned",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"groupId\":7}"),
                33,
                ProfileFieldScope.GROUP);

        assertEquals(7, groupField.config().groupId(), "the group survives being written");
        assertEquals(7, service.findById(groupField.id()).orElseThrow().config().groupId(), "and reading it back");

        service.delete(groupField.id());
    }

    // -- acknowledgeAll with changes --

    @Test
    @Order(24)
    void acknowledgeAllWithPendingChanges() {
        // Create a new field change by setting a value
        var tmpField = service.create(
                station.id(),
                "AckAllField",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"notifyOnChange\":true}"),
                40,
                ProfileFieldScope.MEMBER);
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
        var watched = service.create(
                station.id(),
                "SelbstGeaendert",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{\"notifyOnChange\":true}"),
                41,
                ProfileFieldScope.MEMBER);
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
    @Test
    @Order(29)
    void aDateOfBirthPerKindOfMemberIsFineAndAGroupOneIsNot() {
        var forTeam = service.create(
                station.id(),
                "Geburtstag Team",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                60,
                ProfileFieldScope.TEAM);

        var forGuardians = service.create(
                station.id(),
                "Geburtstag Eltern",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                61,
                ProfileFieldScope.GUARDIAN);
        assertNotNull(forGuardians, "a second kind of member may be asked as well");

        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Noch einer",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        62,
                        ProfileFieldScope.TEAM),
                "asking the same kind twice would give one member two dates");

        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Gruppengeburtstag",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        63,
                        ProfileFieldScope.GROUP),
                "a group reaches members who are already asked by their kind");

        profileFieldRepo.delete(forTeam.id());
        profileFieldRepo.delete(forGuardians.id());
    }

    /** With one asked of a group, no other date of birth may be added at all. */
    @Test
    @Order(29)
    void aGroupDateOfBirthBlocksEveryOther() {
        var forGroup = service.create(
                station.id(),
                "Geburtstag Gruppe",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                70,
                ProfileFieldScope.GROUP);

        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Geburtstag Team",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        71,
                        ProfileFieldScope.TEAM),
                "the group one can meet a team member as well");

        profileFieldRepo.delete(forGroup.id());
    }

    /** Twenty fields moved by one drag is one request, not twenty. */
    @Test
    @Order(29)
    void anOrderIsWrittenInOneGo() {
        var first = service.create(
                station.id(),
                "Erstes",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                80,
                ProfileFieldScope.MANAGER);
        var second = service.create(
                station.id(),
                "Zweites",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                81,
                ProfileFieldScope.MANAGER);

        service.reorder(station.id(), List.of(second.id(), first.id()));

        // Nothing to move is not an error, and writes nothing
        service.reorder(station.id(), List.of());

        var ordered = service.findByStationAndScope(station.id(), ProfileFieldScope.MANAGER);
        assertEquals(second.id(), ordered.getFirst().id(), "the order given is the order stored");
        assertEquals(first.id(), ordered.get(1).id());

        profileFieldRepo.delete(first.id());
        profileFieldRepo.delete(second.id());
    }

    @Test
    @Order(30)
    void onlyOneBirthDateFieldPerStation() {
        var birthDate = service.create(
                station.id(),
                "Geburtsdatum",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                50,
                ProfileFieldScope.MEMBER);

        assertThrows(
                BadRequestResponse.class,
                () -> service.create(
                        station.id(),
                        "Zweites Geburtsdatum",
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        51,
                        ProfileFieldScope.MEMBER),
                "a station may declare one birth date field");

        var plain = service.create(
                station.id(),
                "Eintrittsdatum",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{}"),
                52,
                ProfileFieldScope.MEMBER);
        assertThrows(
                BadRequestResponse.class,
                () -> service.update(
                        plain.id(),
                        plain.name(),
                        ProfileFieldType.BIRTH_DATE,
                        ProfileFieldConfig.parse("{}"),
                        plain.position(),
                        false),
                "turning a second field into the birth date is the same clash");

        assertTrue(
                service.update(
                                birthDate.id(),
                                "Geburtstag",
                                ProfileFieldType.BIRTH_DATE,
                                ProfileFieldConfig.parse("{}"),
                                birthDate.position(),
                                false)
                        .isPresent(),
                "the field that already is the birth date does not clash with itself");

        service.delete(plain.id());
        service.delete(birthDate.id());
    }

    @Test
    @Order(31)
    void aBirthDateFieldCanBeReplacedAfterTheFirstIsGone() {
        var first = service.create(
                station.id(),
                "Geburtsdatum",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                60,
                ProfileFieldScope.MEMBER);
        service.delete(first.id());

        var second = service.create(
                station.id(),
                "Geburtsdatum neu",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                61,
                ProfileFieldScope.GUARDIAN);
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
                0,
                ProfileFieldScope.MEMBER,
                true,
                false,
                null);
        var open = clusterProfileFieldRepo.create(
                cluster.id(),
                "Funkrufname",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.MEMBER,
                false,
                false,
                null);

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

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(fieldId));
        assertTrue(service.findById(fieldId).isEmpty());
    }
}

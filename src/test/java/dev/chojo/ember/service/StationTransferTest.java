/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("database")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationTransferTest extends RepositoryTestBase {
    private static StationExportService exportService;
    private static StationImportService importService;
    private static int sourceStationId;
    private static Map<String, Object> exportedData;

    // Track source IDs for verification
    private static int sourceMember1Id;
    private static int sourceMember2Id;
    private static int sourceMember3Id;

    private static Map<String, Object> exportAllTables(int stationId) {
        var merged = new HashMap<String, Object>();
        for (String table : StationExportService.TABLE_ORDER) {
            var page = exportService.exportTable(stationId, table, 0, 10000);
            merged.putAll(page);
        }
        return merged;
    }

    // ==================== Setup with rich demo data ====================

    @Test
    @Order(1)
    void setup() {
        exportService = new StationExportService(stationRepo);
        importService = new StationImportService(stationRepo, stationMemberRepo, accountRepo);

        // Create station with full settings
        var station = stationRepo.create("Jugendfeuerwehr Musterstadt");
        sourceStationId = station.id();
        stationRepo.updateTimezone(sourceStationId, "Europe/Berlin");
        stationRepo.updateLocale(sourceStationId, "de-DE");
        stationRepo.setDisabledModules(sourceStationId, Set.of(StationModule.LOST_AND_FOUND));

        // --- Accounts & Members ---
        // Manager with full account
        var managerAccount = accountRepo.create("manager@jf-musterstadt.de", "Thomas", "Müller", true);
        var manager = stationMemberRepo.create(sourceStationId, managerAccount.id());
        sourceMember1Id = manager.id();

        // Team member (trainer)
        var trainerAccount = accountRepo.create("trainer@jf-musterstadt.de", "Sandra", "Weber", true);
        var trainer = stationMemberRepo.create(sourceStationId, trainerAccount.id());
        sourceMember2Id = trainer.id();

        // Guardian with child
        var guardianAccount = accountRepo.create("eltern@jf-musterstadt.de", "Maria", "Schmidt", true);
        var guardian = stationMemberRepo.create(sourceStationId, guardianAccount.id());

        // Regular member (child)
        var childAccount = accountRepo.create("kind@jf-musterstadt.de", "Luca", "Schmidt", true);
        var child = stationMemberRepo.create(sourceStationId, childAccount.id());
        sourceMember3Id = child.id();

        // Former member (no account link needed)
        var formerAccount = accountRepo.create("ehemalig@jf-musterstadt.de", "Max", "Alt", true);
        var former = stationMemberRepo.create(sourceStationId, formerAccount.id());

        // --- Roles ---
        Role managerRole = stationMemberRepo.findRoleByName(Roles.MANAGER).orElseThrow();
        Role teamRole = stationMemberRepo.findRoleByName(Roles.TEAM).orElseThrow();
        Role memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        Role guardianRole = stationMemberRepo.findRoleByName(Roles.GUARDIAN).orElseThrow();
        Role attendanceRole =
                stationMemberRepo.findRoleByName(Roles.ATTENDANCE_MANAGER).orElseThrow();

        stationMemberRepo.addRole(manager.id(), managerRole.id());
        stationMemberRepo.addRole(trainer.id(), teamRole.id());
        stationMemberRepo.addRole(trainer.id(), attendanceRole.id());
        stationMemberRepo.addRole(guardian.id(), guardianRole.id());
        stationMemberRepo.addRole(child.id(), memberRole.id());
        stationMemberRepo.addRole(former.id(), memberRole.id());

        // --- Guardian → child relationship ---
        stationMemberRepo.addManager(guardian.id(), child.id());

        // --- Groups ---
        var anfaenger = memberGroupRepo.create(sourceStationId, "Anfänger");
        var fortgeschritten = memberGroupRepo.create(sourceStationId, "Fortgeschrittene");
        memberGroupRepo.addMember(anfaenger.id(), child.id());
        memberGroupRepo.addMember(fortgeschritten.id(), trainer.id());
        memberGroupRepo.addMember(fortgeschritten.id(), manager.id());

        // --- Tags ---
        var tagSchwimmer = userTagRepo.create(sourceStationId, "Schwimmer");
        var tagErsteHilfe = userTagRepo.create(sourceStationId, "Erste Hilfe");
        userTagRepo.addMember(tagSchwimmer.id(), child.id());
        userTagRepo.addMember(tagSchwimmer.id(), trainer.id());
        userTagRepo.addMember(tagErsteHilfe.id(), manager.id());

        // --- Profile fields ---
        var fieldTelefon = profileFieldRepo.create(
                sourceStationId,
                "Telefon",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);
        var fieldGeburtstag = profileFieldRepo.create(
                sourceStationId,
                "Geburtstag",
                ProfileFieldType.DATE,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.MEMBER);
        var fieldNotizen = profileFieldRepo.create(
                sourceStationId,
                "Notizen",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.TEAM);
        profileFieldRepo.setValue(manager.id(), fieldTelefon.id(), "\"0151-11111\"");
        profileFieldRepo.setValue(trainer.id(), fieldTelefon.id(), "\"0151-22222\"");
        profileFieldRepo.setValue(child.id(), fieldGeburtstag.id(), "\"2012-05-15\"");
        profileFieldRepo.setValue(trainer.id(), fieldNotizen.id(), "\"Sehr zuverlässig\"");

        // --- Attendance templates ---
        var templateStandard = attendanceRepo.createTemplate(sourceStationId, "Standard-Übung");
        attendanceRepo.createTemplateField(
                templateStandard.id(), "Leiter", AttendanceFieldType.MEMBER, AttendanceFieldConfig.parse("{}"), 0);
        attendanceRepo.createTemplateField(
                templateStandard.id(), "Thema", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        var templateSonder = attendanceRepo.createTemplate(sourceStationId, "Sondertermin");
        attendanceRepo.createTemplateField(
                templateSonder.id(),
                "Verantwortlich",
                AttendanceFieldType.MEMBER,
                AttendanceFieldConfig.parse("{}"),
                0);

        // --- Event categories ---
        var catTraining = eventRepo.createCategory(sourceStationId, "Training", 0);
        var catSonder = eventRepo.createCategory(sourceStationId, "Sondertermin", 1);

        // --- Events ---
        var now = Instant.now();
        eventRepo.create(
                sourceStationId,
                "Montags Training",
                "Wöchentliches Training",
                StationEvent.EventType.RECURRING,
                1,
                now,
                now.plusSeconds(5400),
                templateStandard.id(),
                false,
                null,
                false,
                catTraining.id(),
                null,
                null,
                null);
        eventRepo.create(
                sourceStationId,
                "Donnerstags Training",
                "Fortgeschrittene",
                StationEvent.EventType.RECURRING,
                4,
                now,
                now.plusSeconds(5400),
                null,
                true,
                now.plusSeconds(86400),
                true,
                catTraining.id(),
                null,
                null,
                null);
        eventRepo.create(
                sourceStationId,
                "Sommerfest",
                "Jährliches Sommerfest",
                StationEvent.EventType.ONE_TIME,
                null,
                now.plusSeconds(2592000),
                now.plusSeconds(2592000 + 14400),
                null,
                true,
                now.plusSeconds(2505600),
                false,
                catSonder.id(),
                null,
                null,
                null);

        // --- Inventories ---
        var invHelme = inventoryRepo.create(sourceStationId, "Helme", InventoryType.INTERNAL, true);
        inventoryRepo.createSize(invHelme.id(), "S", 0, "");
        inventoryRepo.createSize(invHelme.id(), "M", 1, "");
        inventoryRepo.createSize(invHelme.id(), "L", 2, "");
        inventoryRepo.createItem(invHelme.id(), "H-001", "Helm 1", null, "{}");
        inventoryRepo.createItem(invHelme.id(), "H-002", "Helm 2", null, "{}");
        inventoryRepo.createItem(invHelme.id(), "H-003", "Helm 3", null, "{}");

        var invStiefel = inventoryRepo.create(sourceStationId, "Stiefel", InventoryType.INTERNAL, true);
        inventoryRepo.createSize(invStiefel.id(), "38", 0, "");
        inventoryRepo.createSize(invStiefel.id(), "42", 1, "");
        inventoryRepo.createItem(invStiefel.id(), "S-001", "Stiefel 1", null, "{}");
        inventoryRepo.createItem(invStiefel.id(), "S-002", "Stiefel 2", null, "{}");

        // --- Forms ---
        var form = formRepo.create(
                sourceStationId,
                "Zufriedenheitsumfrage",
                "Wie zufrieden bist du?",
                false,
                true,
                null,
                null,
                manager.id());
        formRepo.createQuestion(
                form.id(),
                0,
                FormQuestionType.RATING,
                "Gesamtzufriedenheit",
                "Bewerte von 1-5",
                true,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR));
        formRepo.createQuestion(
                form.id(),
                1,
                FormQuestionType.TEXT,
                "Verbesserungsvorschläge",
                "Was können wir besser machen?",
                false,
                false,
                new FormQuestionConfig.Text(false));
        formRepo.createQuestion(
                form.id(),
                2,
                FormQuestionType.CHOICE,
                "Lieblingsübung",
                "",
                false,
                false,
                FormQuestionConfig.parse(
                        FormQuestionType.CHOICE,
                        "{\"options\":[\"Löschangriff\",\"Knoten\",\"Erste Hilfe\",\"Sport\"]}"));
    }

    // ==================== Export tests ====================

    @Test
    @Order(10)
    void exportContainsAllSections() {
        exportedData = exportAllTables(sourceStationId);

        assertNotNull(exportedData.get("station"));
        assertNotNull(exportedData.get("members"));
        assertNotNull(exportedData.get("memberRoles"));
        assertNotNull(exportedData.get("groups"));
        assertNotNull(exportedData.get("groupMembers"));
        assertNotNull(exportedData.get("tags"));
        assertNotNull(exportedData.get("tagMembers"));
        assertNotNull(exportedData.get("managerRelations"));
        assertNotNull(exportedData.get("profileFields"));
        assertNotNull(exportedData.get("profileFieldValues"));
        assertNotNull(exportedData.get("eventCategories"));
        assertNotNull(exportedData.get("events"));
        assertNotNull(exportedData.get("attendanceTemplates"));
        assertNotNull(exportedData.get("attendanceTemplateFields"));
        assertNotNull(exportedData.get("inventories"));
        assertNotNull(exportedData.get("inventorySizes"));
        assertNotNull(exportedData.get("inventoryItems"));
        assertNotNull(exportedData.get("forms"));
        assertNotNull(exportedData.get("formQuestions"));
        assertNotNull(exportedData.get("disabledModules"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(11)
    void exportHasCorrectCounts() {
        assertEquals(5, ((List<?>) exportedData.get("members")).size());
        assertEquals(2, ((List<?>) exportedData.get("groups")).size());
        assertEquals(3, ((List<?>) exportedData.get("groupMembers")).size());
        assertEquals(2, ((List<?>) exportedData.get("tags")).size());
        assertEquals(3, ((List<?>) exportedData.get("tagMembers")).size());
        assertEquals(1, ((List<?>) exportedData.get("managerRelations")).size());
        assertEquals(3, ((List<?>) exportedData.get("profileFields")).size());
        assertEquals(4, ((List<?>) exportedData.get("profileFieldValues")).size());
        assertEquals(2, ((List<?>) exportedData.get("eventCategories")).size());
        assertEquals(3, ((List<?>) exportedData.get("events")).size());
        assertEquals(2, ((List<?>) exportedData.get("attendanceTemplates")).size());
        assertEquals(3, ((List<?>) exportedData.get("attendanceTemplateFields")).size());
        assertEquals(2, ((List<?>) exportedData.get("inventories")).size());
        assertEquals(5, ((List<?>) exportedData.get("inventorySizes")).size());
        assertEquals(5, ((List<?>) exportedData.get("inventoryItems")).size());
        assertEquals(1, ((List<?>) exportedData.get("forms")).size());
        assertEquals(3, ((List<?>) exportedData.get("formQuestions")).size());
        assertTrue(((List<?>) exportedData.get("disabledModules")).contains("LOST_AND_FOUND"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(12)
    void exportMembersIncludeAccountData() {
        var members = (List<Map<String, Object>>) exportedData.get("members");
        var manager = members.stream()
                .filter(m -> "manager@jf-musterstadt.de".equals(m.get("account_email")))
                .findFirst();
        assertTrue(manager.isPresent());
        assertEquals("Thomas", manager.get().get("account_first_name"));
        assertEquals("Müller", manager.get().get("account_last_name"));
    }

    @Test
    @Order(15)
    void paginationWorks() {
        var page1 = exportService.exportTable(sourceStationId, "members", 0, 2);
        assertEquals(2, ((List<?>) page1.get("members")).size());

        var page2 = exportService.exportTable(sourceStationId, "members", 2, 2);
        assertEquals(2, ((List<?>) page2.get("members")).size());

        var page3 = exportService.exportTable(sourceStationId, "members", 4, 2);
        assertEquals(1, ((List<?>) page3.get("members")).size());

        var page4 = exportService.exportTable(sourceStationId, "members", 5, 2);
        assertTrue(((List<?>) page4.get("members")).isEmpty());
    }

    // ==================== Import into new station ====================

    @Test
    @Order(20)
    void importCreatesNewStationWithAllData() {
        var result = importService.importStation(exportedData);

        assertTrue(result.stationId() > 0);
        assertNotEquals(sourceStationId, result.stationId());
        assertEquals("Jugendfeuerwehr Musterstadt", result.stationName());

        var imported = stationRepo.findById(result.stationId());
        assertTrue(imported.isPresent());
        assertEquals("Europe/Berlin", imported.get().timezone());
        assertEquals("de-DE", imported.get().locale());

        // Members — 5 created (account linking creates accounts for those with emails)
        var importedMembers = stationMemberRepo.findByStation(result.stationId());
        assertEquals(5, importedMembers.size());

        // Groups
        var importedGroups = memberGroupRepo.findByStation(result.stationId());
        assertEquals(2, importedGroups.size());
        var groupNames = importedGroups.stream().map(MemberGroup::name).sorted().toList();
        assertEquals(List.of("Anfänger", "Fortgeschrittene"), groupNames);

        // Tags
        var importedTags = userTagRepo.findByStation(result.stationId());
        assertEquals(2, importedTags.size());

        // Profile fields
        var importedFields = profileFieldRepo.findByStation(result.stationId());
        assertEquals(3, importedFields.size());
        var fieldNames =
                importedFields.stream().map(ProfileField::name).sorted().toList();
        assertEquals(List.of("Geburtstag", "Notizen", "Telefon"), fieldNames);

        // Events
        var importedEvents = eventRepo.findByStation(result.stationId());
        assertEquals(3, importedEvents.size());

        // Inventories
        var importedInventories = inventoryRepo.findByStation(result.stationId());
        assertEquals(2, importedInventories.size());

        // Disabled modules
        assertTrue(stationRepo.findDisabledModules(result.stationId()).contains(StationModule.LOST_AND_FOUND));

        // Owner — should be set to the imported manager member
        var importedStation = stationRepo.findById(result.stationId()).orElseThrow();
        assertNotNull(importedStation.ownerMemberId(), "Station owner should be set after import");

        stationRepo.delete(result.stationId());
    }

    // ==================== Account linking ====================

    @SuppressWarnings("unchecked")
    @Test
    @Order(30)
    void importLinksExistingAccountsByEmail() {
        // Pre-create an account with an email that matches one in the export
        var preExisting = accountRepo.create("trainer@jf-musterstadt.de-import", "Sandra", "Weber", true);
        // This email won't match — let's use exact match instead
        accountRepo.delete(preExisting.id());

        // Create account with matching email (simulating a user who already registered on the target instance)
        var existingAccount = accountRepo.create("reimport-test@example.com", "Existing", "User", true);

        // Build custom export data with a member having that email
        var customData = new HashMap<>(exportedData);
        var members = new ArrayList<>((List<Map<String, Object>>) customData.get("members"));
        members.add(Map.of(
                "id", 9999,
                "display_name", "Reimport User",
                "former", false,
                "account_email", "reimport-test@example.com",
                "account_first_name", "Attacker",
                "account_last_name", "Name"));
        customData.put("members", members);

        var result = importService.importStation(customData);

        // Find the imported member linked to the existing account
        var linkedMember = stationMemberRepo.findByStationAndAccount(result.stationId(), existingAccount.id());
        assertTrue(linkedMember.isPresent(), "Member should be linked to existing account");

        // Verify the existing account was NOT overwritten (name should remain unchanged)
        var account = accountRepo.findById(existingAccount.id()).orElseThrow();
        assertEquals("Existing", account.firstName(), "Account name should not be overwritten");
        assertEquals("User", account.lastName(), "Account last name should not be overwritten");

        stationRepo.delete(result.stationId());
        accountRepo.delete(existingAccount.id());
    }

    // ==================== Import into existing station ====================

    @Test
    @Order(35)
    void importIntoExistingStationAddsData() {
        // Create a target station with one existing member
        var targetStation = stationRepo.create("Target Station");
        var targetAccount = accountRepo.create("target-owner@test.com", "Owner", "User", true);
        var targetMember = stationMemberRepo.create(targetStation.id(), targetAccount.id());

        // Verify station starts with 1 member
        assertEquals(1, stationMemberRepo.findByStation(targetStation.id()).size());

        // Import the exported data into the existing station
        // (using importStation which is the sync version — async is for remote imports)
        var idMap = new StationImportService.IdRemapper();
        // We simulate what startRemoteImportInto does synchronously
        for (String table : List.of(
                "disabledModules",
                "members",
                "memberRoles",
                "groups",
                "groupMembers",
                "tags",
                "tagMembers",
                "managerRelations",
                "profileFields",
                "profileFieldValues",
                "eventCategories",
                "attendanceTemplates",
                "attendanceTemplateFields",
                "events",
                "inventories",
                "inventorySizes",
                "inventoryItems",
                "forms",
                "formQuestions")) {
            importService.importSingleTableForTest(targetStation.id(), table, exportedData, idMap);
        }

        // Now the target station should have 1 original + 5 imported = 6 members
        var allMembers = stationMemberRepo.findByStation(targetStation.id());
        assertEquals(6, allMembers.size());

        // Groups and events should be imported
        assertEquals(2, memberGroupRepo.findByStation(targetStation.id()).size());
        assertEquals(3, eventRepo.findByStation(targetStation.id()).size());
        assertEquals(2, inventoryRepo.findByStation(targetStation.id()).size());

        stationRepo.delete(targetStation.id());
        accountRepo.delete(targetAccount.id());
    }

    // ==================== Edge cases ====================

    @Test
    @Order(40)
    void importEmptyStationWorks() {
        var emptyStation = stationRepo.create("Empty Source");
        var data = exportAllTables(emptyStation.id());
        stationRepo.delete(emptyStation.id());

        var result = importService.importStation(data);
        assertTrue(result.stationId() > 0);
        assertEquals("Empty Source", result.stationName());
        assertTrue(stationMemberRepo.findByStation(result.stationId()).isEmpty());

        stationRepo.delete(result.stationId());
    }
}

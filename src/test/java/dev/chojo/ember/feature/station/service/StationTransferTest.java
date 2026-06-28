/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end station transfer test driving the metadata-driven engines:
 * {@link StationExportService} produces a bundle keyed by DB table names, and
 * {@link StationImportService} consumes it via {@code GenericTableImporter} +
 * {@code TableOrder}.
 *
 * <p>Each test that consumes a round-trip bundle deletes the source-side station before
 * the import to simulate cross-instance reality (the testcontainer DB is shared between
 * source and target).
 */
@Tag("database")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationTransferTest extends RepositoryTestBase {
    private static StationExportService exportService;
    private static StationImportService importService;
    private static int sourceStationId;
    private static Map<String, Object> exportedData;

    // Track source IDs for verification
    private static int sourceManagerId;
    private static int sourceTrainerId;
    private static int sourceChildId;

    /** Collects every wire entry the exporter produces for {@code stationId} into a single Map. */
    private static Map<String, Object> exportAllTables(int stationId) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        for (String table : exportService.getTableOrder()) {
            try {
                var page = exportService.exportTable(stationId, table, 0, 10_000);
                Object payload = page.get(table);
                if (payload != null) bundle.put(table, payload);
            } catch (Exception e) {
                throw new RuntimeException("Failed to export table: " + table, e);
            }
        }
        return bundle;
    }

    // ==================== Setup with rich demo data ====================

    @BeforeAll
    static void setup() {
        exportService = new StationExportService(stationRepo, new Api());
        importService = new StationImportService(
                stationRepo,
                accountRepo,
                exportService,
                new Api(),
                null,
                null,
                null,
                null,
                null,
                null,
                new FederationPartnerTransferFixupService());

        // Create station with full settings
        var station = stationRepo.create("Jugendfeuerwehr Musterstadt");
        sourceStationId = station.id();
        stationRepo.updateTimezone(sourceStationId, "Europe/Berlin");
        stationRepo.updateLocale(sourceStationId, "de-DE");
        stationRepo.setDisabledModules(sourceStationId, Set.of(StationModule.LOST_AND_FOUND));

        // --- Accounts & Members ---
        var managerAccount = accountRepo.create("manager@jf-musterstadt.de", "Thomas", "Müller", true);
        var manager = stationMemberRepo.create(sourceStationId, managerAccount.id());
        sourceManagerId = manager.id();

        var trainerAccount = accountRepo.create("trainer@jf-musterstadt.de", "Sandra", "Weber", true);
        var trainer = stationMemberRepo.create(sourceStationId, trainerAccount.id());
        sourceTrainerId = trainer.id();

        var guardianAccount = accountRepo.create("eltern@jf-musterstadt.de", "Maria", "Schmidt", true);
        var guardian = stationMemberRepo.create(sourceStationId, guardianAccount.id());

        var childAccount = accountRepo.create("kind@jf-musterstadt.de", "Luca", "Schmidt", true);
        var child = stationMemberRepo.create(sourceStationId, childAccount.id());
        sourceChildId = child.id();

        var formerAccount = accountRepo.create("ehemalig@jf-musterstadt.de", "Max", "Alt", true);
        var former = stationMemberRepo.create(sourceStationId, formerAccount.id());

        // --- Permissions ---
        Permission managerPerm = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        Permission loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        Permission memberPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        Permission guardianPerm = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_GUARDIAN)
                .orElseThrow();
        Permission attendancePerm = stationMemberRepo
                .findPermissionByName(StationPermission.ATTENDANCE_MANAGER)
                .orElseThrow();

        stationMemberRepo.grantPermission(manager.id(), managerPerm.id());
        stationMemberRepo.grantPermission(trainer.id(), loginPerm.id());
        stationMemberRepo.grantPermission(trainer.id(), attendancePerm.id());
        stationMemberRepo.grantPermission(guardian.id(), guardianPerm.id());
        stationMemberRepo.grantPermission(child.id(), memberPerm.id());
        stationMemberRepo.grantPermission(former.id(), memberPerm.id());

        // --- User Types ---
        stationMemberRepo.setUserType(manager.id(), StationUserType.MANAGER);
        stationMemberRepo.setUserType(trainer.id(), StationUserType.TEAM);
        stationMemberRepo.setUserType(guardian.id(), StationUserType.GUARDIAN);
        stationMemberRepo.setUserType(child.id(), StationUserType.MEMBER);
        stationMemberRepo.setUserType(former.id(), StationUserType.MEMBER);

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
        var catTraining = eventRepo.createCategory(sourceStationId, "Training", 0, "#ff6421");
        var catSonder = eventRepo.createCategory(sourceStationId, "Sondertermin", 1, null);

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
                null,
                null);

        // --- Inventories ---
        var invHelme = inventoryRepo.create(sourceStationId, "Helme", InventoryType.INTERNAL, true);
        inventoryRepo.createSize(invHelme.id(), "S", 0, "");
        inventoryRepo.createSize(invHelme.id(), "M", 1, "");
        inventoryRepo.createSize(invHelme.id(), "L", 2, "");
        inventoryRepo.createItem(invHelme.id(), "H-001", "Helm 1", null, null);
        inventoryRepo.createItem(invHelme.id(), "H-002", "Helm 2", null, null);
        inventoryRepo.createItem(invHelme.id(), "H-003", "Helm 3", null, null);

        var invStiefel = inventoryRepo.create(sourceStationId, "Stiefel", InventoryType.INTERNAL, true);
        inventoryRepo.createSize(invStiefel.id(), "38", 0, "");
        inventoryRepo.createSize(invStiefel.id(), "42", 1, "");
        inventoryRepo.createItem(invStiefel.id(), "S-001", "Stiefel 1", null, null);
        inventoryRepo.createItem(invStiefel.id(), "S-002", "Stiefel 2", null, null);

        // --- Forms ---
        var form = formRepo.create(
                sourceStationId,
                "Zufriedenheitsumfrage",
                "Wie zufrieden bist du?",
                false,
                true,
                false,
                null,
                null,
                manager.id(),
                FormPurpose.INTERNAL);
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

        // Wire keys are real DB table names produced by GenericTableExporter
        assertNotNull(exportedData.get("station"));
        assertNotNull(exportedData.get("station_member"));
        assertNotNull(exportedData.get("member_group"));
        assertNotNull(exportedData.get("member_group_entry"));
        assertNotNull(exportedData.get("user_tag"));
        assertNotNull(exportedData.get("user_tag_entry"));
        assertNotNull(exportedData.get("member_manager"));
        assertNotNull(exportedData.get("profile_field"));
        assertNotNull(exportedData.get("profile_field_value"));
        assertNotNull(exportedData.get("event_category"));
        assertNotNull(exportedData.get("station_event"));
        assertNotNull(exportedData.get("attendance_template"));
        assertNotNull(exportedData.get("attendance_template_field"));
        assertNotNull(exportedData.get("inventory"));
        assertNotNull(exportedData.get("inventory_size"));
        assertNotNull(exportedData.get("inventory_item"));
        assertNotNull(exportedData.get("form"));
        assertNotNull(exportedData.get("form_question"));
        assertNotNull(exportedData.get("station_disabled_module"));
        // account is a TRACKED custom-scope table reachable through station_member
        assertNotNull(exportedData.get("account"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(11)
    void exportHasCorrectCounts() {
        assertEquals(5, ((List<?>) exportedData.get("station_member")).size());
        assertEquals(2, ((List<?>) exportedData.get("member_group")).size());
        assertEquals(3, ((List<?>) exportedData.get("member_group_entry")).size());
        assertEquals(2, ((List<?>) exportedData.get("user_tag")).size());
        assertEquals(3, ((List<?>) exportedData.get("user_tag_entry")).size());
        assertEquals(1, ((List<?>) exportedData.get("member_manager")).size());
        assertEquals(3, ((List<?>) exportedData.get("profile_field")).size());
        assertEquals(4, ((List<?>) exportedData.get("profile_field_value")).size());
        assertEquals(2, ((List<?>) exportedData.get("event_category")).size());
        assertEquals(3, ((List<?>) exportedData.get("station_event")).size());
        assertEquals(2, ((List<?>) exportedData.get("attendance_template")).size());
        assertEquals(3, ((List<?>) exportedData.get("attendance_template_field")).size());
        assertEquals(2, ((List<?>) exportedData.get("inventory")).size());
        assertEquals(5, ((List<?>) exportedData.get("inventory_size")).size());
        assertEquals(5, ((List<?>) exportedData.get("inventory_item")).size());
        assertEquals(1, ((List<?>) exportedData.get("form")).size());
        assertEquals(3, ((List<?>) exportedData.get("form_question")).size());
        // station_disabled_module is a FLAT list of enum names
        assertTrue(((List<?>) exportedData.get("station_disabled_module")).contains("LOST_AND_FOUND"));
        // accounts: 5 members → 5 referenced accounts (customScope through station_member)
        assertEquals(5, ((List<?>) exportedData.get("account")).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(12)
    void exportMembersIncludeAccountData() {
        var members = (List<Map<String, Object>>) exportedData.get("station_member");
        var manager = members.stream()
                .filter(m -> "manager@jf-musterstadt.de".equals(m.get("account_email")))
                .findFirst();
        assertTrue(manager.isPresent());
        // The exporter emits account_uid alongside account_email so the importer can match by the
        // stable cross-instance identity; first_name / last_name lookups were removed because
        // matching humans by name is never unique.
        assertNotNull(manager.get().get("account_uid"));
        // user_type comes inline on the station_member row now (the legacy memberUserTypes wire is gone)
        assertEquals("MANAGER", manager.get().get("user_type"));
    }

    @Test
    @Order(15)
    void paginationWorks() {
        var page1 = exportService.exportTable(sourceStationId, "station_member", 0, 2);
        assertEquals(2, ((List<?>) page1.get("station_member")).size());

        var page2 = exportService.exportTable(sourceStationId, "station_member", 2, 2);
        assertEquals(2, ((List<?>) page2.get("station_member")).size());

        var page3 = exportService.exportTable(sourceStationId, "station_member", 4, 2);
        assertEquals(1, ((List<?>) page3.get("station_member")).size());

        var page4 = exportService.exportTable(sourceStationId, "station_member", 5, 2);
        assertTrue(((List<?>) page4.get("station_member")).isEmpty());
    }

    // ==================== Import into a new station ====================

    @Test
    @Order(20)
    void importCreatesNewStationWithAllData() {
        // Snapshot the bundle, then remove source-side data so the import is the sole creator
        // (the testcontainer DB is shared between source and target).
        var bundle = new HashMap<>(exportedData);
        stationRepo.delete(sourceStationId); // cascades members, groups, etc.
        for (String email : List.of(
                "manager@jf-musterstadt.de",
                "trainer@jf-musterstadt.de",
                "eltern@jf-musterstadt.de",
                "kind@jf-musterstadt.de",
                "ehemalig@jf-musterstadt.de")) {
            accountRepo.findByEmail(email).ifPresent(a -> accountRepo.delete(a.id()));
        }

        var result = importService.importStation(bundle);

        assertTrue(result.stationId() > 0);
        assertNotEquals(sourceStationId, result.stationId());
        assertEquals("Jugendfeuerwehr Musterstadt", result.stationName());

        var imported = stationRepo.findById(result.stationId()).orElseThrow();
        assertEquals("Europe/Berlin", imported.timezone());
        assertEquals("de-DE", imported.locale());

        var importedMembers = stationMemberRepo.findByStation(result.stationId());
        assertEquals(5, importedMembers.size());

        var importedGroups = memberGroupRepo.findByStation(result.stationId());
        assertEquals(2, importedGroups.size());
        var groupNames = importedGroups.stream().map(MemberGroup::name).sorted().toList();
        assertEquals(List.of("Anfänger", "Fortgeschrittene"), groupNames);

        var importedTags = userTagRepo.findByStation(result.stationId());
        assertEquals(2, importedTags.size());

        var importedFields = profileFieldRepo.findByStation(result.stationId());
        assertEquals(3, importedFields.size());
        var fieldNames =
                importedFields.stream().map(ProfileField::name).sorted().toList();
        assertEquals(List.of("Geburtstag", "Notizen", "Telefon"), fieldNames);

        var importedEvents = eventRepo.findByStation(result.stationId());
        assertEquals(3, importedEvents.size());

        var importedInventories = inventoryRepo.findByStation(result.stationId());
        assertEquals(2, importedInventories.size());

        assertTrue(stationRepo.findDisabledModules(result.stationId()).contains(StationModule.LOST_AND_FOUND));

        // Owner is assigned to the first imported MANAGER after all tables land.
        assertNotNull(imported.ownerMemberId(), "Station owner should be set after import");

        // PKs were remapped — source member ids should not match any target member id.
        var importedMemberIds = importedMembers.stream().map(StationMember::id).toList();
        assertTrue(importedMemberIds.stream().noneMatch(id -> id == sourceManagerId));
        assertTrue(importedMemberIds.stream().noneMatch(id -> id == sourceTrainerId));
        assertTrue(importedMemberIds.stream().noneMatch(id -> id == sourceChildId));

        stationRepo.delete(result.stationId());
    }

    // ==================== Account linking ====================

    @Test
    @Order(30)
    void importLinksExistingAccountsByEmail() {
        // Pre-create an account on the target with a known name and credential
        String email = "linked-import@example.com";
        var existingAccount = accountRepo.create(email, "Existing", "User", true);
        accountRepo.createCredential(existingAccount.id(), "$bcrypt$target-original");

        // Build a minimal synthetic bundle whose account entry uses the same email but a
        // different name + hash — the import path must NOT overwrite the existing account.
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("station", Map.of("name", "Link-Test Station"));
        bundle.put(
                "account",
                List.of(Map.of(
                        "email", email,
                        "first_name", "Attacker",
                        "last_name", "Override")));
        bundle.put(
                "account_credential",
                List.of(Map.of("account_email", email, "password_hash", "$bcrypt$source-override")));
        // A member referencing the same email via account_email lookup
        bundle.put(
                "station_member",
                List.of(Map.of(
                        "id", 9999,
                        "display_name", "Linked Member",
                        "former", false,
                        "user_type", "MEMBER",
                        "account_email", email,
                        "account_first_name", "Existing",
                        "account_last_name", "User")));

        var result = importService.importStation(bundle);

        // The pre-existing account is reused: name & credential remain unchanged
        var account = accountRepo.findById(existingAccount.id()).orElseThrow();
        assertEquals("Existing", account.firstName(), "account name must not be overwritten");
        assertEquals("User", account.lastName(), "account last name must not be overwritten");
        var cred = accountRepo.findCredential(existingAccount.id()).orElseThrow();
        assertEquals("$bcrypt$target-original", cred.passwordHash(), "existing credential must be preserved");

        // The station_member row resolves the FK back to the existing account via account_email lookup
        var linkedMember = stationMemberRepo.findByStationAndAccount(result.stationId(), existingAccount.id());
        assertTrue(linkedMember.isPresent(), "member should be linked to existing account");

        stationRepo.delete(result.stationId());
        accountRepo.delete(existingAccount.id());
    }

    // ==================== Import into an existing station ====================

    @Test
    @Order(35)
    void importIntoExistingStationAddsData() {
        // Set up a target station with one local member
        var targetStation = stationRepo.create("Target Station");
        var targetAccount = accountRepo.create("target-owner@example.com", "Owner", "User", true);
        stationMemberRepo.create(targetStation.id(), targetAccount.id());
        assertEquals(1, stationMemberRepo.findByStation(targetStation.id()).size());

        // Re-snapshot the source bundle (the earlier round-trip deleted the source station, so seed again)
        var freshSourceStation = stationRepo.create("Source Station 2");
        var newAccount = accountRepo.create("merge-source@example.com", "Merge", "Source", true);
        stationMemberRepo.create(freshSourceStation.id(), newAccount.id());
        memberGroupRepo.create(freshSourceStation.id(), "Imported Group A");
        memberGroupRepo.create(freshSourceStation.id(), "Imported Group B");

        var bundle = new ArrayList<>(exportService.getTableOrder())
                .stream()
                        .map(t -> Map.entry(t, exportService.exportTable(freshSourceStation.id(), t, 0, 10_000)))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().get(e.getKey())));

        // Remove source-side artefacts so the import has to recreate everything on the target
        stationRepo.delete(freshSourceStation.id());
        accountRepo.findByEmail("merge-source@example.com").ifPresent(a -> accountRepo.delete(a.id()));

        // Merge into the existing target station
        importService.importStationInto(targetStation.id(), bundle);

        // Target now has the original owner + the imported member, and the two new groups
        var allMembers = stationMemberRepo.findByStation(targetStation.id());
        assertEquals(2, allMembers.size());
        var groupNames = memberGroupRepo.findByStation(targetStation.id()).stream()
                .map(MemberGroup::name)
                .sorted()
                .toList();
        assertEquals(List.of("Imported Group A", "Imported Group B"), groupNames);

        stationRepo.delete(targetStation.id());
        accountRepo.delete(targetAccount.id());
        accountRepo.findByEmail("merge-source@example.com").ifPresent(a -> accountRepo.delete(a.id()));
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

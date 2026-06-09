/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProcedureRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static Account account2;
    private static StationMember member;
    private static StationMember member2;
    private static int templateId;
    private static int templateItemId1;
    private static int templateItemId2;
    private static int procedureId;
    private static int itemId1;
    private static int itemId2;
    private static int itemId3;
    private static int adHocProcedureId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Procedure Station");
        account = accountRepo.create("proc@test.com", "Proc", "User");
        account2 = accountRepo.create("proc2@test.com", "Proc2", "User2");
        member = stationMemberRepo.create(station.id(), account.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        accountRepo.delete(account2.id());
    }

    // ── Templates ──

    @Test
    @Order(1)
    void createTemplate() {
        ProcedureTemplate t = procedureRepo.createTemplate(station.id(), "Fire Drill", "Standard drill", member.id());
        assertNotNull(t);
        assertEquals("Fire Drill", t.name());
        assertEquals("Standard drill", t.description());
        assertEquals(station.id(), t.stationId());
        assertFalse(t.archived());
        templateId = t.id();
    }

    @Test
    @Order(2)
    void findTemplateById() {
        var opt = procedureRepo.findTemplateById(templateId);
        assertTrue(opt.isPresent());
        assertEquals("Fire Drill", opt.get().name());
    }

    @Test
    @Order(3)
    void findTemplateByIdNotFound() {
        assertTrue(procedureRepo.findTemplateById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findTemplatesByStation() {
        var list = procedureRepo.findTemplatesByStation(station.id(), false);
        assertEquals(1, list.size());
        assertEquals("Fire Drill", list.getFirst().name());
    }

    @Test
    @Order(5)
    void updateTemplate() {
        assertTrue(procedureRepo.updateTemplate(templateId, "Updated Drill", "Updated desc"));
        var updated = procedureRepo.findTemplateById(templateId).orElseThrow();
        assertEquals("Updated Drill", updated.name());
        assertEquals("Updated desc", updated.description());
    }

    @Test
    @Order(6)
    void updateTemplateNotFound() {
        assertFalse(procedureRepo.updateTemplate(99999, "No", "No"));
    }

    @Test
    @Order(7)
    void archiveTemplate() {
        // Create a second template to archive
        var t2 = procedureRepo.createTemplate(station.id(), "Temp", "Desc", member.id());
        assertTrue(procedureRepo.archiveTemplate(t2.id()));
        // Archived template should not appear in non-archived list
        var nonArchived = procedureRepo.findTemplatesByStation(station.id(), false);
        assertTrue(nonArchived.stream().noneMatch(t -> t.id() == t2.id()));
        // But should appear in include-archived list
        var all = procedureRepo.findTemplatesByStation(station.id(), true);
        assertTrue(all.stream().anyMatch(t -> t.id() == t2.id()));
    }

    // ── Template Items ──

    @Test
    @Order(10)
    void createTemplateItem() {
        templateItemId1 = procedureRepo
                .createTemplateItem(templateId, "Step 1", "First step", true, true, 1)
                .id();
        templateItemId2 = procedureRepo
                .createTemplateItem(templateId, "Step 2", "Second step", false, false, 2)
                .id();
        var items = procedureRepo.findTemplateItems(templateId);
        assertEquals(2, items.size());
        assertEquals("Step 1", items.get(0).title());
        assertEquals("Step 2", items.get(1).title());
    }

    @Test
    @Order(11)
    void updateTemplateItem() {
        assertTrue(
                procedureRepo.updateTemplateItem(templateItemId1, "Step 1 Updated", "Updated desc", false, false, 1));
        var items = procedureRepo.findTemplateItems(templateId);
        assertEquals("Step 1 Updated", items.get(0).title());
        assertFalse(items.get(0).isPublic());
    }

    @Test
    @Order(12)
    void updateTemplateItemNotFound() {
        assertFalse(procedureRepo.updateTemplateItem(99999, "No", "No", false, false, 1));
    }

    // ── Template Item Dependencies ──

    @Test
    @Order(15)
    void setAndFindTemplateItemDependencies() {
        // Step 2 depends on Step 1
        procedureRepo.setTemplateItemDependencies(templateId, List.of(new int[] {templateItemId2, templateItemId1}));
        var deps = procedureRepo.findTemplateItemDependencies(templateId);
        assertEquals(1, deps.size());
        assertEquals(templateItemId2, deps.getFirst()[0]);
        assertEquals(templateItemId1, deps.getFirst()[1]);
    }

    @Test
    @Order(16)
    void clearTemplateItemDependencies() {
        procedureRepo.setTemplateItemDependencies(templateId, List.of());
        assertTrue(procedureRepo.findTemplateItemDependencies(templateId).isEmpty());
        // Restore for snapshot test
        procedureRepo.setTemplateItemDependencies(templateId, List.of(new int[] {templateItemId2, templateItemId1}));
    }

    // ── Procedures ──

    @Test
    @Order(20)
    void createProcedure() {
        Instant dueAt = Instant.now().plus(7, ChronoUnit.DAYS);
        Procedure p = procedureRepo.createProcedure(
                station.id(), templateId, "Drill Instance", "Run the drill", true, member.id(), dueAt);
        assertNotNull(p);
        assertEquals("Drill Instance", p.name());
        assertEquals(ProcedureStatus.OPEN, p.status());
        assertEquals(station.id(), p.stationId());
        assertEquals(templateId, p.templateId());
        assertNull(p.resolvedAt());
        procedureId = p.id();
    }

    @Test
    @Order(21)
    void createAdHocProcedure() {
        Procedure p = procedureRepo.createProcedure(
                station.id(), null, "Ad Hoc Task", "No template", false, member.id(), null);
        assertNotNull(p);
        assertNull(p.templateId());
        assertNull(p.dueAt());
        adHocProcedureId = p.id();
    }

    @Test
    @Order(22)
    void findProcedureById() {
        assertTrue(procedureRepo.findProcedureById(procedureId).isPresent());
        assertTrue(procedureRepo.findProcedureById(99999).isEmpty());
    }

    @Test
    @Order(23)
    void findProceduresByStationNoFilter() {
        var list = procedureRepo.findProceduresByStation(station.id(), null);
        assertEquals(2, list.size());
    }

    @Test
    @Order(24)
    void findProceduresByStationWithFilter() {
        var list = procedureRepo.findProceduresByStation(station.id(), ProcedureStatus.OPEN);
        assertEquals(2, list.size());
        var resolved = procedureRepo.findProceduresByStation(station.id(), ProcedureStatus.RESOLVED);
        assertTrue(resolved.isEmpty());
    }

    @Test
    @Order(25)
    void updateProcedure() {
        Instant newDue = Instant.now().plus(14, ChronoUnit.DAYS);
        assertTrue(procedureRepo.updateProcedure(procedureId, "Renamed Drill", "New desc", false, newDue));
        var updated = procedureRepo.findProcedureById(procedureId).orElseThrow();
        assertEquals("Renamed Drill", updated.name());
        assertEquals("New desc", updated.description());
        assertFalse(updated.isPublic());
    }

    @Test
    @Order(26)
    void updateProcedureNotFound() {
        assertFalse(procedureRepo.updateProcedure(99999, "No", "No", false, null));
    }

    // ── Assignees ──

    @Test
    @Order(30)
    void addAndFindAssignees() {
        procedureRepo.addAssignee(procedureId, member.id());
        procedureRepo.addAssignee(procedureId, member2.id());
        var ids = procedureRepo.findAssigneeIds(procedureId);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(member.id()));
        assertTrue(ids.contains(member2.id()));
    }

    @Test
    @Order(31)
    void addAssigneeDuplicate() {
        // ON CONFLICT DO NOTHING — should not throw
        procedureRepo.addAssignee(procedureId, member.id());
        assertEquals(2, procedureRepo.findAssigneeIds(procedureId).size());
    }

    @Test
    @Order(32)
    void removeAssignee() {
        assertTrue(procedureRepo.removeAssignee(procedureId, member2.id()));
        assertEquals(1, procedureRepo.findAssigneeIds(procedureId).size());
    }

    @Test
    @Order(33)
    void removeAssigneeNotFound() {
        assertFalse(procedureRepo.removeAssignee(procedureId, 99999));
    }

    @Test
    @Order(34)
    void findProceduresByAssigneeNoFilter() {
        var list = procedureRepo.findProceduresByAssignee(station.id(), member.id(), null, false);
        assertEquals(1, list.size());
        assertEquals(procedureId, list.getFirst().id());
    }

    @Test
    @Order(35)
    void findProceduresByAssigneeWithFilter() {
        var open = procedureRepo.findProceduresByAssignee(station.id(), member.id(), ProcedureStatus.OPEN, false);
        assertEquals(1, open.size());
        var resolved =
                procedureRepo.findProceduresByAssignee(station.id(), member.id(), ProcedureStatus.RESOLVED, false);
        assertTrue(resolved.isEmpty());
    }

    // ── Procedure Items ──

    @Test
    @Order(40)
    void createItem() {
        var i1 = procedureRepo.createItem(procedureId, "Check exits", "Verify all exits clear", true, true, 1);
        var i2 = procedureRepo.createItem(procedureId, "Sound alarm", "Trigger alarm", true, true, 2);
        var i3 = procedureRepo.createItem(procedureId, "Evacuate", "Everyone out", true, false, 3);
        assertNotNull(i1);
        assertFalse(i1.checked());
        assertNull(i1.checkedAt());
        assertNull(i1.checkedBy());
        itemId1 = i1.id();
        itemId2 = i2.id();
        itemId3 = i3.id();
    }

    @Test
    @Order(41)
    void findItems() {
        var items = procedureRepo.findItems(procedureId);
        assertEquals(3, items.size());
        assertEquals("Check exits", items.get(0).title());
    }

    @Test
    @Order(42)
    void findItemById() {
        assertTrue(procedureRepo.findItemById(itemId1).isPresent());
        assertTrue(procedureRepo.findItemById(99999).isEmpty());
    }

    @Test
    @Order(43)
    void updateItem() {
        assertTrue(procedureRepo.updateItem(itemId1, "Check exits v2", "Updated", false, false, 1));
        var updated = procedureRepo.findItemById(itemId1).orElseThrow();
        assertEquals("Check exits v2", updated.title());
        assertFalse(updated.isPublic());
    }

    @Test
    @Order(44)
    void updateItemNotFound() {
        assertFalse(procedureRepo.updateItem(99999, "No", "No", false, false, 1));
    }

    @Test
    @Order(45)
    void updateItemNote() {
        assertTrue(procedureRepo.updateItemNote(itemId1, "Important note"));
        var item = procedureRepo.findItemById(itemId1).orElseThrow();
        assertEquals("Important note", item.note());
    }

    @Test
    @Order(46)
    void updateItemNoteNotFound() {
        assertFalse(procedureRepo.updateItemNote(99999, "No"));
    }

    // ── Item Dependencies ──

    @Test
    @Order(50)
    void addAndFindItemDependencies() {
        // Item 2 depends on Item 1
        procedureRepo.addItemDependency(itemId2, itemId1);
        var deps = procedureRepo.findItemDependencies(procedureId);
        assertEquals(1, deps.size());
        assertEquals(itemId2, deps.getFirst()[0]);
        assertEquals(itemId1, deps.getFirst()[1]);
    }

    // ── Check / Uncheck ──

    @Test
    @Order(60)
    void checkItem() {
        assertTrue(procedureRepo.checkItem(itemId1, member.id()));
        var item = procedureRepo.findItemById(itemId1).orElseThrow();
        assertTrue(item.checked());
        assertNotNull(item.checkedAt());
        assertEquals(member.id(), item.checkedBy());
    }

    @Test
    @Order(61)
    void checkItemAlreadyChecked() {
        // Already checked, should not change
        assertFalse(procedureRepo.checkItem(itemId1, member.id()));
    }

    @Test
    @Order(62)
    void uncheckItem() {
        assertTrue(procedureRepo.uncheckItem(itemId1));
        var item = procedureRepo.findItemById(itemId1).orElseThrow();
        assertFalse(item.checked());
        assertNull(item.checkedAt());
        assertNull(item.checkedBy());
    }

    @Test
    @Order(63)
    void uncheckItemAlreadyUnchecked() {
        assertFalse(procedureRepo.uncheckItem(itemId1));
    }

    // ── Snapshot ──

    @Test
    @Order(70)
    void snapshotTemplateItem() {
        // Restore template item to original state for snapshot
        procedureRepo.updateTemplateItem(templateItemId1, "Step 1", "First step", true, true, 1);
        var templateItem = procedureRepo.findTemplateItems(templateId).getFirst();
        var snapped = procedureRepo.snapshotTemplateItem(adHocProcedureId, templateItem);
        assertNotNull(snapped);
        assertEquals("Step 1", snapped.title());
        assertEquals("First step", snapped.description());
        assertEquals(adHocProcedureId, snapped.procedureId());
        assertFalse(snapped.checked());
    }

    // ── Resolve / Reopen ──

    @Test
    @Order(80)
    void resolveProcedure() {
        assertTrue(procedureRepo.resolveProcedure(procedureId));
        var p = procedureRepo.findProcedureById(procedureId).orElseThrow();
        assertEquals(ProcedureStatus.RESOLVED, p.status());
        assertNotNull(p.resolvedAt());
    }

    @Test
    @Order(81)
    void resolveAlreadyResolved() {
        assertFalse(procedureRepo.resolveProcedure(procedureId));
    }

    @Test
    @Order(82)
    void reopenProcedure() {
        assertTrue(procedureRepo.reopenProcedure(procedureId));
        var p = procedureRepo.findProcedureById(procedureId).orElseThrow();
        assertEquals(ProcedureStatus.OPEN, p.status());
        assertNull(p.resolvedAt());
    }

    @Test
    @Order(83)
    void reopenAlreadyOpen() {
        assertFalse(procedureRepo.reopenProcedure(procedureId));
    }

    // ── Sidebar Counts ──

    @Test
    @Order(90)
    void countOpenByStation() {
        int count = procedureRepo.countOpenByStation(station.id());
        assertTrue(count >= 2);
    }

    @Test
    @Order(91)
    void countOpenByAssigneeWithAvailableItems() {
        // member is assigned to procedureId which is OPEN and has public+user_assigned unchecked items
        // First restore item1 to public + user_assigned
        procedureRepo.updateItem(itemId1, "Check exits", "Desc", true, true, 1);
        int count = procedureRepo.countOpenByAssigneeWithAvailableItems(station.id(), member.id());
        assertTrue(count >= 1);
    }

    @Test
    @Order(92)
    void countOpenByAssigneeNoAssignment() {
        // member2 is not assigned to any procedure
        int count = procedureRepo.countOpenByAssigneeWithAvailableItems(station.id(), member2.id());
        assertEquals(0, count);
    }

    // ── Delete ──

    @Test
    @Order(100)
    void deleteItem() {
        assertTrue(procedureRepo.deleteItem(itemId3));
        assertEquals(2, procedureRepo.findItems(procedureId).size());
    }

    @Test
    @Order(101)
    void deleteItemNotFound() {
        assertFalse(procedureRepo.deleteItem(99999));
    }

    @Test
    @Order(102)
    void deleteTemplateItem() {
        // Create a temporary template item and delete it
        var temp = procedureRepo.createTemplateItem(templateId, "Temp", "Desc", true, true, 99);
        assertTrue(procedureRepo.deleteTemplateItem(temp.id()));
        assertFalse(procedureRepo.deleteTemplateItem(temp.id()));
    }

    @Test
    @Order(110)
    void deleteProcedure() {
        assertTrue(procedureRepo.deleteProcedure(adHocProcedureId));
        assertTrue(procedureRepo.findProcedureById(adHocProcedureId).isEmpty());
    }

    @Test
    @Order(111)
    void deleteProcedureNotFound() {
        assertFalse(procedureRepo.deleteProcedure(99999));
    }
}

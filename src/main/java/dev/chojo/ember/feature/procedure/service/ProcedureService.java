/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.event.events.ProcedureReopened;
import dev.chojo.ember.event.events.ProcedureResolved;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ProcedureService {
    private final ProcedureRepository repository;
    private final DomainEventBus eventBus;

    @Inject
    public ProcedureService(ProcedureRepository repository, DomainEventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    // ── Templates ──

    public List<ProcedureTemplate> findTemplatesByStation(int stationId, boolean includeArchived) {
        return repository.findTemplatesByStation(stationId, includeArchived);
    }

    public Optional<ProcedureTemplate> findTemplateById(int id) {
        return repository.findTemplateById(id);
    }

    public ProcedureTemplate createTemplate(int stationId, String name, String description, int createdBy) {
        return repository.createTemplate(stationId, name, description, createdBy);
    }

    public Optional<ProcedureTemplate> updateTemplate(int id, String name, String description) {
        if (repository.updateTemplate(id, name, description)) {
            return repository.findTemplateById(id);
        }
        return Optional.empty();
    }

    public boolean archiveTemplate(int id) {
        return repository.archiveTemplate(id);
    }

    public List<ProcedureTemplateItem> findTemplateItems(int templateId) {
        return repository.findTemplateItems(templateId);
    }

    public ProcedureTemplateItem createTemplateItem(
            int templateId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return repository.createTemplateItem(templateId, title, description, isPublic, userAssigned, position);
    }

    public boolean updateTemplateItem(
            int id, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return repository.updateTemplateItem(id, title, description, isPublic, userAssigned, position);
    }

    public boolean deleteTemplateItem(int id) {
        return repository.deleteTemplateItem(id);
    }

    public List<int[]> findTemplateItemDependencies(int templateId) {
        return repository.findTemplateItemDependencies(templateId);
    }

    public void setTemplateItemDependencies(int templateId, List<int[]> dependencies) {
        repository.setTemplateItemDependencies(templateId, dependencies);
    }

    // ── Procedures ──

    public List<Procedure> findProceduresByStation(int stationId, ProcedureStatus status) {
        return repository.findProceduresByStation(stationId, status);
    }

    public List<Procedure> findProceduresByAssignee(
            int stationId, int memberId, ProcedureStatus status, boolean publicOnly) {
        return repository.findProceduresByAssignee(stationId, memberId, status, publicOnly);
    }

    public Optional<Procedure> findProcedureById(int id) {
        return repository.findProcedureById(id);
    }

    public Procedure createProcedure(
            int stationId,
            Integer templateId,
            String name,
            String description,
            boolean isPublic,
            int assignedBy,
            Instant dueAt,
            List<Integer> assigneeIds) {
        var procedure =
                repository.createProcedure(stationId, templateId, name, description, isPublic, assignedBy, dueAt);

        // Snapshot template items if created from template
        if (templateId != null) {
            snapshotTemplate(procedure.id(), templateId);
        }

        // Add assignees
        for (int memberId : assigneeIds) {
            repository.addAssignee(procedure.id(), memberId);
        }

        if (!assigneeIds.isEmpty()) {
            eventBus.publish(new ProcedureAssigned(stationId, procedure.id(), name, assigneeIds, assignedBy));
        }

        return procedure;
    }

    private void snapshotTemplate(int procedureId, int templateId) {
        var templateItems = repository.findTemplateItems(templateId);
        var templateDeps = repository.findTemplateItemDependencies(templateId);

        // Map old template item IDs to new procedure item IDs
        Map<Integer, Integer> idMapping = new HashMap<>();
        for (ProcedureTemplateItem item : templateItems) {
            ProcedureItem created = repository.snapshotTemplateItem(procedureId, item);
            idMapping.put(item.id(), created.id());
        }

        // Recreate dependencies with new IDs
        for (int[] dep : templateDeps) {
            Integer newItemId = idMapping.get(dep[0]);
            Integer newDependsOnId = idMapping.get(dep[1]);
            if (newItemId != null && newDependsOnId != null) {
                repository.addItemDependency(newItemId, newDependsOnId);
            }
        }
    }

    public boolean updateProcedure(int id, String name, String description, boolean isPublic, Instant dueAt) {
        return repository.updateProcedure(id, name, description, isPublic, dueAt);
    }

    public boolean resolveProcedure(int id, int resolvedByMemberId) {
        var procedure = repository.findProcedureById(id);
        if (procedure.isEmpty()) return false;
        if (!repository.resolveProcedure(id)) return false;

        var assigneeIds = repository.findAssigneeIds(id);
        eventBus.publish(new ProcedureResolved(
                procedure.get().stationId(), id, procedure.get().name(), assigneeIds, resolvedByMemberId));
        return true;
    }

    public boolean reopenProcedure(int id, int reopenedByMemberId) {
        var procedure = repository.findProcedureById(id);
        if (procedure.isEmpty()) return false;
        if (!repository.reopenProcedure(id)) return false;

        var assigneeIds = repository.findAssigneeIds(id);
        eventBus.publish(new ProcedureReopened(
                procedure.get().stationId(), id, procedure.get().name(), assigneeIds, reopenedByMemberId));
        return true;
    }

    public boolean deleteProcedure(int id) {
        return repository.deleteProcedure(id);
    }

    // ── Assignees ──

    public List<Integer> findAssigneeIds(int procedureId) {
        return repository.findAssigneeIds(procedureId);
    }

    public void addAssignees(int procedureId, List<Integer> memberIds, int assignedByMemberId) {
        var procedure = repository.findProcedureById(procedureId);
        if (procedure.isEmpty()) return;

        var existing = Set.copyOf(repository.findAssigneeIds(procedureId));
        var newIds = memberIds.stream().filter(id -> !existing.contains(id)).toList();
        for (int memberId : newIds) {
            repository.addAssignee(procedureId, memberId);
        }

        if (!newIds.isEmpty()) {
            eventBus.publish(new ProcedureAssigned(
                    procedure.get().stationId(), procedureId, procedure.get().name(), newIds, assignedByMemberId));
        }
    }

    public boolean removeAssignee(int procedureId, int memberId) {
        return repository.removeAssignee(procedureId, memberId);
    }

    // ── Items ──

    public Optional<ProcedureItem> findItemById(int itemId) {
        return repository.findItemById(itemId);
    }

    public List<ProcedureItem> findItems(int procedureId) {
        return repository.findItems(procedureId);
    }

    public ProcedureItem createItem(
            int procedureId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return repository.createItem(procedureId, title, description, isPublic, userAssigned, position);
    }

    public boolean updateItem(
            int id, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return repository.updateItem(id, title, description, isPublic, userAssigned, position);
    }

    public boolean deleteItem(int id) {
        return repository.deleteItem(id);
    }

    public boolean checkItem(int itemId, int checkedByMemberId) {
        var item = repository.findItemById(itemId);
        if (item.isEmpty()) return false;

        // Validate dependencies are met
        var deps = repository.findItemDependencies(item.get().procedureId());
        var allItems = repository.findItems(item.get().procedureId());
        var checkedIds = allItems.stream()
                .filter(ProcedureItem::checked)
                .map(ProcedureItem::id)
                .collect(Collectors.toSet());

        for (int[] dep : deps) {
            if (dep[0] == itemId && !checkedIds.contains(dep[1])) {
                return false; // Dependency not met
            }
        }

        if (!repository.checkItem(itemId, checkedByMemberId)) return false;

        var procedure = repository.findProcedureById(item.get().procedureId());
        if (procedure.isPresent()) {
            var assigneeIds = repository.findAssigneeIds(procedure.get().id());
            eventBus.publish(new ProcedureItemChecked(
                    procedure.get().stationId(),
                    procedure.get().id(),
                    procedure.get().name(),
                    itemId,
                    item.get().title(),
                    assigneeIds,
                    checkedByMemberId));
        }
        return true;
    }

    public boolean uncheckItem(int itemId) {
        return repository.uncheckItem(itemId);
    }

    public boolean updateItemNote(int itemId, String note) {
        return repository.updateItemNote(itemId, note);
    }

    public List<int[]> findItemDependencies(int procedureId) {
        return repository.findItemDependencies(procedureId);
    }

    public void setItemDependencies(int procedureId, List<int[]> dependencies) {
        repository.setItemDependencies(procedureId, dependencies);
    }

    // ── Sidebar Counts ──

    public int countOpenByAssigneeWithAvailableItems(int stationId, int memberId) {
        return repository.countOpenByAssigneeWithAvailableItems(stationId, memberId);
    }

    public int countOpenByStation(int stationId) {
        return repository.countOpenByStation(stationId);
    }
}

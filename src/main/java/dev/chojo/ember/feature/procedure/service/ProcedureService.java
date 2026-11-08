/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
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
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ProcedureService {
    private static final Logger log = LoggerFactory.getLogger(ProcedureService.class);

    private final ProcedureRepository repository;
    private final DomainEventBus eventBus;
    private final RestrictionService restrictions;

    @Inject
    public ProcedureService(ProcedureRepository repository, DomainEventBus eventBus, RestrictionService restrictions) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.restrictions = restrictions;
    }

    public List<ProcedureTemplate> findTemplatesByStation(int stationId, boolean includeArchived) {
        return repository.findTemplatesByStation(stationId, includeArchived);
    }

    public Optional<ProcedureTemplate> findTemplateById(int id) {
        return repository.findTemplateById(id);
    }

    public ProcedureTemplate createTemplate(int stationId, String name, @Nullable String description, int createdBy) {
        var template = repository.createTemplate(stationId, name, description, createdBy);
        log.info("Created procedure template {} on station {} by member {}", template.id(), stationId, createdBy);
        return template;
    }

    public Optional<ProcedureTemplate> updateTemplate(int id, String name, @Nullable String description) {
        if (repository.updateTemplate(id, name, description)) {
            log.info("Updated procedure template {}", id);
            return repository.findTemplateById(id);
        }
        log.warn("Update for procedure template {} affected zero rows", id);
        return Optional.empty();
    }

    public boolean archiveTemplate(int id) {
        boolean archived = repository.archiveTemplate(id);
        if (archived) {
            log.info("Archived procedure template {}", id);
        } else {
            log.warn("Archive for procedure template {} affected zero rows", id);
        }
        return archived;
    }

    public List<ProcedureTemplateItem> findTemplateItems(int templateId) {
        return repository.findTemplateItems(templateId);
    }

    public ProcedureTemplateItem createTemplateItem(
            int templateId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position) {
        var item = repository.createTemplateItem(templateId, title, description, isPublic, userAssigned, position);
        log.info("Created procedure template item {} on template {}", item.id(), templateId);
        return item;
    }

    public boolean updateTemplateItem(
            int id, String title, @Nullable String description, boolean isPublic, boolean userAssigned, int position) {
        boolean updated = repository.updateTemplateItem(id, title, description, isPublic, userAssigned, position);
        if (updated) {
            log.info("Updated procedure template item {}", id);
        } else {
            log.warn("Update for procedure template item {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteTemplateItem(int id) {
        boolean deleted = repository.deleteTemplateItem(id);
        if (deleted) {
            log.info("Deleted procedure template item {}", id);
        } else {
            log.warn("Delete for procedure template item {} affected zero rows", id);
        }
        return deleted;
    }

    /**
     * One step of one template.
     *
     * @param templateId the template the step has to belong to
     * @param itemId     the step
     * @return the step, or empty when the template holds no step by that id
     */
    public Optional<ProcedureTemplateItem> findTemplateItemIn(int templateId, int itemId) {
        return repository.findTemplateItems(templateId).stream()
                .filter(item -> item.id() == itemId)
                .findFirst();
    }

    public List<int[]> findTemplateItemDependencies(int templateId) {
        return repository.findTemplateItemDependencies(templateId);
    }

    /**
     * Replaces the dependencies between the steps of one template.
     *
     * @param templateId   the template
     * @param dependencies pairs of step ids: the step first, the step it waits for second
     * @throws RefusalResponse {@link ProcedureRefusal#PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE} when a pair names a step
     *                         of another template
     */
    public void setTemplateItemDependencies(int templateId, List<int[]> dependencies) {
        var held = repository.findTemplateItems(templateId).stream()
                .map(ProcedureTemplateItem::id)
                .collect(Collectors.toSet());
        if (!namesOnly(held, dependencies)) throw ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE.raise();
        repository.setTemplateItemDependencies(templateId, dependencies);
        log.info("Procedure template {} now carries {} item dependency(s)", templateId, dependencies.size());
    }

    public List<Procedure> findProceduresByStation(int stationId, @Nullable ProcedureStatus status) {
        return repository.findProceduresByStation(stationId, status);
    }

    /**
     * The procedures whose managing party takes the member in, as "Meine" lists them.
     *
     * @param stationId  the station
     * @param memberId   the member
     * @param status     the state asked for, or null for both
     * @param publicOnly whether to leave out procedures that are not public
     * @return the procedures, newest first
     */
    public List<Procedure> findProceduresRunBy(
            int stationId, int memberId, @Nullable ProcedureStatus status, boolean publicOnly) {
        return repository.findProceduresRunBy(stationId, memberId, status, publicOnly);
    }

    public Optional<Procedure> findProcedureById(int id) {
        return repository.findProcedureById(id);
    }

    /**
     * Every procedure prepared for one date of one appointment.
     *
     * @param stationId the station the appointment belongs to
     * @param eventId   the appointment
     * @param eventDate the one occurrence of it
     * @return what is already there for that date, newest first
     */
    public List<Procedure> findProceduresByOccurrence(int stationId, int eventId, LocalDate eventDate) {
        return repository.findProceduresByOccurrence(stationId, eventId, eventDate);
    }

    /**
     * Writes a procedure, snapshots the template it was started from and gives it its managing party:
     * the one asked for, else the template's, else none. The members the party names one by one are
     * told it was handed to them.
     *
     * @param eventId   the appointment this was prepared for, or {@code null} for a procedure that
     *                  stands on its own
     * @param eventDate the one occurrence of that appointment, {@code null} exactly when the
     *                  appointment is
     */
    public Procedure createProcedure(
            int stationId,
            @Nullable Integer templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            int assignedBy,
            @Nullable Instant dueAt,
            @Nullable RestrictionAudience party,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate) {
        var given = ofStation(
                stationId,
                party != null ? party : templateId != null ? templatePartyOf(templateId) : RestrictionAudience.empty());
        var procedure = repository.createProcedure(
                stationId, templateId, name, description, isPublic, assignedBy, dueAt, eventId, eventDate);

        if (templateId != null) {
            snapshotTemplate(procedure.id(), templateId);
        }

        restrictions.setAudience(RestrictionType.PROCEDURE, procedure.id(), given);
        var named = given.toSelection().memberIds();
        if (!named.isEmpty()) {
            eventBus.publish(new ProcedureAssigned(stationId, procedure.id(), name, named, assignedBy));
        }

        log.info(
                "Created procedure {} on station {} (template {}, party naming nobody: {})",
                procedure.id(),
                stationId,
                templateId,
                given.namesNobody());
        return procedure;
    }

    public boolean updateProcedure(
            int id, String name, @Nullable String description, boolean isPublic, @Nullable Instant dueAt) {
        boolean updated = repository.updateProcedure(id, name, description, isPublic, dueAt);
        if (updated) {
            log.info("Updated procedure {}", id);
        } else {
            log.warn("Update for procedure {} affected zero rows", id);
        }
        return updated;
    }

    public boolean resolveProcedure(int id, int resolvedByMemberId) {
        var procedure = repository.findProcedureById(id);
        if (procedure.isEmpty()) {
            log.warn("Resolve for procedure {} skipped: not found", id);
            return false;
        }
        if (!repository.resolveProcedure(id)) {
            log.warn("Resolve for procedure {} affected zero rows", id);
            return false;
        }

        var party = toldOf(procedure.get());
        eventBus.publish(new ProcedureResolved(
                procedure.get().stationId(), id, procedure.get().name(), party, resolvedByMemberId));
        log.info("Resolved procedure {} by member {}", id, resolvedByMemberId);
        return true;
    }

    public boolean reopenProcedure(int id, int reopenedByMemberId) {
        var procedure = repository.findProcedureById(id);
        if (procedure.isEmpty()) {
            log.warn("Reopen for procedure {} skipped: not found", id);
            return false;
        }
        if (!repository.reopenProcedure(id)) {
            log.warn("Reopen for procedure {} affected zero rows", id);
            return false;
        }

        var party = toldOf(procedure.get());
        eventBus.publish(new ProcedureReopened(
                procedure.get().stationId(), id, procedure.get().name(), party, reopenedByMemberId));
        log.info("Reopened procedure {} by member {}", id, reopenedByMemberId);
        return true;
    }

    public boolean deleteProcedure(int id) {
        boolean deleted = repository.deleteProcedure(id);
        if (deleted) {
            log.info("Deleted procedure {}", id);
        } else {
            log.warn("Delete for procedure {} affected zero rows", id);
        }
        return deleted;
    }

    /**
     * The managing party of a procedure: who sees its public steps, ticks the steps marked for them and
     * is told about it.
     *
     * @param procedureId the procedure
     * @return the party; one naming nobody where the procedure has none
     */
    public RestrictionAudience partyOf(int procedureId) {
        return restrictions.audienceOf(RestrictionType.PROCEDURE, procedureId);
    }

    /**
     * Whether the managing party of a procedure takes a member in. Unlike other restrictions, a
     * procedure with no party is run by nobody beyond those who edit procedures.
     *
     * @param procedureId the procedure
     * @param memberId    the member
     * @return whether the member belongs to the party
     */
    public boolean runBy(int procedureId, int memberId) {
        return restrictions.hasRestrictions(RestrictionType.PROCEDURE, procedureId)
                && restrictions.includes(RestrictionType.PROCEDURE, procedureId, memberId);
    }

    /**
     * The current members of the station the managing party of a procedure takes in, none where it has
     * no party.
     *
     * @param stationId   the station
     * @param procedureId the procedure
     * @return the members' ids
     */
    public List<Integer> partyMembers(int stationId, int procedureId) {
        var party = partyOf(procedureId);
        if (party.namesNobody()) return List.of();
        return restrictions.membersIn(stationId, party);
    }

    /**
     * Gives a procedure another managing party. The members it names one by one for the first time are
     * told it was handed to them.
     *
     * @param procedureId        the procedure
     * @param party              the new party; one naming nobody leaves it to those who edit procedures
     * @param assignedByMemberId who changed it
     */
    public void setParty(int procedureId, RestrictionAudience party, int assignedByMemberId) {
        var procedure = repository.findProcedureById(procedureId);
        if (procedure.isEmpty()) {
            log.warn("Party of procedure {} not changed: not found", procedureId);
            return;
        }
        var checked = ofStation(procedure.get().stationId(), party);
        var before = Set.copyOf(partyOf(procedureId).toSelection().memberIds());
        restrictions.setAudience(RestrictionType.PROCEDURE, procedureId, checked);
        var newlyNamed = checked.toSelection().memberIds().stream()
                .filter(memberId -> !before.contains(memberId))
                .toList();
        if (!newlyNamed.isEmpty()) {
            eventBus.publish(new ProcedureAssigned(
                    procedure.get().stationId(), procedureId, procedure.get().name(), newlyNamed, assignedByMemberId));
        }
        log.info("Changed the party of procedure {}, {} member(s) newly named", procedureId, newlyNamed.size());
    }

    /**
     * Who of the party is told about a procedure: its members, but only where the procedure is public,
     * since a member of the party who does not edit procedures may not open one that is not.
     */
    private List<Integer> toldOf(Procedure procedure) {
        return procedure.isPublic() ? partyMembers(procedure.stationId(), procedure.id()) : List.of();
    }

    /**
     * A party as it is stored: each part once, refused where it names a group, tag or member of another
     * station.
     */
    private RestrictionAudience ofStation(int stationId, RestrictionAudience party) {
        var selection = party.toSelection();
        var distinct = new RestrictionAudience(
                selection.userTypes().stream().distinct().toList(),
                selection.groupIds().stream().distinct().toList(),
                selection.tagIds().stream().distinct().toList(),
                selection.memberIds().stream().distinct().toList(),
                party.mode());
        if (!restrictions.ofStation(stationId, distinct)) throw ProcedureRefusal.PROCEDURE_PARTY_NOT_OF_STATION.raise();
        return distinct;
    }

    /**
     * Refuses a party naming a group, tag or member that is not of the station, before anything is written.
     *
     * @param stationId the station
     * @param party     the party
     */
    public void requirePartyOf(int stationId, RestrictionAudience party) {
        ofStation(stationId, party);
    }

    /**
     * The managing party a procedure template hands to every procedure started from it.
     *
     * @param templateId the template
     * @return the party; one naming nobody where the template has none
     */
    public RestrictionAudience templatePartyOf(int templateId) {
        return restrictions.audienceOf(RestrictionType.PROCEDURE_TEMPLATE, templateId);
    }

    /**
     * Sets the managing party a procedure template hands to every procedure started from it.
     *
     * @param templateId the template
     * @param party      the party; one naming nobody hands none
     */
    public void setTemplateParty(int templateId, RestrictionAudience party) {
        var template = repository.findTemplateById(templateId);
        if (template.isEmpty()) return;
        restrictions.setAudience(
                RestrictionType.PROCEDURE_TEMPLATE,
                templateId,
                ofStation(template.get().stationId(), party));
        log.info("Changed the default party of procedure template {}", templateId);
    }

    /**
     * One step of one procedure.
     *
     * @param procedureId the procedure the step has to belong to
     * @param itemId      the step
     * @return the step, or empty when the procedure holds no step by that id
     */
    public Optional<ProcedureItem> findItemIn(int procedureId, int itemId) {
        return repository.findItemById(itemId).filter(item -> item.procedureId() == procedureId);
    }

    public List<ProcedureItem> findItems(int procedureId) {
        return repository.findItems(procedureId);
    }

    public ProcedureItem createItem(
            int procedureId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position) {
        var item = repository.createItem(procedureId, title, description, isPublic, userAssigned, position);
        log.info("Created procedure item {} on procedure {}", item.id(), procedureId);
        return item;
    }

    public boolean updateItem(
            int id, String title, @Nullable String description, boolean isPublic, boolean userAssigned, int position) {
        boolean updated = repository.updateItem(id, title, description, isPublic, userAssigned, position);
        if (updated) {
            log.info("Updated procedure item {}", id);
        } else {
            log.warn("Update for procedure item {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteItem(int id) {
        boolean deleted = repository.deleteItem(id);
        if (deleted) {
            log.info("Deleted procedure item {}", id);
        } else {
            log.warn("Delete for procedure item {} affected zero rows", id);
        }
        return deleted;
    }

    public boolean checkItem(int itemId, int checkedByMemberId) {
        var item = repository.findItemById(itemId);
        if (item.isEmpty()) {
            log.warn("Check for procedure item {} skipped: not found", itemId);
            return false;
        }

        var deps = repository.findItemDependencies(item.get().procedureId());
        var allItems = repository.findItems(item.get().procedureId());
        var checkedIds = allItems.stream()
                .filter(ProcedureItem::checked)
                .map(ProcedureItem::id)
                .collect(Collectors.toSet());

        for (int[] dep : deps) {
            if (dep[0] == itemId && !checkedIds.contains(dep[1])) {
                log.warn("Check for procedure item {} blocked: dependency {} not yet checked", itemId, dep[1]);
                return false;
            }
        }

        if (!repository.checkItem(itemId, checkedByMemberId)) {
            log.warn("Check for procedure item {} affected zero rows", itemId);
            return false;
        }

        var procedure = repository.findProcedureById(item.get().procedureId());
        if (procedure.isPresent()) {
            var party = item.get().isPublic() ? toldOf(procedure.get()) : List.<Integer>of();
            eventBus.publish(new ProcedureItemChecked(
                    procedure.get().stationId(),
                    procedure.get().id(),
                    procedure.get().name(),
                    itemId,
                    item.get().title(),
                    party,
                    checkedByMemberId));
        }
        log.info("Checked procedure item {} by member {}", itemId, checkedByMemberId);
        return true;
    }

    public boolean uncheckItem(int itemId) {
        boolean unchecked = repository.uncheckItem(itemId);
        if (unchecked) {
            log.info("Unchecked procedure item {}", itemId);
        } else {
            log.warn("Uncheck for procedure item {} affected zero rows", itemId);
        }
        return unchecked;
    }

    public boolean updateItemNote(int itemId, String note) {
        boolean updated = repository.updateItemNote(itemId, note);
        if (updated) {
            log.info("Updated procedure item note {}", itemId);
        } else {
            log.warn("Update note for procedure item {} affected zero rows", itemId);
        }
        return updated;
    }

    public List<int[]> findItemDependencies(int procedureId) {
        return repository.findItemDependencies(procedureId);
    }

    /**
     * Replaces the dependencies between the steps of one procedure.
     *
     * @param procedureId  the procedure
     * @param dependencies pairs of step ids: the step first, the step it waits for second
     * @throws RefusalResponse {@link ProcedureRefusal#PROCEDURE_STEP_NOT_IN_PROCEDURE} when a pair names a step of
     *                         another procedure
     */
    public void setItemDependencies(int procedureId, List<int[]> dependencies) {
        var held = repository.findItems(procedureId).stream()
                .map(ProcedureItem::id)
                .collect(Collectors.toSet());
        if (!namesOnly(held, dependencies)) throw ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE.raise();
        repository.setItemDependencies(procedureId, dependencies);
        log.info("Procedure {} now carries {} item dependency(s)", procedureId, dependencies.size());
    }

    private static boolean namesOnly(Set<Integer> held, List<int[]> dependencies) {
        return dependencies.stream().allMatch(pair -> held.contains(pair[0]) && held.contains(pair[1]));
    }

    public int countOpenRunByWithAvailableItems(int stationId, int memberId) {
        return repository.countOpenRunByWithAvailableItems(stationId, memberId);
    }

    public int countOpenByStation(int stationId) {
        return repository.countOpenByStation(stationId);
    }

    private void snapshotTemplate(int procedureId, int templateId) {
        var templateItems = repository.findTemplateItems(templateId);
        var templateDeps = repository.findTemplateItemDependencies(templateId);

        Map<Integer, Integer> procedureItemIdByTemplateItemId = new HashMap<>();
        for (ProcedureTemplateItem item : templateItems) {
            ProcedureItem created = repository.snapshotTemplateItem(procedureId, item);
            procedureItemIdByTemplateItemId.put(item.id(), created.id());
        }

        for (int[] dep : templateDeps) {
            Integer newItemId = procedureItemIdByTemplateItemId.get(dep[0]);
            Integer newDependsOnId = procedureItemIdByTemplateItemId.get(dep[1]);
            if (newItemId != null && newDependsOnId != null) {
                repository.addItemDependency(newItemId, newDependsOnId);
            }
        }
    }
}

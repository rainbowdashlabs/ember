/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point to the event domain for routes and neighbouring features. Every call is served by one
 * of the focused services this facade composes, which is where the behaviour and the parameter
 * documentation live: {@link EventCrudService}, {@link EventOccurrenceService},
 * {@link EventCategoryService}, {@link EventBreakService}, {@link EventRestrictionService},
 * {@link EventFieldDefaultService}, {@link EventRegistrationService} and
 * {@link EventReminderService}.
 */
@Singleton
public class EventService {
    private final EventCrudService crudService;
    private final EventOccurrenceService occurrenceService;
    private final EventCategoryService categoryService;
    private final EventBreakService breakService;
    private final EventRestrictionService restrictionService;
    private final EventFieldDefaultService fieldDefaultService;
    private final EventRegistrationService registrationService;
    private final EventReminderService reminderService;

    @Inject
    public EventService(
            EventCrudService crudService,
            EventOccurrenceService occurrenceService,
            EventCategoryService categoryService,
            EventBreakService breakService,
            EventRestrictionService restrictionService,
            EventFieldDefaultService fieldDefaultService,
            EventRegistrationService registrationService,
            EventReminderService reminderService) {
        this.crudService = crudService;
        this.occurrenceService = occurrenceService;
        this.categoryService = categoryService;
        this.breakService = breakService;
        this.restrictionService = restrictionService;
        this.fieldDefaultService = fieldDefaultService;
        this.registrationService = registrationService;
        this.reminderService = reminderService;
    }

    /**
     * Retrieves all events for a station — see {@link EventCrudService#findByStation}.
     */
    public List<StationEvent> findByStation(int stationId) {
        return crudService.findByStation(stationId);
    }

    /**
     * Searches the public events of a station for the event picker cells — see
     * {@link EventCrudService#searchEventPicker}.
     */
    public List<EventRepository.PickerEvent> searchEventPicker(
            int stationId, String search, EventRepository.PickerMode mode, int limit) {
        return crudService.searchEventPicker(stationId, search, mode, limit);
    }

    /**
     * Bulk-resolves the public UUIDs for a set of event ids — see
     * {@link EventCrudService#findPublicUidsByIds}.
     */
    public Map<Integer, UUID> findPublicUidsByIds(int stationId, Collection<Integer> ids) {
        return crudService.findPublicUidsByIds(stationId, ids);
    }

    /**
     * Resolves a single station event by its public UUID — see {@link EventCrudService#findByPublicUid}.
     */
    public Optional<StationEvent> findByPublicUid(int stationId, UUID publicUid) {
        return crudService.findByPublicUid(stationId, publicUid);
    }

    /**
     * Retrieves the events of a station a member is allowed to see — see
     * {@link EventCrudService#findByStationForMember}.
     */
    public List<StationEvent> findByStationForMember(int stationId, int memberId) {
        return crudService.findByStationForMember(stationId, memberId);
    }

    /**
     * Filters a station's events by member, category and registration requirement — see
     * {@link EventCrudService#findFiltered}.
     */
    public List<StationEvent> findFiltered(
            int stationId, Integer memberId, Integer categoryId, Boolean requiresRegistration) {
        return crudService.findFiltered(stationId, memberId, categoryId, requiresRegistration);
    }

    /**
     * Unions the filtered events visible to any of the given members — see
     * {@link EventCrudService#findFilteredForMembers}.
     */
    public List<StationEvent> findFilteredForMembers(
            int stationId, List<Integer> memberIds, Integer categoryId, Boolean requiresRegistration) {
        return crudService.findFilteredForMembers(stationId, memberIds, categoryId, requiresRegistration);
    }

    /**
     * Finds a station event by its ID — see {@link EventCrudService#findById}.
     */
    public Optional<StationEvent> findById(int id) {
        return crudService.findById(id);
    }

    /**
     * Creates a new station event — see {@link EventCrudService#create}.
     */
    public StationEvent create(
            int stationId,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {
        return crudService.create(
                stationId,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                templateId,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                categoryId,
                registrationLimit,
                minRegistrations,
                thresholdDate,
                registrationCloseDays);
    }

    /**
     * Persists a new event without publishing any domain event — see
     * {@link EventCrudService#createWithoutEvent}.
     */
    public StationEvent createWithoutEvent(
            int stationId,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {
        return crudService.createWithoutEvent(
                stationId,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                templateId,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                categoryId,
                registrationLimit,
                minRegistrations,
                thresholdDate,
                registrationCloseDays);
    }

    /**
     * Updates a station event and returns the refreshed entity — see {@link EventCrudService#update}.
     */
    public Optional<StationEvent> update(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId,
            Boolean isPublic,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {
        return crudService.update(
                id,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                templateId,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                categoryId,
                isPublic,
                registrationLimit,
                minRegistrations,
                thresholdDate,
                registrationCloseDays);
    }

    /**
     * Deletes a station event by ID — see {@link EventCrudService#delete}.
     */
    public boolean delete(int id) {
        return crudService.delete(id);
    }

    /**
     * Cancels an event, notifying all registered members — see {@link EventCrudService#cancelEvent}.
     */
    public boolean cancelEvent(int stationId, int eventId, String reason) {
        return crudService.cancelEvent(stationId, eventId, reason);
    }

    /**
     * Returns the most recent station-event modification time — see
     * {@link EventCrudService#findMaxEventUpdatedAt}.
     */
    public Instant findMaxEventUpdatedAt(int stationId) {
        return crudService.findMaxEventUpdatedAt(stationId);
    }

    /**
     * Finds all events that occur today for a station — see
     * {@link EventOccurrenceService#findTodayEvents}.
     */
    public List<StationEvent> findTodayEvents(int stationId) {
        return occurrenceService.findTodayEvents(stationId);
    }

    /**
     * Expands events into chronologically sorted date occurrences — see
     * {@link EventOccurrenceService#findUpcomingOccurrences}.
     */
    public List<UpcomingEventOccurrence> findUpcomingOccurrences(
            int stationId,
            List<Integer> memberIds,
            Integer categoryId,
            Boolean requiresRegistration,
            String search,
            int limit,
            int offset) {
        return occurrenceService.findUpcomingOccurrences(
                stationId, memberIds, categoryId, requiresRegistration, search, limit, offset);
    }

    /**
     * Finds an event category by its ID — see {@link EventCategoryService#findById}.
     */
    public Optional<EventCategory> findCategoryById(int id) {
        return categoryService.findById(id);
    }

    /**
     * Retrieves all event categories for a station — see {@link EventCategoryService#findByStation}.
     */
    public List<EventCategory> findCategoriesByStation(int stationId) {
        return categoryService.findByStation(stationId);
    }

    /**
     * Creates a new event category — see {@link EventCategoryService#create}.
     */
    public EventCategory createCategory(int stationId, String name, int position, String color) {
        return categoryService.create(stationId, name, position, color);
    }

    /**
     * Updates an event category — see {@link EventCategoryService#update}.
     */
    public boolean updateCategory(
            int id, String name, int position, Integer maxShownEvents, boolean isPublic, String color) {
        return categoryService.update(id, name, position, maxShownEvents, isPublic, color);
    }

    /**
     * Deletes an event category by ID — see {@link EventCategoryService#delete}.
     */
    public boolean deleteCategory(int id) {
        return categoryService.delete(id);
    }

    /**
     * Rewrites the display order of a station's event categories — see
     * {@link EventCategoryService#reorder}.
     */
    public void reorderCategories(int stationId, List<Integer> orderedIds) {
        categoryService.reorder(stationId, orderedIds);
    }

    /**
     * Retrieves all event breaks for a station — see {@link EventBreakService#findByStation}.
     */
    public List<EventBreak> findBreaksByStation(int stationId) {
        return breakService.findByStation(stationId);
    }

    /**
     * Finds an event break by its ID — see {@link EventBreakService#findById}.
     */
    public Optional<EventBreak> findBreakById(int id) {
        return breakService.findById(id);
    }

    /**
     * Creates a new event break — see {@link EventBreakService#create}.
     */
    public EventBreak createBreak(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        return breakService.create(stationId, name, startDate, endDate);
    }

    /**
     * Updates an event break and returns the refreshed entity — see {@link EventBreakService#update}.
     */
    public Optional<EventBreak> updateBreak(int id, String name, LocalDate startDate, LocalDate endDate) {
        return breakService.update(id, name, startDate, endDate);
    }

    /**
     * Deletes an event break by ID — see {@link EventBreakService#delete}.
     */
    public boolean deleteBreak(int id) {
        return breakService.delete(id);
    }

    /**
     * Retrieves the restriction set for an event — see {@link EventRestrictionService#findRestrictions}.
     */
    public RestrictionSet findRestrictions(int eventId) {
        return restrictionService.findRestrictions(eventId);
    }

    /**
     * Replaces all restrictions of an event — see {@link EventRestrictionService#setRestrictions}.
     */
    public void setRestrictions(int eventId, RestrictionSelection selection) {
        restrictionService.setRestrictions(eventId, selection);
    }

    /**
     * Updates the restriction mode for an event — see
     * {@link EventRestrictionService#updateRestrictionMode}.
     */
    public void updateRestrictionMode(int eventId, RestrictionMode mode) {
        restrictionService.updateRestrictionMode(eventId, mode);
    }

    /**
     * Checks if a member is eligible for an event — see
     * {@link EventRestrictionService#isMemberEligible}.
     */
    public boolean isMemberEligible(int eventId, int memberId, Set<StationPermission> memberPermissions) {
        return restrictionService.isMemberEligible(eventId, memberId, memberPermissions);
    }

    /**
     * Retrieves all field default configurations for an event — see
     * {@link EventFieldDefaultService#findByEvent}.
     */
    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return fieldDefaultService.findByEvent(eventId);
    }

    /**
     * Replaces all field defaults for an event — see {@link EventFieldDefaultService#setForEvent}.
     */
    public void setFieldDefaults(int eventId, List<EventFieldDefault> defaults) {
        fieldDefaultService.setForEvent(eventId, defaults);
    }

    /**
     * Resolves field defaults into concrete values — see {@link EventFieldDefaultService#resolve}.
     */
    public Map<Integer, String> resolveFieldDefaults(int eventId) {
        return fieldDefaultService.resolve(eventId);
    }

    /**
     * Retrieves all pending registrations for events in a station — see
     * {@link EventRegistrationService#findPendingByStation}.
     */
    public List<EventRegistration> findPendingRegistrationsByStation(int stationId) {
        return registrationService.findPendingByStation(stationId);
    }

    /**
     * Retrieves all registrations for an event occurrence — see
     * {@link EventRegistrationService#findByEventAndDate}.
     */
    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return registrationService.findByEventAndDate(eventId, eventDate);
    }

    /**
     * Retrieves all registrations for an event across all dates — see
     * {@link EventRegistrationService#findByEvent}.
     */
    public List<EventRegistration> findAllRegistrations(int eventId) {
        return registrationService.findByEvent(eventId);
    }

    /**
     * Retrieves all registrations of a member — see {@link EventRegistrationService#findByMember}.
     */
    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return registrationService.findByMember(memberId);
    }

    /**
     * Retrieves the registrations of several members in one query — see
     * {@link EventRegistrationService#findByMembers}.
     */
    public List<EventRegistration> findRegistrationsByMembers(Collection<Integer> memberIds) {
        return registrationService.findByMembers(memberIds);
    }

    /**
     * Returns the most recent registration time across the given members — see
     * {@link EventRegistrationService#findMaxCreatedAt}.
     */
    public Instant findMaxRegistrationCreatedAt(Collection<Integer> memberIds) {
        return registrationService.findMaxCreatedAt(memberIds);
    }

    /**
     * Registers a member for an event — see {@link EventRegistrationService#register}.
     */
    public EventRegistration register(
            int eventId, int memberId, LocalDate eventDate, boolean autoAccept, Integer createdBy) {
        return registrationService.register(eventId, memberId, eventDate, autoAccept, createdBy);
    }

    /**
     * Finds a registration by its ID — see {@link EventRegistrationService#findById}.
     */
    public Optional<EventRegistration> findRegistrationById(int id) {
        return registrationService.findById(id);
    }

    /**
     * Moves a registration to a new status — see {@link EventRegistrationService#updateStatus}.
     */
    public boolean updateRegistrationStatus(int id, RegistrationStatus status) {
        return registrationService.updateStatus(id, status);
    }

    /**
     * Removes a registration — see {@link EventRegistrationService#withdraw}.
     */
    public boolean withdrawRegistration(int id) {
        return registrationService.withdraw(id);
    }

    /**
     * Records that a member will not attend an occurrence — see
     * {@link EventRegistrationService#decline}.
     */
    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, Integer createdBy) {
        return registrationService.decline(eventId, memberId, eventDate, createdBy);
    }

    /**
     * Returns the per-event registration totals of a station — see
     * {@link EventRegistrationService#findCountsByStation}.
     */
    public List<RegistrationCount> findRegistrationCounts(int stationId) {
        return registrationService.findCountsByStation(stationId);
    }

    /**
     * Returns the members who declined an occurrence — see
     * {@link EventRegistrationService#findDeclinedMemberIds}.
     */
    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return registrationService.findDeclinedMemberIds(eventId, eventDate);
    }

    /**
     * Returns per-member registration statistics for an event — see
     * {@link EventRegistrationService#findStatsByEvent}.
     */
    public List<MemberRegistrationStats> findRegistrationStats(int eventId, Integer categoryId, int months) {
        return registrationService.findStatsByEvent(eventId, categoryId, months);
    }

    /**
     * Returns the configured reminder lead times of an event — see {@link EventReminderService#findDays}.
     */
    public List<Integer> findReminderDays(int eventId) {
        return reminderService.findDays(eventId);
    }

    /**
     * Replaces the reminder lead times of an event — see {@link EventReminderService#setDays}.
     */
    public void setReminders(int eventId, List<Integer> daysBefore) {
        reminderService.setDays(eventId, daysBefore);
    }
}

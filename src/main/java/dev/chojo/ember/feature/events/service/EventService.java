/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventSummary;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service providing business logic for station events, including CRUD operations for events, breaks, categories,
 * restrictions, field defaults, and registrations.
 */
@Singleton
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final EventBreakRepository breakRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventFieldDefaultRepository fieldDefaultRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventReminderRepository reminderRepository;
    private final RestrictionService restrictionService;
    private final DomainEventBus eventBus;

    @Inject
    public EventService(
            EventRepository eventRepository,
            EventBreakRepository breakRepository,
            EventCategoryRepository categoryRepository,
            EventFieldDefaultRepository fieldDefaultRepository,
            EventRegistrationRepository registrationRepository,
            EventReminderRepository reminderRepository,
            RestrictionService restrictionService,
            DomainEventBus eventBus) {
        this.eventRepository = eventRepository;
        this.breakRepository = breakRepository;
        this.categoryRepository = categoryRepository;
        this.fieldDefaultRepository = fieldDefaultRepository;
        this.registrationRepository = registrationRepository;
        this.reminderRepository = reminderRepository;
        this.restrictionService = restrictionService;
        this.eventBus = eventBus;
    }

    // -- Events --

    /**
     * Retrieves all events for a station.
     *
     * @param stationId the station ID
     * @return the list of station events
     */
    public List<StationEvent> findByStation(int stationId) {
        return eventRepository.findByStation(stationId);
    }

    /**
     * Event picker for the {@code FEATURED_EVENT} / {@code UPCOMING_EVENTS} /
     * {@code PAST_EVENT_RECAP} cells. Returns a compact picker shape filtered
     * to public events (per-event {@code public = TRUE} or category-default).
     */
    public List<EventRepository.PickerEvent> searchEventPicker(
            int stationId, String search, EventRepository.PickerMode mode, int limit) {
        return eventRepository.searchForPicker(stationId, search, mode, limit);
    }

    /**
     * Bulk-resolves the public UUIDs for a set of event ids — see
     * {@link EventRepository#findPublicUidsByIds}.
     */
    public Map<Integer, UUID> findPublicUidsByIds(int stationId, Collection<Integer> ids) {
        return eventRepository.findPublicUidsByIds(stationId, ids);
    }

    /**
     * Resolves a single station event by its public UUID — used by cell renderers.
     */
    public Optional<StationEvent> findByPublicUid(int stationId, UUID publicUid) {
        return eventRepository.findByPublicUid(stationId, publicUid);
    }

    /**
     * Retrieves events for a station that the given member is allowed to see.
     *
     * @param stationId the station ID
     * @param memberId  the requesting member ID
     * @return the filtered list of station events
     */
    public List<StationEvent> findByStationForMember(int stationId, int memberId) {
        return eventRepository.findByStationForMember(stationId, memberId);
    }

    public List<StationEvent> findFiltered(
            int stationId, Integer memberId, Integer categoryId, Boolean requiresRegistration) {
        return eventRepository.findFiltered(stationId, memberId, categoryId, requiresRegistration);
    }

    public List<StationEvent> findFilteredForMembers(
            int stationId, List<Integer> memberIds, Integer categoryId, Boolean requiresRegistration) {
        if (memberIds == null) {
            return eventRepository.findFiltered(stationId, null, categoryId, requiresRegistration);
        }
        var eventMap = new LinkedHashMap<Integer, StationEvent>();
        for (int mid : memberIds) {
            for (var ev : eventRepository.findFiltered(stationId, mid, categoryId, requiresRegistration)) {
                eventMap.putIfAbsent(ev.id(), ev);
            }
        }
        return new ArrayList<>(eventMap.values());
    }

    /**
     * Finds a station event by its ID.
     *
     * @param id the event ID
     * @return the event, if found
     */
    public Optional<StationEvent> findById(int id) {
        return eventRepository.findById(id);
    }

    /**
     * Creates a new station event.
     *
     * @param stationId            the station this event belongs to
     * @param name                 the event name
     * @param description          the event description
     * @param eventType            the recurrence type
     * @param dayOfWeek            the ISO day of week for recurring events, or null
     * @param startTime            the start time
     * @param endTime              the end time
     * @param templateId           the optional attendance template ID
     * @param requiresRegistration whether registration is required
     * @param registrationDeadline the registration deadline, or null
     * @param requiresConfirmation whether registrations require manager confirmation
     * @param categoryId           the optional category ID
     * @return the created event
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
        var event = createWithoutEvent(
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
        eventBus.publish(new EventCreated(stationId, event));
        return event;
    }

    /**
     * Persists a new event without publishing any domain event. Reserved for callers that
     * aggregate their own domain event (e.g. {@code BatchEventService} emitting
     * {@link dev.chojo.ember.event.events.EventsBatchCreated} once at the end).
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
        var event = eventRepository.create(
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
        log.info("Created event {} for station {} ({}, type={})", event.id(), stationId, name, eventType);
        return event;
    }

    /**
     * Updates a station event and returns the refreshed entity if the update was successful.
     *
     * @param id                   the event ID
     * @param name                 the new event name
     * @param description          the new description
     * @param eventType            the new recurrence type
     * @param dayOfWeek            the new day of week, or null
     * @param startTime            the new start time
     * @param endTime              the new end time
     * @param templateId           the new template ID, or null
     * @param requiresRegistration whether registration is required
     * @param registrationDeadline the new registration deadline, or null
     * @param requiresConfirmation whether registrations require confirmation
     * @param categoryId           the new category ID, or null
     * @return the updated event, or empty if not found
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
        if (eventRepository.update(
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
                registrationCloseDays)) {
            log.info("Updated event {}", id);
            return eventRepository.findById(id);
        }
        log.warn("Cannot update event: event {} not found", id);
        return Optional.empty();
    }

    /**
     * Deletes a station event by ID.
     *
     * @param id the event ID
     * @return true if the event was deleted
     */
    public boolean delete(int id) {
        var event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            log.warn("Cannot delete event: event {} not found", id);
            return false;
        }
        if (eventRepository.delete(id)) {
            log.info("Deleted event {} for station {}", id, event.stationId());
            eventBus.publish(new EventDeleted(event.stationId(), id, event.name()));
            return true;
        }
        log.warn("Failed to delete event {}", id);
        return false;
    }

    /**
     * Cancels an event, notifying all registered members.
     *
     * @param stationId the station ID (for ownership check)
     * @param eventId   the event ID
     * @param reason    optional cancellation reason
     * @return true if the event was cancelled
     */
    public boolean cancelEvent(int stationId, int eventId, String reason) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.stationId() != stationId) {
            log.warn("Cannot cancel event: event {} not found for station {}", eventId, stationId);
            return false;
        }
        if (event.cancelled()) {
            log.warn("Cannot cancel event: event {} already cancelled", eventId);
            return false;
        }

        boolean cancelled = eventRepository.cancelEvent(eventId, reason);
        if (cancelled) {
            log.info("Cancelled event {} for station {}", eventId, stationId);
            eventBus.publish(new EventCancelled(stationId, eventId, event.name(), reason));
        } else {
            log.warn("Failed to cancel event {}", eventId);
        }
        return cancelled;
    }

    /**
     * Finds all events that occur today for a station, taking into account recurrence rules and break periods.
     * One-time events match by their start date; recurring events match by day of week and recurrence pattern.
     *
     * @param stationId the station ID
     * @return the list of today's events
     */
    public List<StationEvent> findTodayEvents(int stationId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int dayOfWeek = today.getDayOfWeek().getValue();
        int dayOfMonth = today.getDayOfMonth();
        int monthValue = today.getMonthValue();
        boolean inBreak = breakRepository.isDateInBreak(stationId, today);

        return eventRepository.findByStation(stationId).stream()
                .filter(e -> {
                    if (e.eventType() == StationEvent.EventType.ONE_TIME) {
                        if (e.startTime() == null) return false;
                        LocalDate eventDateUtc =
                                e.startTime().atZone(ZoneOffset.UTC).toLocalDate();
                        return today.equals(eventDateUtc);
                    }
                    if (inBreak) return false;
                    return switch (e.eventType()) {
                        case RECURRING -> e.dayOfWeek() != null && e.dayOfWeek() == dayOfWeek;
                        case MONTHLY_FIRST -> e.dayOfWeek() != null && e.dayOfWeek() == dayOfWeek && dayOfMonth <= 7;
                        case QUARTERLY ->
                            e.dayOfWeek() != null
                                    && e.dayOfWeek() == dayOfWeek
                                    && dayOfMonth <= 7
                                    && (monthValue - 1) % 3 == 0;
                        case YEARLY ->
                            e.startTime() != null
                                    && e.startTime().atZone(ZoneOffset.UTC).getMonthValue() == monthValue
                                    && e.startTime().atZone(ZoneOffset.UTC).getDayOfMonth() == dayOfMonth;
                        default -> false;
                    };
                })
                .toList();
    }

    /**
     * Expands events into chronologically sorted date occurrences for the next 28 days,
     * applying optional server-side filters, with pagination on the expanded list.
     */
    public List<UpcomingEventOccurrence> findUpcomingOccurrences(
            int stationId,
            List<Integer> memberIds,
            Integer categoryId,
            Boolean requiresRegistration,
            String search,
            int limit,
            int offset) {
        var events = findFilteredForMembers(stationId, memberIds, categoryId, requiresRegistration);
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            events = events.stream()
                    .filter(ev -> {
                        String name = ev.name() != null ? ev.name().toLowerCase() : "";
                        String desc =
                                ev.description() != null ? ev.description().toLowerCase() : "";
                        return name.contains(q) || desc.contains(q);
                    })
                    .toList();
        }
        var breaks = breakRepository.findByStation(stationId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int maxDays = 28;
        var occurrences = new ArrayList<UpcomingEventOccurrence>();

        // One-time events
        for (var ev : events) {
            if (ev.eventType() != StationEvent.EventType.ONE_TIME || ev.startTime() == null) continue;
            LocalDate eventDate = ev.startTime().atZone(ZoneOffset.UTC).toLocalDate();
            if (!eventDate.isBefore(today)) {
                occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), eventDate));
            }
        }

        // Recurring events for the next 28 days
        for (int d = 0; d <= maxDays; d++) {
            LocalDate date = today.plusDays(d);
            if (EventBreak.coversAny(breaks, date)) continue;

            for (var ev : events) {
                if (ev.occursOn(date)) {
                    occurrences.add(new UpcomingEventOccurrence(EventSummary.of(ev), date));
                }
            }
        }

        occurrences.sort(Comparator.comparing(UpcomingEventOccurrence::date));
        return occurrences.stream().skip(offset).limit(limit).toList();
    }

    // -- Categories --

    /**
     * Retrieves all event categories for a station.
     *
     * @param id the ID
     * @return the list of categories
     */
    public Optional<EventCategory> findCategoryById(int id) {
        return categoryRepository.findById(id);
    }

    public List<EventCategory> findCategoriesByStation(int stationId) {
        return categoryRepository.findByStation(stationId);
    }

    /**
     * Creates a new event category.
     *
     * @param stationId the station ID
     * @param name      the category name
     * @param position  the display order position
     * @param color     optional display color (#RRGGBB), or null
     * @return the created category
     */
    public EventCategory createCategory(int stationId, String name, int position, String color) {
        var category = categoryRepository.create(stationId, name, position, color);
        log.info("Created event category {} for station {}", category.id(), stationId);
        return category;
    }

    /**
     * Updates an event category.
     *
     * @param id       the category ID
     * @param name     the new name
     * @param position the new position
     * @param color    the optional new display color (#RRGGBB), or null to clear
     * @return true if the category was updated
     */
    public boolean updateCategory(
            int id, String name, int position, Integer maxShownEvents, boolean isPublic, String color) {
        if (categoryRepository.update(id, name, position, maxShownEvents, isPublic, color)) {
            log.info("Updated event category {}", id);
            return true;
        }
        log.warn("Cannot update event category: category {} not found", id);
        return false;
    }

    /**
     * Deletes an event category by ID.
     *
     * @param id the category ID
     * @return true if the category was deleted
     */
    public boolean deleteCategory(int id) {
        if (categoryRepository.delete(id)) {
            log.info("Deleted event category {}", id);
            return true;
        }
        log.warn("Cannot delete event category: category {} not found", id);
        return false;
    }

    /**
     * Rewrites the display order of a station's event categories. Ids from another station are
     * ignored by the repository, so a caller cannot reorder a foreign station's categories.
     *
     * @param stationId  the owning station
     * @param orderedIds the category IDs in their new order
     */
    public void reorderCategories(int stationId, List<Integer> orderedIds) {
        categoryRepository.reorder(stationId, orderedIds);
        log.info("Reordered {} event categories", orderedIds.size());
    }

    // -- Breaks --

    /**
     * Retrieves all event breaks for a station.
     *
     * @param stationId the station ID
     * @return the list of breaks
     */
    public List<EventBreak> findBreaksByStation(int stationId) {
        return breakRepository.findByStation(stationId);
    }

    /**
     * Finds an event break by its ID.
     *
     * @param id the break ID
     * @return the break, if found
     */
    public Optional<EventBreak> findBreakById(int id) {
        return breakRepository.findById(id);
    }

    /**
     * Creates a new event break.
     *
     * @param stationId the station ID
     * @param name      the break name
     * @param startDate the first day of the break
     * @param endDate   the last day of the break
     * @return the created break
     */
    public EventBreak createBreak(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        var eventBreak = breakRepository.create(stationId, name, startDate, endDate);
        log.info("Created event break {} for station {}", eventBreak.id(), stationId);
        return eventBreak;
    }

    /**
     * Updates an event break and returns the refreshed entity if the update was successful.
     *
     * @param id        the break ID
     * @param name      the new break name
     * @param startDate the new start date
     * @param endDate   the new end date
     * @return the updated break, or empty if not found
     */
    public Optional<EventBreak> updateBreak(int id, String name, LocalDate startDate, LocalDate endDate) {
        if (breakRepository.update(id, name, startDate, endDate)) {
            log.info("Updated event break {}", id);
            return breakRepository.findById(id);
        }
        log.warn("Cannot update event break: break {} not found", id);
        return Optional.empty();
    }

    /**
     * Deletes an event break by ID.
     *
     * @param id the break ID
     * @return true if the break was deleted
     */
    public boolean deleteBreak(int id) {
        if (breakRepository.delete(id)) {
            log.info("Deleted event break {}", id);
            return true;
        }
        log.warn("Cannot delete event break: break {} not found", id);
        return false;
    }

    // -- Restrictions --

    /**
     * Retrieves the restriction set for an event.
     *
     * @param eventId the event ID
     * @return the restriction set
     */
    public RestrictionSet findRestrictions(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        RestrictionMode mode = event != null ? event.restrictionMode() : RestrictionMode.AND;
        return restrictionService.findRestrictionSet(RestrictionType.EVENT, eventId, mode);
    }

    /**
     * Sets all restrictions for an event, replacing any existing restrictions.
     *
     * @param eventId   the event ID
     * @param selection the restriction selection to persist
     */
    public void setRestrictions(int eventId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.EVENT, eventId, selection);
        log.info("Set restrictions for event {}", eventId);
    }

    /**
     * Updates the restriction mode for an event.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     */
    public void updateRestrictionMode(int eventId, RestrictionMode mode) {
        eventRepository.updateRestrictionMode(eventId, mode);
        log.info("Updated restriction mode for event {} to {}", eventId, mode);
    }

    /**
     * Checks if a member is eligible for an event based on its restrictions.
     * Delegates to the DB function which resolves the member's identity internally.
     *
     * @param eventId  the event to check
     * @param memberId the member ID
     */
    public boolean isMemberEligible(int eventId, int memberId, Set<StationPermission> memberPermissions) {
        return restrictionService.checkRestriction(RestrictionType.EVENT, eventId, memberId, memberPermissions);
    }

    // -- Field Defaults --

    /**
     * Retrieves all field default configurations for an event.
     *
     * @param eventId the event ID
     * @return the list of field defaults
     */
    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return fieldDefaultRepository.findByEvent(eventId);
    }

    /**
     * Replaces all field defaults for an event.
     *
     * @param eventId  the event ID
     * @param defaults the new field default configurations
     */
    public void setFieldDefaults(int eventId, List<EventFieldDefault> defaults) {
        fieldDefaultRepository.replaceForEvent(eventId, defaults);
        log.info("Set field defaults for event {} ({} defaults)", eventId, defaults.size());
    }

    /**
     * Resolves field defaults for an event into concrete values by replacing event property links.
     */
    public Map<Integer, String> resolveFieldDefaults(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of();

        var defaults = fieldDefaultRepository.findByEvent(eventId);
        var result = new HashMap<Integer, String>();
        for (var def : defaults) {
            String resolved =
                    switch (def.source()) {
                        case "VALUE" -> def.value();
                        case "EVENT_NAME" -> event.name();
                        case "EVENT_DESCRIPTION" -> event.description();
                        case "EVENT_START_TIME" -> event.startTime() != null ? "\"" + event.startTime() + "\"" : null;
                        case "EVENT_END_TIME" -> event.endTime() != null ? "\"" + event.endTime() + "\"" : null;
                        default -> null;
                    };
            if (resolved != null) {
                result.put(def.fieldId(), resolved);
            }
        }
        return result;
    }

    // -- Registrations --

    /**
     * Retrieves all pending registrations for events in a station.
     *
     * @param stationId the station ID
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingRegistrationsByStation(int stationId) {
        return registrationRepository.findPendingByStation(stationId);
    }

    /**
     * Retrieves all registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return registrationRepository.findByEventAndDate(eventId, eventDate);
    }

    /**
     * Retrieves all registrations for an event across all dates.
     *
     * @param eventId the event ID
     * @return the list of registrations
     */
    public List<EventRegistration> findAllRegistrations(int eventId) {
        return registrationRepository.findByEvent(eventId);
    }

    /**
     * Retrieves all registrations for a specific member.
     *
     * @param memberId the member ID
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return registrationRepository.findByMember(memberId);
    }

    /**
     * Retrieves upcoming registrations for any member in the given collection in one query.
     * Used by the personal iCal feed to load owner + managed-member registrations together.
     *
     * @param memberIds the member IDs to fetch registrations for
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrationsByMembers(Collection<Integer> memberIds) {
        return registrationRepository.findByMembers(memberIds);
    }

    /**
     * Returns the most recent station-event modification time, for feed cache invalidation.
     */
    public Instant findMaxEventUpdatedAt(int stationId) {
        return eventRepository.findMaxEventUpdatedAt(stationId);
    }

    /**
     * Returns the most recent registration time across the given members, for feed cache invalidation.
     */
    public Instant findMaxRegistrationCreatedAt(Collection<Integer> memberIds) {
        return registrationRepository.findMaxCreatedAt(memberIds);
    }

    /**
     * Registers a member for an event. If {@code autoAccept} is true, the registration is immediately accepted.
     *
     * @param eventId    the event ID
     * @param memberId   the member ID
     * @param eventDate  the event occurrence date
     * @param autoAccept whether to automatically accept the registration
     * @param createdBy  the member ID of the creator, or null if self-registered
     * @return the created registration
     */
    public EventRegistration register(
            int eventId, int memberId, LocalDate eventDate, boolean autoAccept, Integer createdBy) {
        var registration =
                registrationRepository.create(eventId, memberId, eventDate, RegistrationStatus.PENDING, createdBy);
        log.info(
                "Registered member {} for event {} on {} (status={})",
                memberId,
                eventId,
                eventDate,
                autoAccept ? RegistrationStatus.ACCEPTED : RegistrationStatus.PENDING);
        if (autoAccept) {
            registrationRepository.updateStatus(registration.id(), RegistrationStatus.ACCEPTED);
            return registrationRepository.findById(registration.id()).orElse(registration);
        }
        return registration;
    }

    /**
     * Finds a registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventRegistration> findRegistrationById(int id) {
        return registrationRepository.findById(id);
    }

    public boolean updateRegistrationStatus(int id, RegistrationStatus status) {
        if (!registrationRepository.updateStatus(id, status)) {
            log.warn("Cannot update registration status: registration {} not found", id);
            return false;
        }
        log.info("Updated registration {} status to {}", id, status);
        registrationRepository.findById(id).ifPresent(registration -> eventRepository
                .findById(registration.eventId())
                .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                        event.stationId(), event.id(), event.name(), registration.memberId(), status))));
        return true;
    }

    public boolean withdrawRegistration(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (!registrationRepository.delete(id)) {
            log.warn("Cannot withdraw registration: registration {} not found", id);
            return false;
        }
        log.info("Withdrew registration {}", id);
        if (registration != null && registration.status() == RegistrationStatus.ACCEPTED) {
            eventRepository
                    .findById(registration.eventId())
                    .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                            event.stationId(),
                            event.id(),
                            event.name(),
                            registration.memberId(),
                            RegistrationStatus.WITHDRAWN)));
        }
        return true;
    }

    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, Integer createdBy) {
        var existing = registrationRepository.findByEventAndDate(eventId, eventDate).stream()
                .filter(r -> r.memberId() == memberId)
                .findFirst()
                .orElse(null);
        var result =
                registrationRepository.create(eventId, memberId, eventDate, RegistrationStatus.DECLINED, createdBy);
        log.info("Declined registration for member {} on event {} ({})", memberId, eventId, eventDate);
        if (existing != null && existing.status() == RegistrationStatus.ACCEPTED) {
            eventRepository
                    .findById(eventId)
                    .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                            event.stationId(), event.id(), event.name(), memberId, RegistrationStatus.DECLINED)));
        }
        return result;
    }

    public List<RegistrationCount> findRegistrationCounts(int stationId) {
        return registrationRepository.findCountsByStation(stationId);
    }

    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return registrationRepository.findDeclinedMemberIds(eventId, eventDate);
    }

    public List<MemberRegistrationStats> findRegistrationStats(int eventId, Integer categoryId, int months) {
        return registrationRepository.findStatsByEvent(eventId, categoryId, months);
    }

    // --- Reminders ---

    public List<Integer> findReminderDays(int eventId) {
        return reminderRepository.findDays(eventId);
    }

    public void setReminders(int eventId, List<Integer> daysBefore) {
        reminderRepository.replace(eventId, daysBefore);
        log.info("Set reminders for event {} ({} days)", eventId, daysBefore.size());
    }
}

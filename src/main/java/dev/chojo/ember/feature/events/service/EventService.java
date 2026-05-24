/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service providing business logic for station events, including CRUD operations for events, breaks, categories,
 * restrictions, field defaults, and registrations.
 */
@Singleton
public class EventService {
    private final EventRepository eventRepository;
    private final RestrictionRepository restrictionRepository;

    @Inject
    public EventService(EventRepository eventRepository, RestrictionRepository restrictionRepository) {
        this.eventRepository = eventRepository;
        this.restrictionRepository = restrictionRepository;
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
     * Retrieves events for a station that the given member is allowed to see.
     *
     * @param stationId the station ID
     * @param memberId  the requesting member ID
     * @return the filtered list of station events
     */
    public List<StationEvent> findByStationForMember(int stationId, int memberId) {
        return eventRepository.findByStationForMember(stationId, memberId);
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
            Integer categoryId) {
        return eventRepository.create(
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
                categoryId);
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
            Integer categoryId) {
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
                categoryId)) {
            return eventRepository.findById(id);
        }
        return Optional.empty();
    }

    /**
     * Deletes a station event by ID.
     *
     * @param id the event ID
     * @return true if the event was deleted
     */
    public boolean delete(int id) {
        return eventRepository.delete(id);
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
        boolean inBreak = eventRepository.isDateInBreak(stationId, today);

        return eventRepository.findByStation(stationId).stream()
                .filter(e -> {
                    if (e.eventType() == StationEvent.EventType.ONE_TIME) {
                        if (e.startTime() == null) return false;
                        LocalDate eventDateUtc =
                                e.startTime().atZone(ZoneOffset.UTC).toLocalDate();
                        return today.equals(eventDateUtc);
                    }
                    if (inBreak || e.dayOfWeek() == null) return false;
                    return switch (e.eventType()) {
                        case RECURRING -> e.dayOfWeek() == dayOfWeek;
                        case MONTHLY_FIRST -> e.dayOfWeek() == dayOfWeek && dayOfMonth <= 7;
                        case QUARTERLY -> e.dayOfWeek() == dayOfWeek && dayOfMonth <= 7 && (monthValue - 1) % 3 == 0;
                        case YEARLY ->
                            e.startTime() != null
                                    && e.startTime().atZone(ZoneOffset.UTC).getMonthValue() == monthValue
                                    && e.startTime().atZone(ZoneOffset.UTC).getDayOfMonth() == dayOfMonth;
                        default -> false;
                    };
                })
                .toList();
    }

    // -- Categories --

    /**
     * Retrieves all event categories for a station.
     *
     * @param stationId the station ID
     * @return the list of categories
     */
    public List<EventCategory> findCategoriesByStation(int stationId) {
        return eventRepository.findCategoriesByStation(stationId);
    }

    /**
     * Creates a new event category.
     *
     * @param stationId the station ID
     * @param name      the category name
     * @param position  the display order position
     * @return the created category
     */
    public EventCategory createCategory(int stationId, String name, int position) {
        return eventRepository.createCategory(stationId, name, position);
    }

    /**
     * Updates an event category.
     *
     * @param id       the category ID
     * @param name     the new name
     * @param position the new position
     * @return true if the category was updated
     */
    public boolean updateCategory(int id, String name, int position) {
        return eventRepository.updateCategory(id, name, position);
    }

    /**
     * Deletes an event category by ID.
     *
     * @param id the category ID
     * @return true if the category was deleted
     */
    public boolean deleteCategory(int id) {
        return eventRepository.deleteCategory(id);
    }

    // -- Breaks --

    /**
     * Retrieves all event breaks for a station.
     *
     * @param stationId the station ID
     * @return the list of breaks
     */
    public List<EventBreak> findBreaksByStation(int stationId) {
        return eventRepository.findBreaksByStation(stationId);
    }

    /**
     * Finds an event break by its ID.
     *
     * @param id the break ID
     * @return the break, if found
     */
    public Optional<EventBreak> findBreakById(int id) {
        return eventRepository.findBreakById(id);
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
        return eventRepository.createBreak(stationId, name, startDate, endDate);
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
        if (eventRepository.updateBreak(id, name, startDate, endDate)) {
            return eventRepository.findBreakById(id);
        }
        return Optional.empty();
    }

    /**
     * Deletes an event break by ID.
     *
     * @param id the break ID
     * @return true if the break was deleted
     */
    public boolean deleteBreak(int id) {
        return eventRepository.deleteBreak(id);
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
        return restrictionRepository.findRestrictionSet(
                RestrictionType.EVENT.table(), RestrictionType.EVENT.fkColumn(), eventId, mode);
    }

    /**
     * Sets all restrictions for an event, replacing any existing restrictions.
     *
     * @param eventId   the event ID
     * @param roleIds   the role IDs to restrict to, or null for no role restrictions
     * @param groupIds  the group IDs to restrict to, or null for no group restrictions
     * @param tagIds    the tag IDs to restrict to, or null for no tag restrictions
     * @param memberIds the member IDs to restrict to, or null for no member restrictions
     */
    public void setRestrictions(
            int eventId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {
        restrictionRepository.setRestrictions(
                RestrictionType.EVENT.table(),
                RestrictionType.EVENT.fkColumn(),
                eventId,
                roleIds != null ? roleIds : List.of(),
                groupIds != null ? groupIds : List.of(),
                tagIds != null ? tagIds : List.of(),
                memberIds != null ? memberIds : List.of());
    }

    /**
     * Updates the restriction mode for an event.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     */
    public void updateRestrictionMode(int eventId, RestrictionMode mode) {
        eventRepository.updateRestrictionMode(eventId, mode);
    }

    /**
     * Checks if a member is eligible for an event based on its restrictions.
     * Delegates to the DB function which resolves the member's identity internally.
     *
     * @param eventId  the event to check
     * @param memberId the member ID
     */
    public boolean isMemberEligible(int eventId, int memberId) {
        return restrictionRepository.checkRestriction(RestrictionType.EVENT, eventId, memberId);
    }

    // -- Field Defaults --

    /**
     * Retrieves all field default configurations for an event.
     *
     * @param eventId the event ID
     * @return the list of field defaults
     */
    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return eventRepository.findFieldDefaults(eventId);
    }

    /**
     * Replaces all field defaults for an event.
     *
     * @param eventId  the event ID
     * @param defaults the new field default configurations
     */
    public void setFieldDefaults(int eventId, List<EventFieldDefault> defaults) {
        eventRepository.setFieldDefaults(eventId, defaults);
    }

    /**
     * Resolves field defaults for an event into concrete values by replacing event property links.
     */
    public Map<Integer, String> resolveFieldDefaults(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of();

        var defaults = eventRepository.findFieldDefaults(eventId);
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
        return eventRepository.findPendingRegistrationsByStation(stationId);
    }

    /**
     * Retrieves all registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return eventRepository.findRegistrations(eventId, eventDate);
    }

    /**
     * Retrieves all registrations for an event across all dates.
     *
     * @param eventId the event ID
     * @return the list of registrations
     */
    public List<EventRegistration> findAllRegistrations(int eventId) {
        return eventRepository.findAllRegistrations(eventId);
    }

    /**
     * Retrieves all registrations for a specific member.
     *
     * @param memberId the member ID
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return eventRepository.findRegistrationsByMember(memberId);
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
        var registration = eventRepository.createRegistration(
                eventId, memberId, eventDate, EventRegistration.RegistrationStatus.PENDING, createdBy);
        if (autoAccept) {
            eventRepository.updateRegistrationStatus(registration.id(), EventRegistration.RegistrationStatus.ACCEPTED);
            return eventRepository.findRegistrationById(registration.id()).orElse(registration);
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
        return eventRepository.findRegistrationById(id);
    }

    public boolean updateRegistrationStatus(int id, EventRegistration.RegistrationStatus status) {
        return eventRepository.updateRegistrationStatus(id, status);
    }

    public boolean withdrawRegistration(int id) {
        return eventRepository.deleteRegistration(id);
    }

    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, Integer createdBy) {
        return eventRepository.createRegistration(
                eventId, memberId, eventDate, EventRegistration.RegistrationStatus.DECLINED, createdBy);
    }

    public List<EventRepository.RegistrationCount> findRegistrationCounts(int stationId) {
        return eventRepository.findRegistrationCounts(stationId);
    }

    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return eventRepository.findDeclinedMemberIds(eventId, eventDate);
    }
}

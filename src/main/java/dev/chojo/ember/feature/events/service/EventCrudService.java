/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of station events: lookups, creation, updates, deletion and cancellation,
 * including the domain events that accompany them.
 */
@Singleton
public class EventCrudService {
    private static final Logger log = LoggerFactory.getLogger(EventCrudService.class);

    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;

    @Inject
    public EventCrudService(EventRepository eventRepository, DomainEventBus eventBus) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
    }

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
     * Bulk-resolves the public UUIDs for a set of event ids - see
     * {@link EventRepository#findPublicUidsByIds}.
     */
    public Map<Integer, UUID> findPublicUidsByIds(int stationId, Collection<Integer> ids) {
        return eventRepository.findPublicUidsByIds(stationId, ids);
    }

    /**
     * Resolves a single station event by its public UUID - used by cell renderers.
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

    /**
     * Applies the optional category and registration filters for a single member perspective.
     */
    public List<StationEvent> findFiltered(
            int stationId, Integer memberId, Integer categoryId, Boolean requiresRegistration) {
        return eventRepository.findFiltered(stationId, memberId, categoryId, requiresRegistration);
    }

    /**
     * Unions the filtered events visible to any of the given members, keeping the first occurrence
     * of every event. A null member list falls back to the unrestricted station view.
     */
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
     * Creates a new station event and announces it on the domain event bus.
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
     * Returns the most recent station-event modification time, for feed cache invalidation.
     */
    public Instant findMaxEventUpdatedAt(int stationId) {
        return eventRepository.findMaxEventUpdatedAt(stationId);
    }
}

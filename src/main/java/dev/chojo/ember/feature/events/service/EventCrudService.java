/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventChanged;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.equipment.service.EquipmentReleaseService;
import dev.chojo.ember.feature.events.entity.PickerEvent;
import dev.chojo.ember.feature.events.entity.PickerMode;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of station events: lookups, creation, updates and deletion, including the
 * domain events that accompany them. Calling off lives in {@link EventCancellationService}.
 */
@Singleton
public class EventCrudService {
    private static final Logger log = LoggerFactory.getLogger(EventCrudService.class);

    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;
    private final EquipmentReleaseService equipmentRelease;
    private final RestrictionService restrictionService;

    @Inject
    public EventCrudService(
            EventRepository eventRepository,
            DomainEventBus eventBus,
            EquipmentReleaseService equipmentRelease,
            RestrictionService restrictionService) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.equipmentRelease = equipmentRelease;
        this.restrictionService = restrictionService;
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
     * {@code PAST_EVENT_RECAP} cells, offering the events every reader of the audience may see: on a
     * page the public ones, in a news or wiki article every event kept to nobody in particular. Who
     * is picking does not widen it, so a block never names what part of its readers cannot see.
     */
    public List<PickerEvent> searchEventPicker(
            int stationId, BlockAudience audience, @Nullable String search, PickerMode mode, int limit) {
        return eventRepository.searchForPicker(stationId, audience, search, mode, limit);
    }

    /**
     * The event a block names, when every reader of the audience may see it. See
     * {@link EventRepository#findOpenByUid}.
     */
    public Optional<StationEvent> findOpenByUid(int stationId, BlockAudience audience, UUID publicUid) {
        return eventRepository.findOpenByUid(stationId, audience, publicUid);
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
     * Retrieves events for a station that the given member is allowed to see, all of them where
     * the member manages events.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @return the filtered list of station events
     */
    public List<StationEvent> findByStationForMember(int stationId, int memberId) {
        return eventRepository.findByStationForMember(stationId, memberId, managesEvents(memberId));
    }

    /**
     * Applies the optional category and registration filters for a single member perspective. A
     * member who manages events sees them all, as does a {@code null} member.
     */
    public List<StationEvent> findFiltered(
            int stationId,
            @Nullable Integer memberId,
            @Nullable Integer categoryId,
            @Nullable Boolean requiresRegistration) {
        return eventRepository.findFiltered(stationId, restrictedTo(memberId), categoryId, requiresRegistration);
    }

    /**
     * Unions the filtered events visible to any of the given members, keeping the first occurrence
     * of every event. A null member list falls back to the unrestricted station view, and so does
     * any listed member who manages events.
     */
    public List<StationEvent> findFilteredForMembers(
            int stationId,
            @Nullable List<Integer> memberIds,
            @Nullable Integer categoryId,
            @Nullable Boolean requiresRegistration) {
        if (memberIds == null) {
            return eventRepository.findFiltered(stationId, null, categoryId, requiresRegistration);
        }
        var eventMap = new LinkedHashMap<Integer, StationEvent>();
        for (int mid : memberIds) {
            for (var ev :
                    eventRepository.findFiltered(stationId, restrictedTo(mid), categoryId, requiresRegistration)) {
                eventMap.putIfAbsent(ev.id(), ev);
            }
        }
        return new ArrayList<>(eventMap.values());
    }

    /**
     * The member whose view restrictions narrow a listing, or {@code null} where nothing narrows
     * it: no member was named, or the member manages events and sees all of them.
     */
    private @Nullable Integer restrictedTo(@Nullable Integer memberId) {
        return memberId == null || managesEvents(memberId) ? null : memberId;
    }

    private boolean managesEvents(int memberId) {
        return restrictionService.manages(RestrictionType.EVENT_VIEW, memberId);
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
            @Nullable String description,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            @Nullable Integer templateId,
            boolean requiresRegistration,
            @Nullable Instant registrationDeadline,
            boolean requiresConfirmation,
            @Nullable Integer categoryId,
            @Nullable Integer registrationLimit,
            @Nullable Integer minRegistrations,
            @Nullable Integer thresholdDays,
            @Nullable Integer registrationCloseDays) {
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
                thresholdDays,
                registrationCloseDays);
        eventBus.publish(new EventCreated(stationId, event));
        return event;
    }

    /**
     * Announces an event that was persisted with {@link #createWithoutEvent}.
     *
     * <p>Whoever hears about a new appointment depends on who may know it exists, and that is
     * written after the row. A caller that has audiences to set therefore persists first, sets them,
     * and announces last, so the handlers see a finished event rather than a bare one.
     */
    public void announceCreated(int stationId, StationEvent event) {
        eventBus.publish(new EventCreated(stationId, event));
    }

    /**
     * Persists a new event without publishing any domain event. Reserved for callers that
     * aggregate their own domain event (e.g. {@code BatchEventService} emitting
     * {@link dev.chojo.ember.event.events.EventsBatchCreated} once at the end), and for those that
     * must write the event's audiences before it is announced.
     */
    public StationEvent createWithoutEvent(
            int stationId,
            String name,
            @Nullable String description,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            @Nullable Integer templateId,
            boolean requiresRegistration,
            @Nullable Instant registrationDeadline,
            boolean requiresConfirmation,
            @Nullable Integer categoryId,
            @Nullable Integer registrationLimit,
            @Nullable Integer minRegistrations,
            @Nullable Integer thresholdDays,
            @Nullable Integer registrationCloseDays) {
        requireUsableSpan(startTime, endTime, Refusal.EVENT_ENDS_BEFORE_IT_STARTS_ON_CREATE);
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
                thresholdDays,
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
            @Nullable String description,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            @Nullable Integer templateId,
            boolean requiresRegistration,
            @Nullable Instant registrationDeadline,
            boolean requiresConfirmation,
            @Nullable Integer categoryId,
            @Nullable Boolean isPublic,
            @Nullable Integer registrationLimit,
            @Nullable Integer minRegistrations,
            @Nullable Integer thresholdDays,
            @Nullable Integer registrationCloseDays) {
        requireUsableSpan(startTime, endTime, Refusal.EVENT_ENDS_BEFORE_IT_STARTS_ON_CHANGE);
        var before = eventRepository.findById(id).orElse(null);
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
                thresholdDays,
                registrationCloseDays)) {
            log.info("Updated event {}", id);
            var after = eventRepository.findById(id);
            after.filter(event -> before != null)
                    .ifPresent(event -> eventBus.publish(new EventChanged(event.stationId(), before, event)));
            return after;
        }
        log.warn("Cannot update event: event {} not found", id);
        return Optional.empty();
    }

    /**
     * Refuses an appointment that finishes before it begins.
     *
     * <p>An appointment may run past midnight and over several days, which is what a camp is, so the
     * length is not measured here. Only the one order that cannot happen is refused, and it reached
     * further than the appointment itself: an attendance sheet takes its times from the appointment,
     * and a sheet running backwards counts everybody on it for nothing.
     *
     * @param startTime when it begins, null where none was given
     * @param endTime   when it ends, read the same way
     * @param refusal   what the caller refuses a backwards span with
     * @throws RefusalResponse where the end lies before the start
     */
    private void requireUsableSpan(Instant startTime, Instant endTime, Refusal refusal) {
        if (startTime == null || endTime == null) return;
        if (endTime.isBefore(startTime)) {
            throw refusal.raise();
        }
    }

    /**
     * Says when a repeating event stops repeating, or takes the end off again.
     *
     * <p>A last day and a number of times are two ways of saying the same thing, so only one of them
     * is ever set. A one-off appointment has nothing to repeat and is refused rather than quietly
     * given an end nobody would ever see.
     *
     * @param id    the event
     * @param until the last day it may fall on, or null
     * @param count how many times it takes place in total, or null
     * @return the event as it now stands, or empty when there is no such event
     */
    public Optional<StationEvent> setRepeatEnd(int id, @Nullable LocalDate until, @Nullable Integer count) {
        var event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            log.warn("Cannot set the repeat end: event {} not found", id);
            return Optional.empty();
        }
        if (until != null && count != null) {
            throw Refusal.EVENT_SERIES_END_GIVEN_TWICE.raise();
        }
        if ((until != null || count != null) && !event.isRecurring()) {
            throw Refusal.EVENT_SERIES_END_ON_ONE_OFF.raise();
        }
        if (count != null && count < 1) {
            throw Refusal.EVENT_SERIES_COUNT_BELOW_ONE.raise();
        }
        if (until != null
                && event.startTime() != null
                && until.isBefore(event.startTime().atZone(ZoneOffset.UTC).toLocalDate())) {
            throw Refusal.EVENT_SERIES_ENDS_BEFORE_IT_STARTS.raise();
        }

        eventRepository.updateRepeatEnd(id, until, count);
        log.info("Event {} now repeats until {} or {} times", id, until, count);
        var after = eventRepository.findById(id);
        after.ifPresent(updated -> eventBus.publish(new EventChanged(updated.stationId(), event, updated)));
        return after;
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
        equipmentRelease.release(id, event.stationId());
        if (eventRepository.delete(id)) {
            log.info("Deleted event {} for station {}", id, event.stationId());
            eventBus.publish(new EventDeleted(event.stationId(), id, event.name()));
            return true;
        }
        log.warn("Failed to delete event {}", id);
        return false;
    }

    /**
     * Returns the most recent station-event modification time, for feed cache invalidation.
     */
    public Instant findMaxEventUpdatedAt(int stationId) {
        return eventRepository.findMaxEventUpdatedAt(stationId);
    }
}

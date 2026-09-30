/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;

/**
 * What a feed entry says about an appointment: the fields the notification carries, and from the
 * appointment itself when it still exists its times, its recurrence, its registration terms and
 * its custom fields.
 */
@Singleton
public class EventFeedDetails implements FeedDetailsContributor {
    private final EventCrudService crudService;
    private final EventFieldService eventFieldService;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EventFeedDetails(
            EventCrudService crudService, EventFieldService eventFieldService, OccurrenceCalendar occurrenceCalendar) {
        this.crudService = crudService;
        this.eventFieldService = eventFieldService;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.NewEvent(String _, String eventDescription) -> {
                details.putSnippetIfPresent(details.label("description", "Description"), eventDescription);
                addEventContext(details);
            }
            case NotificationParams.NewEventsBatch(int count, String eventPreview, LocalDate firstEventDate) -> {
                details.put(details.label("count", "Count"), String.valueOf(count));
                details.putIfPresent(details.label("events"), eventPreview);
                if (firstEventDate != null) {
                    details.put(details.label("start", "Start"), details.date(firstEventDate));
                }
            }
            case NotificationParams.EventRegistrationStatus(
                    String memberName,
                    String eventName,
                    RegistrationStatus status,
                    String eventDescription) -> {
                details.putIfPresent(details.label("member", "Member"), memberName);
                details.putIfPresent(details.label("status", "Status"), details.statusWithSymbol(status.name()));
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putSnippetIfPresent(details.label("description", "Description"), eventDescription);
                addEventContext(details);
            }
            case NotificationParams.EventCancelled(String eventName, String reason) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putIfPresent(details.label("reason"), reason);
                addEventContext(details);
            }
            case NotificationParams.EventReminder(String eventName, int daysBefore, LocalDate eventDate) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                if (eventDate != null) details.put(details.label("eventDate"), details.date(eventDate));
                details.put(details.label("daysBefore"), String.valueOf(daysBefore));
                addEventContext(details);
            }
            case NotificationParams.RegistrationClosing(String eventName, int daysBefore, String memberName) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putIfPresent(details.label("member", "Member"), memberName);
                details.put(details.label("daysBefore"), String.valueOf(daysBefore));
                addEventContext(details);
            }
            case NotificationParams.RegistrationAnswerMissing(
                    String eventName,
                    LocalDate eventDate,
                    String memberName) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putIfPresent(details.label("member", "Member"), memberName);
                if (eventDate != null) details.put(details.label("eventDate"), details.date(eventDate));
                addEventContext(details);
            }
            case NotificationParams.EventDateDropped(
                    String eventName,
                    LocalDate eventDate,
                    String memberName,
                    LocalDate nextDate) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putIfPresent(details.label("member", "Member"), memberName);
                if (eventDate != null) details.put(details.label("eventDate"), details.date(eventDate));
                if (nextDate != null) details.put(details.label("nextDate", "Next date"), details.date(nextDate));
                addEventContext(details);
            }
            case NotificationParams.EventMoved(String eventName, LocalDate eventDate, String _, String memberName) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.putIfPresent(details.label("member", "Member"), memberName);
                if (eventDate != null) details.put(details.label("eventDate"), details.date(eventDate));
                addEventContext(details);
            }
            case NotificationParams.RegistrationDeadlineExpired(String eventName, int pendingCount) -> {
                details.putIfPresent(details.label("event", "Event"), eventName);
                details.put(details.label("pendingCount"), String.valueOf(pendingCount));
            }
            default -> {}
        }
    }

    /**
     * Adds what the appointment the notification links to says about itself. A same-day appointment
     * collapses into one "when" row so the reader reads one fact rather than two; one that spans days
     * keeps a start and an end, each with its own date. Registration terms are surfaced so a member
     * can act on the entry alone, and custom fields are named by the service, because a reader
     * outside the app cannot read an internal id.
     *
     * <p>A missing link or an appointment deleted since adds nothing, and neither does a lookup that
     * fails: a feed never breaks over a stale reference.
     */
    private void addEventContext(FeedDetails details) {
        Integer eventId = details.linkId();
        if (eventId == null) return;
        try {
            var event = crudService.findById(eventId).orElse(null);
            if (event == null) return;
            addTimes(details, event);
            addRecurrence(details, event);
            if (event.requiresRegistration() && event.registrationDeadline() != null) {
                details.put(
                        details.label("deadline", "Deadline"),
                        details.moment(event.registrationDeadline(), details.zoneOf(event.stationId())));
            }
            if (event.registrationLimit() != null) {
                details.put(details.label("limit", "Limit"), String.valueOf(event.registrationLimit()));
            }
            for (var field : eventFieldService.findByEvent(
                    eventId, occurrenceCalendar.dateInView(event).orElse(null))) {
                if (field.value() == null || field.value().isBlank()) continue;
                String value = eventFieldService.displayValue(field);
                if (value.isBlank()) continue;
                details.put(field.name(), value);
            }
        } catch (Exception ignored) {
        }
    }

    private static void addTimes(FeedDetails details, StationEvent event) {
        var start = event.startTime();
        var end = event.endTime();
        var zone = details.zoneOf(event.stationId());
        if (start != null && end != null && details.sameDay(start, end, zone)) {
            details.put(details.label("when", "When"), details.sameDayRange(start, end, zone));
            return;
        }
        if (start != null) details.put(details.label("start", "Start"), details.moment(start, zone));
        if (end != null) details.put(details.label("end", "End"), details.moment(end, zone));
    }

    /** Names how an appointment repeats, where the bundle has a name for it. */
    private static void addRecurrence(FeedDetails details, StationEvent event) {
        if (event.eventType() == null || event.eventType() == StationEvent.EventType.ONE_TIME) return;
        String key = "eventType." + event.eventType().name();
        String recurrence = details.localized("ical", key, null);
        if (!recurrence.equals(key)) details.put(details.label("recurrence", "Recurrence"), recurrence);
    }
}

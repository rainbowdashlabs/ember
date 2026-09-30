/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Aggregates a batch event creation into a single NEW_EVENTS_BATCH notification per member instead
 * of emitting one NEW_EVENT per row. The preview lists up to three event names.
 */
@Singleton
public class EventsBatchCreatedHandler implements DomainEventHandler<EventsBatchCreated> {
    private static final int PREVIEW_LIMIT = 3;

    private final Notifier notifier;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final RestrictionService restrictionService;

    @Inject
    public EventsBatchCreatedHandler(
            Notifier notifier,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            RestrictionService restrictionService) {
        this.notifier = notifier;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.restrictionService = restrictionService;
    }

    @Override
    public Class<EventsBatchCreated> eventType() {
        return EventsBatchCreated.class;
    }

    /**
     * Announces a batch of new appointments as one entry naming when the first of them falls.
     *
     * <p>Each member hears only about the appointments they may see: the entry counts and names
     * those alone, and a member who may see none of them hears nothing.
     *
     * <p>Which day that is belongs to the station's clock rather than the server's: an appointment
     * just after midnight in Berlin is the previous day read in UTC, and the announcement would name
     * a day nobody is meeting on.
     */
    @Override
    public void handle(EventsBatchCreated event) {
        var events = event.events();
        if (events.isEmpty()) return;

        var zone = StationFormat.timezoneOf(
                stationRepository.findById(event.stationId()).orElse(null));
        var audiences = events.stream()
                .map(e -> restrictionService.findMembersPassingRestriction(
                        RestrictionType.EVENT_VIEW, e.id(), event.stationId()))
                .toList();

        if (audiences.stream().allMatch(Optional::isEmpty)) {
            notifier.notify(
                    StationAudience.wholeStation(event.stationId()),
                    NotificationType.NEW_EVENTS_BATCH,
                    dataFor(events, zone),
                    Delivery.EVERY_TIME);
            return;
        }

        var membersBySeenEvents = new LinkedHashMap<List<StationEvent>, List<Integer>>();
        for (var member : stationMemberRepository.findByStation(event.stationId())) {
            var seen = IntStream.range(0, events.size())
                    .filter(i -> audiences
                            .get(i)
                            .map(ids -> ids.contains(member.id()))
                            .orElse(true))
                    .mapToObj(events::get)
                    .toList();
            if (seen.isEmpty()) continue;
            membersBySeenEvents.computeIfAbsent(seen, key -> new ArrayList<>()).add(member.id());
        }
        membersBySeenEvents.forEach((seen, memberIds) -> notifier.notify(
                StationAudience.members(memberIds),
                NotificationType.NEW_EVENTS_BATCH,
                dataFor(seen, zone),
                Delivery.EVERY_TIME));
    }

    /**
     * The announcement of some appointments: how many, the names of the first few and the day the
     * earliest falls on.
     */
    private static NotificationData dataFor(List<StationEvent> events, ZoneId zone) {
        var preview = new StringBuilder();
        int previewN = Math.min(PREVIEW_LIMIT, events.size());
        for (int i = 0; i < previewN; i++) {
            if (i > 0) preview.append(", ");
            preview.append(events.get(i).name());
        }
        if (events.size() > previewN) {
            preview.append(", …");
        }

        LocalDate firstEventDate = events.stream()
                .map(StationEvent::startTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .map(instant -> instant.atZone(zone).toLocalDate())
                .orElse(null);

        return NotificationData.of(
                new NotificationParams.NewEventsBatch(events.size(), preview.toString(), firstEventDate),
                new NotificationData.NotificationLink("events-upcoming", Map.of()));
    }
}

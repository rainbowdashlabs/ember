/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupDeadlineExpired;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Watches the deadlines of event groups, once per event group rather than once per part.
 *
 * <p>A few days and one day before sign-ups close, members the event group is meant for who never answered
 * it are reminded. Once they closed with sign-ups still waiting for a decision, whoever runs events
 * is told. The parts' own deadlines are left out of the appointment checks, so nobody hears of a
 * event group once for every part.
 */
@Singleton
public class EventGroupDeadlineChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EventGroupDeadlineChecker.class);
    /** The lead times warned about, nearest first: a deadline inside several gets the nearest one only. */
    private static final int[] CLOSING_WARNINGS = {1, 3};

    private final EventGroupRepository eventGroups;
    private final DomainEventBus eventBus;
    private final Notifier notifier;
    private final MemberNameResolver names;
    private final RestrictionService restrictions;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public EventGroupDeadlineChecker(
            EventGroupRepository eventGroups,
            DomainEventBus eventBus,
            Notifier notifier,
            MemberNameResolver names,
            RestrictionService restrictions,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventGroups = eventGroups;
        this.eventBus = eventBus;
        this.notifier = notifier;
        this.names = names;
        this.restrictions = restrictions;
        this.readOnlyGuard = readOnlyGuard;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "event-group-deadline-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)),
                this::check));
    }

    /** Runs one sweep: warnings for deadlines coming, notices for deadlines passed. */
    void check() {
        try {
            warnAboutClosingEventGroups();
            tellAboutExpiredEventGroups();
        } catch (RuntimeException e) {
            log.error("Error checking event group deadlines", e);
        }
    }

    private void warnAboutClosingEventGroups() {
        for (int daysBefore : CLOSING_WARNINGS) {
            for (var eventGroup : eventGroups.findClosingIn(daysBefore)) {
                if (!readOnlyGuard.isWritable(eventGroup.stationId())) continue;
                for (int memberId :
                        eventGroups.findUnansweredMemberIds(eventGroup.eventGroupId(), eventGroup.stationId())) {
                    if (!meantFor(eventGroup.eventGroupId(), memberId)) continue;
                    notifier.notify(
                            StationAudience.household(List.of(memberId)),
                            NotificationType.REGISTRATION_CLOSING,
                            NotificationData.of(
                                    new NotificationParams.RegistrationClosing(
                                            eventGroup.name(), daysBefore, names.called(memberId)),
                                    NotificationLinks.eventGroup(eventGroup.eventGroupId())),
                            Delivery.EVERY_TIME);
                }
                for (int wider : CLOSING_WARNINGS) {
                    if (wider >= daysBefore) eventGroups.markClosingWarned(eventGroup.eventGroupId(), wider);
                }
            }
        }
    }

    private void tellAboutExpiredEventGroups() {
        for (var eventGroup : eventGroups.findExpiredDeadlines()) {
            if (!readOnlyGuard.isWritable(eventGroup.stationId())) continue;
            eventGroups.markDeadlineNotified(eventGroup.eventGroupId());
            eventBus.publish(new EventGroupDeadlineExpired(
                    eventGroup.stationId(), eventGroup.eventGroupId(), eventGroup.name(), eventGroup.pendingCount()));
        }
    }

    private boolean meantFor(int eventGroupId, int memberId) {
        var member = restrictions.memberOf(memberId).orElse(null);
        return member != null
                && restrictions.includes(RestrictionType.EVENT_GROUP_VIEW, eventGroupId, member)
                && restrictions.includes(RestrictionType.EVENT_GROUP, eventGroupId, member);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Notifies a member of this station, and whoever looks after them, that a partner station decided about
 * their sign-up for one of its appointments or event groups, with the notice a member of the partner gets.
 * The partner's managers are not notified: they did not decide and are not asked to act.
 */
@Singleton
public class PartnerStatusNotices {
    private final Notifier notifier;
    private final MemberNameResolver names;

    @Inject
    public PartnerStatusNotices(Notifier notifier, MemberNameResolver names) {
        this.notifier = notifier;
        this.names = names;
    }

    /**
     * Notifies a member's household about the status a partner gave their sign-up.
     *
     * @param memberId      the member
     * @param name          the appointment or event group, as the partner names it
     * @param status        the status the sign-up holds now
     * @param queuePosition the place in line where it now waits on the waiting list, or null
     * @param movedUp       whether it moved up from the waiting list
     * @param link          where the notice opens
     */
    public void tell(
            int memberId,
            String name,
            RegistrationStatus status,
            @Nullable Integer queuePosition,
            boolean movedUp,
            NotificationLink link) {
        notifier.notify(
                StationAudience.household(List.of(memberId)),
                NotificationType.EVENT_REGISTRATION_STATUS,
                NotificationData.of(
                        new NotificationParams.EventRegistrationStatus(
                                names.called(memberId), name, status, null, queuePosition, movedUp, false),
                        link),
                Delivery.EVERY_TIME);
    }
}

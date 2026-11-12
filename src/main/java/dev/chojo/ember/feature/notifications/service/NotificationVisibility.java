/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.HiddenNotifications;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which notifications a station's switched-off modules hide, and which types its settings page offers.
 *
 * <p>Nothing is deleted when a module goes off: its notifications are kept and only left out of what is
 * shown, counted and mailed, so they are back the moment the module is.
 */
@Singleton
public class NotificationVisibility {
    private final ModuleAvailability modules;
    private final StationMemberRepository members;

    @Inject
    public NotificationVisibility(ModuleAvailability modules, StationMemberRepository members) {
        this.modules = modules;
        this.members = members;
    }

    /**
     * What one reader's feed leaves out.
     *
     * @param recipient whose feed
     * @return what the station of a station member hides; nothing for a cluster member
     */
    public HiddenNotifications hiddenFor(Recipient recipient) {
        return switch (recipient) {
            case Recipient.OfStation station ->
                members.findById(station.memberId())
                        .map(member -> hiddenAt(member.stationId()))
                        .orElse(HiddenNotifications.NONE);
            case Recipient.OfCluster ignored -> HiddenNotifications.NONE;
        };
    }

    /**
     * What a station hides.
     *
     * @param stationId the station
     * @return the hidden types and mention routes
     */
    public HiddenNotifications hiddenAt(int stationId) {
        return HiddenNotifications.of(modules.disabledAt(stationId));
    }

    /**
     * The types a station's members can choose about: every type but those of a switched-off module.
     *
     * @param stationId the station
     * @return the offered types
     */
    public Set<NotificationType> offeredAt(int stationId) {
        Set<NotificationType> offered = EnumSet.allOf(NotificationType.class);
        offered.removeAll(hiddenAt(stationId).types());
        return offered;
    }
}

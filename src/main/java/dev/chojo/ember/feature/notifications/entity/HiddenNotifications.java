/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.station.entity.StationModule;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The notifications of a station's switched-off modules, which are kept but not shown there.
 *
 * <p>Most notifications name their module through their type. A mention takes the module of the page it
 * was made on, so the mentions on the pages of a switched-off module are named by those pages' routes.
 *
 * @param types         the types of switched-off modules
 * @param mentionRoutes the routes of the pages whose mentions are hidden
 */
public record HiddenNotifications(Set<NotificationType> types, Set<String> mentionRoutes) {

    /** Nothing hidden, for a station that has every module and for a cluster's feed. */
    public static final HiddenNotifications NONE = new HiddenNotifications(Set.of(), Set.of());

    /**
     * What a station with the given modules switched off hides.
     *
     * @param disabled the switched-off modules, with their parts
     * @return the hidden types and mention routes
     */
    public static HiddenNotifications of(Set<StationModule> disabled) {
        if (disabled.isEmpty()) return NONE;
        Set<NotificationType> types = EnumSet.noneOf(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            if (type.module().map(module -> module.isOffIn(disabled)).orElse(false)) types.add(type);
        }
        Set<String> routes = new TreeSet<>();
        NotificationLinks.commentPages().forEach((route, module) -> {
            if (module.isOffIn(disabled)) routes.add(route);
        });
        return new HiddenNotifications(Set.copyOf(types), Set.copyOf(routes));
    }

    /**
     * Whether one notification is hidden.
     *
     * @param type the notification's type
     * @param link the notification's link, {@code null} where it carries none
     * @return {@code true} when it belongs to a switched-off module
     */
    public boolean hides(NotificationType type, NotificationData.@Nullable NotificationLink link) {
        if (types.contains(type)) return true;
        return type == NotificationType.COMMENT_MENTION && link != null && mentionRoutes.contains(link.route());
    }

    /**
     * Whether anything is hidden at all.
     *
     * @return {@code true} when nothing is
     */
    public boolean isEmpty() {
        return types.isEmpty() && mentionRoutes.isEmpty();
    }
}

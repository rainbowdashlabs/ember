/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.notifications.entity.DigestGroup;

import java.util.Map;
import java.util.Optional;

/**
 * The one table of the pages a notification can open, for stations and associations alike.
 *
 * <p>A link names a page by the route name the app knows it by, which is also what the in-app list
 * navigates to; a mail or a feed needs the address behind that name, which is this table. Each name
 * is the {@code name} of a page file in the app, and a test holds the two to each other, so a name
 * the app does not know cannot reach a reader.
 *
 * <p>A recurring event has a date-aware route so the reader lands on the right occurrence, and a
 * board ticket is addressed by the board's short key and the ticket's number on it rather than by
 * primary keys, so the handlers pass {@code boardKey} and {@code ticketNumber}.
 */
final class NotificationPages {
    private static final String STATION_LANDING = "/station/dashboard/overview";
    private static final String CLUSTER_LANDING = "/cluster";

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("news-list", "/station/news"),
            Map.entry("news-detail", "/station/news/{id}"),
            Map.entry("kb-file", "/station/knowledge/file/{id}"),
            Map.entry("events-registrations", "/station/events/registrations"),
            Map.entry("events-upcoming", "/station/events/upcoming"),
            Map.entry("event-detail", "/station/events/{id}"),
            Map.entry("event-detail-date", "/station/events/{id}/{date}"),
            Map.entry("forms-list", "/station/forms"),
            Map.entry("forms-fill", "/station/forms/{id}/fill"),
            Map.entry("inventory-movements", "/station/inventory/movements"),
            Map.entry("inventory-movement-detail", "/station/inventory/movement/{id}"),
            Map.entry("inventory-procurement", "/station/inventory/procurement"),
            Map.entry("inventory-lending-request", "/station/inventory/lending/request/{id}"),
            Map.entry("inventory-self-check", "/station/inventory/self-check/{id}"),
            Map.entry("inventory-self-check-review", "/station/inventory/checks/self/{id}"),
            Map.entry("members-detail", "/station/members/detail/{id}"),
            Map.entry("members-list", "/station/members/list"),
            Map.entry("member-documents", "/station/members/documents"),
            Map.entry("waiting-lists", "/station/members/waiting-lists"),
            Map.entry("profile", "/station/profile"),
            Map.entry("profile-managed", "/station/profile/managed"),
            Map.entry("dashboard-overview", STATION_LANDING),
            Map.entry("lost-and-found", "/station/lost-and-found"),
            Map.entry("procedure-list", "/station/procedures"),
            Map.entry("procedure-detail", "/station/procedures/{id}"),
            Map.entry("ticket-detail", "/station/boards/{boardKey}/tickets/{ticketNumber}"),
            Map.entry("station-manage-cluster", "/station/manage/cluster"),
            Map.entry("station-modules", "/station/manage/modules"),
            Map.entry("station-mail-import", "/station/manage/mail-import"),
            Map.entry("station-storage", "/station/monitoring/storage"),
            Map.entry("station-federation", "/station/federate"),
            Map.entry("cluster-overview", CLUSTER_LANDING),
            Map.entry("cluster-applications", "/cluster/applications"),
            Map.entry("cluster-members", "/cluster/members"),
            Map.entry("cluster-inventory", "/cluster/inventory"),
            Map.entry("cluster-movements", "/cluster/inventory/movements"));

    private NotificationPages() {}

    /**
     * The address behind a route name, with its parameters still as {@code {name}} placeholders.
     *
     * @param route the route name a link carries
     * @return the address, or empty for a name the table does not know
     */
    static Optional<String> pathOf(String route) {
        return Optional.ofNullable(PATHS.get(route));
    }

    /**
     * Where a link the table does not know leads instead: the start page of the area it was read in.
     *
     * @param kind station or association
     * @return the address of that start page
     */
    static String landingOf(DigestGroup.Kind kind) {
        return switch (kind) {
            case STATION -> STATION_LANDING;
            case CLUSTER -> CLUSTER_LANDING;
        };
    }

    /**
     * Every route name the table knows, with its address.
     *
     * @return the table
     */
    static Map<String, String> all() {
        return PATHS;
    }
}

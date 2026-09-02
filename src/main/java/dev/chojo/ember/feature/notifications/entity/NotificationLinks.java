/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;

import java.time.LocalDate;
import java.util.Map;

/**
 * The links that an announcement and its later withdrawal have to agree on.
 *
 * <p>A withdrawal finds its notifications by what they point at. Building the same link twice by
 * hand is how the two sides drift apart and a withdrawal comes to name something no notification
 * carries, so both sides take it from here.
 */
public final class NotificationLinks {
    private NotificationLinks() {}

    /**
     * The link to one news article.
     *
     * @param newsId the article
     * @return the link its notifications carry
     */
    public static NotificationLink news(int newsId) {
        return new NotificationLink("news-detail", Map.of("id", newsId));
    }

    /**
     * The link to one appointment.
     *
     * @param eventId the appointment
     * @return the link its notifications carry
     */
    public static NotificationLink event(int eventId) {
        return new NotificationLink("event-detail", Map.of("id", eventId));
    }

    /**
     * The link to one occasion of an appointment. The date rides along as a path segment, so that a
     * reminder about a repeating appointment opens the instance it is actually about.
     *
     * @param eventId the appointment
     * @param date    the day the reminder is about
     * @return the link its reminders carry
     */
    public static NotificationLink eventDate(int eventId, LocalDate date) {
        return new NotificationLink(
                "event-detail-date", Map.of("id", String.valueOf(eventId), "date", date.toString()));
    }

    /**
     * The same link with the date left out, which every reminder for that appointment carries in
     * full. Naming only the appointment is what lets a withdrawal reach all of them at once, so this
     * one is for matching and not for navigating.
     *
     * @param eventId the appointment
     * @return the part of the link every reminder for it shares
     */
    public static NotificationLink eventDates(int eventId) {
        return new NotificationLink("event-detail-date", Map.of("id", String.valueOf(eventId)));
    }

    /**
     * The link to one form. Its number travels as text, which is what the stored notifications say.
     *
     * @param formId the form
     * @return the link its notifications carry
     */
    public static NotificationLink form(int formId) {
        return new NotificationLink("forms-fill", Map.of("id", String.valueOf(formId)));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
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
    private static final String NEWS_DETAIL = "news-detail";
    private static final String EVENT_DETAIL = "event-detail";
    private static final String KB_FILE = "kb-file";
    private static final String TICKET_DETAIL = "ticket-detail";

    private NotificationLinks() {}

    /**
     * The link to one news article.
     *
     * @param newsId the article
     * @return the link its notifications carry
     */
    public static NotificationLink news(int newsId) {
        return new NotificationLink(NEWS_DETAIL, Map.of("id", newsId));
    }

    /**
     * The link to one appointment.
     *
     * @param eventId the appointment
     * @return the link its notifications carry
     */
    public static NotificationLink event(int eventId) {
        return new NotificationLink(EVENT_DETAIL, Map.of("id", eventId));
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

    /**
     * The link to one board ticket. The page is reached by the board and the number; the id rides
     * along beside them, which is what lets the feed renderer look the ticket up for its title,
     * assignee and priority.
     *
     * @param address  where the ticket's page is
     * @param ticketId the ticket
     * @return the link its notifications carry
     */
    public static NotificationLink ticket(BoardTicketAddress address, int ticketId) {
        return new NotificationLink(
                TICKET_DETAIL,
                Map.of(
                        "boardKey", address.boardKey(),
                        "ticketNumber", address.ticketNumber(),
                        "ticketId", ticketId));
    }

    /**
     * The link to one comment: the page it hangs under, plus the comment itself, so that opening
     * the notification lands on the comment instead of the top of a long list.
     *
     * @param entityType what the comment hangs under
     * @param entityId   the article, file, appointment or ticket
     * @param address    where the ticket's page is, for a comment on a ticket, and {@code null} for
     *                   everything else, which is reached by its id alone
     * @param commentId  the comment
     * @return the link its notifications carry
     */
    public static NotificationLink comment(
            CommentEntityType entityType, int entityId, BoardTicketAddress address, int commentId) {
        var page = commentPage(entityType, entityId, address);
        return new NotificationLink(page.route(), page.routeParams(), commentQuery(commentId));
    }

    /**
     * The same link with the page left out, which every notification about that comment carries in
     * full. A comment id is unique among the comments of its kind, so naming only the comment is
     * both enough to find them and narrow enough to leave the other comments of the same page
     * alone. This one is for matching and not for navigating.
     *
     * @param entityType what the comment hangs under
     * @param commentId  the comment
     * @return the part of the link every notification about it shares
     */
    public static NotificationLink commentAlone(CommentEntityType entityType, int commentId) {
        return new NotificationLink(commentRoute(entityType), Map.of(), commentQuery(commentId));
    }

    private static NotificationLink commentPage(
            CommentEntityType entityType, int entityId, BoardTicketAddress address) {
        return switch (entityType) {
            case NEWS -> news(entityId);
            case EVENT -> event(entityId);
            case KB -> new NotificationLink(KB_FILE, Map.of("id", entityId));
            case BOARD_TICKET -> ticket(address, entityId);
        };
    }

    private static Map<String, Object> commentQuery(int commentId) {
        return Map.of("comment", commentId);
    }

    private static String commentRoute(CommentEntityType entityType) {
        return switch (entityType) {
            case NEWS -> NEWS_DETAIL;
            case EVENT -> EVENT_DETAIL;
            case KB -> KB_FILE;
            case BOARD_TICKET -> TICKET_DETAIL;
        };
    }
}

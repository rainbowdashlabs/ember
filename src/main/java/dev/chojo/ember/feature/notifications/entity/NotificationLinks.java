/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
     * The member list narrowed to the members of one expiry date field whose date runs out or has
     * passed. The list reads the field and the states from its address when it opens.
     *
     * @param fieldId the expiry date field
     * @return the link member management's reminders carry
     */
    public static NotificationLink runningOut(int fieldId) {
        var query = new LinkedHashMap<String, Object>();
        query.put("field", fieldId);
        query.put("state", "expiring,expired");
        return new NotificationLink("members-list", Map.of(), query);
    }

    /**
     * The association's own list of the members at its stations.
     *
     * @return the link an association's reminders about its members carry
     */
    public static NotificationLink clusterMembers() {
        return new NotificationLink("cluster-members", Map.of());
    }

    /**
     * The association's own people, the ones who act for it, with the requests it sent.
     *
     * @return the link the news about its own people carries
     */
    public static NotificationLink clusterTeam() {
        return new NotificationLink("cluster-team", Map.of());
    }

    /**
     * The page of one lending request between two stations, for whichever side is told.
     *
     * @param requestId the lending request
     * @return the link its notifications carry
     */
    public static NotificationLink lendingRequest(int requestId) {
        return new NotificationLink("inventory-lending-request", Map.of("id", requestId));
    }

    /**
     * The station's document store, where mail that arrived without a member waits to be filed.
     *
     * @return the link the mail import's notices carry
     */
    public static NotificationLink documentStore() {
        return new NotificationLink("documents-store", Map.of());
    }

    /**
     * The station's storage page, which shows how much of its space is used.
     *
     * @return the link a storage warning carries
     */
    public static NotificationLink stationStorage() {
        return new NotificationLink("station-storage", Map.of());
    }

    /**
     * The reader's own profile, which is where a member renews a date of their own.
     *
     * @return the link a member's own reminders carry
     */
    public static NotificationLink ownProfile() {
        return new NotificationLink("profile", Map.of());
    }

    /**
     * The page on which a guardian fills in the profile of a member in their care, opened on that
     * member.
     *
     * @param memberId the member in their care
     * @return the link a guardian's copy of a reminder carries
     */
    public static NotificationLink managedProfile(int memberId) {
        return new NotificationLink("profile-managed", Map.of(), Map.of("member", memberId));
    }

    /**
     * The member's own page in the member management.
     *
     * @param memberId the member
     * @return the link a notification about the member carries
     */
    public static NotificationLink member(int memberId) {
        return new NotificationLink("members-detail", Map.of("id", memberId));
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
     * The link to one knowledge base file.
     *
     * @param fileId the file
     * @return the link its notifications carry
     */
    public static NotificationLink kbFile(int fileId) {
        return new NotificationLink(KB_FILE, Map.of("id", fileId));
    }

    /**
     * The link to one comment: the page it hangs under, plus the comment itself, so that opening
     * the notification lands on the comment instead of the top of a long list. Which page that is
     * the comment's target decides.
     *
     * @param page      the page of the article, file, appointment or ticket the comment hangs under
     * @param commentId the comment
     * @return the link its notifications carry
     */
    public static NotificationLink comment(NotificationLink page, int commentId) {
        return new NotificationLink(page.route(), page.routeParams(), Map.of("comment", commentId));
    }

    /**
     * The same link with the page's parameters left out, which every notification about that
     * comment carries in full. A comment id is unique among all comments, so naming only the
     * comment is both enough to find them and narrow enough to leave the other comments of the same
     * page alone, also after the page's own address changed. This one is for matching and not for
     * navigating.
     *
     * @param link the link to the comment
     * @return the part of the link every notification about it shares
     */
    public static NotificationLink commentAlone(NotificationLink link) {
        return new NotificationLink(link.route(), Map.of(), link.query());
    }

    /**
     * The federation page of the station told, where its partners and its requests to federate are
     * listed.
     *
     * @return the link its notifications carry
     */
    public static NotificationLink federation() {
        return new NotificationLink("station-federation", Map.of());
    }

    /**
     * The page on which member changes are confirmed, where open name requests wait.
     *
     * @return the link a name request carries
     */
    public static NotificationLink memberChanges() {
        return new NotificationLink("members-changes", Map.of());
    }

    /**
     * The reader's own account profile, where their register name is shown and changed.
     *
     * @return the link a decision on their name request carries
     */
    public static NotificationLink ownAccountProfile() {
        return new NotificationLink("account-avatar", Map.of());
    }

    /**
     * The signing screen of one signature field, which a request and its reminders open and which their
     * withdrawal names once the field is settled.
     *
     * @param fieldId the signature field
     * @return the link its notifications carry
     */
    public static NotificationLink signingField(int fieldId) {
        return new NotificationLink("station-signing", Map.of("fieldId", fieldId));
    }

    /**
     * The reader's own documents, followed by those of every member in their care.
     *
     * @return the link a signed copy leads to
     */
    public static NotificationLink ownDocuments() {
        return new NotificationLink("documents-own", Map.of());
    }

    /**
     * One document among the reader's own documents, opened with its sealed versions.
     *
     * @param documentId the document
     * @return the link a signed copy leads to
     */
    public static NotificationLink ownDocument(int documentId) {
        return new NotificationLink("documents-own", Map.of(), Map.of("document", documentId));
    }

    /**
     * The readable record of one sealed version of a document among the reader's own documents.
     *
     * @param documentId the document
     * @param version    the version's number
     * @return the link a signed copy's record leads to
     */
    public static NotificationLink ownDocumentRecord(int documentId, int version) {
        var query = new LinkedHashMap<String, Object>();
        query.put("document", documentId);
        query.put("record", version);
        return new NotificationLink("documents-own", Map.of(), query);
    }
}

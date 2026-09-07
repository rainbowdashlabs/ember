/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.StepActor;

import java.time.LocalDate;

/**
 * Sealed interface for type-safe notification parameters.
 * Each notification type has a corresponding record with its specific fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface NotificationParams {

    record NewNews(String title, String author, String preview) implements NotificationParams {}

    record NewsComment(String newsTitle, String author, String preview) implements NotificationParams {}

    record CommentMention(String entityTitle, String author, String preview) implements NotificationParams {}

    record NewEvent(String title, String eventDescription) implements NotificationParams {}

    /**
     * Aggregated notification for bulk event creation. Carries the total count and a short
     * comma-separated preview of the first few event names so the feed body can summarise.
     */
    record NewEventsBatch(int count, String eventPreview, LocalDate firstEventDate) implements NotificationParams {}

    record EventRegistrationStatus(String eventName, RegistrationStatus status, String eventDescription)
            implements NotificationParams {}

    record ExchangeNewRequest(String memberName, String inventoryName, String reason) implements NotificationParams {}

    /**
     * A movement has moved on. The step label is the flow's own words rather than a fixed status,
     * because the chain a station walks is its own to name, and the next actor says whose turn it
     * is now: this notification is only ever sent to that party, so its presence is the signal.
     */
    record ExchangeStatusChange(String stepLabel, String inventoryName, StepActor nextActor)
            implements NotificationParams {}

    record MovementDeclined(String inventoryName, String reason) implements NotificationParams {}

    /**
     * Somebody called a movement off. Where the piece ended up cannot be worked out from that fact
     * alone, so it is carried: called off before the handover it is back with whoever sent it, called
     * off while it was in the post it stayed where it had got to.
     */
    record MovementCancelled(String inventoryName, String itemName, String reason, boolean itemStayedAway)
            implements NotificationParams {}

    /**
     * A station has asked to join a cluster. Named by station, because the cluster reading it knows which
     * cluster it is.
     */
    record ClusterApplicationSubmitted(String stationName) implements NotificationParams {}

    record ClusterApplicationApproved(String clusterName) implements NotificationParams {}

    record ClusterApplicationDenied(String clusterName, String reason) implements NotificationParams {}

    record ClusterApplicationWithdrawn(String stationName) implements NotificationParams {}

    record ClusterStationReleased(String clusterName) implements NotificationParams {}

    /** The module name travels as a string, because the reader sees a label rather than an enum. */
    record ClusterModuleDenied(String clusterName, String module) implements NotificationParams {}

    /** The quota is already formatted, because a byte count is not what somebody wants to read. */
    record ClusterQuotaChanged(String clusterName, String quota) implements NotificationParams {}

    /** What is coming and who sent it. The station has nothing to answer yet, so no step is named. */
    record ClusterItemIssued(String clusterName, String itemName) implements NotificationParams {}

    /** The station is named because the cluster's gear is spread over several of them. */
    record ClusterItemLost(String itemName, String stationName) implements NotificationParams {}

    /**
     * Says only that something moved. Naming the permission would mean listing a set that may have changed in
     * four ways at once, and the screen it links to shows the answer anyway.
     */
    record ClusterMemberRoleChanged(String clusterName) implements NotificationParams {}

    /** The field names arrive already joined, because the reader wants a sentence and not a list. */
    record ClusterFieldValueChanged(String clusterName, String fields) implements NotificationParams {}

    record MemberAddedToGroup(String groupName, String addedByName) implements NotificationParams {}

    record ProfileFieldChanged(String memberName, String fieldName) implements NotificationParams {}

    record ProcurementRequested(String inventoryName) implements NotificationParams {}

    record ProcurementFulfilled(String inventoryName) implements NotificationParams {}

    record NewForm(String title) implements NotificationParams {}

    record BoardTicketUpdate(String boardName, String ticketKey, String changeDescription)
            implements NotificationParams {}

    record LostAndFoundNew(String description) implements NotificationParams {}

    record LostAndFoundClaimed(String name, String description) implements NotificationParams {}

    record WaitlistNewEntry(String childName, String listName) implements NotificationParams {}

    record LendingNewRequest(String stationName, String itemSummary) implements NotificationParams {}

    record LendingStatusChange(String stationName, LendingStatus status) implements NotificationParams {}

    record LendingNewMessage(String stationName, String senderName) implements NotificationParams {}

    record RegistrationDeadlineExpired(String eventName, int pendingCount) implements NotificationParams {}

    record EventCancelled(String eventName, String reason) implements NotificationParams {}

    record EventReminder(String eventName, int daysBefore, LocalDate eventDate) implements NotificationParams {}

    /**
     * Registration for an event is about to close and this member has not said whether they are coming.
     *
     * @param memberName whose answer is missing, which is the reader themselves or somebody they look
     *                   after; a guardian needs to know which of their children it is about
     */
    record RegistrationClosing(String eventName, int daysBefore, String memberName) implements NotificationParams {}

    record ProcedureAssigned(String procedureName, String assignedByName) implements NotificationParams {}

    record ProcedureResolvedParams(String procedureName) implements NotificationParams {}

    record ProcedureReopenedParams(String procedureName) implements NotificationParams {}

    record ProcedureItemCheckedParams(String procedureName, String itemTitle, String checkedByName)
            implements NotificationParams {}

    /**
     * A member has been asked to answer for the gear recorded against their name.
     *
     * @param memberName  whose gear it is about, which is the reader or somebody they look after
     * @param handedOutBy who asked
     * @param dueOn       the day the answer is wanted by, or an empty string where none was named
     */
    record SelfCheckAssigned(String memberName, String handedOutBy, String dueOn) implements NotificationParams {}

    /**
     * A member has handed their answers in and somebody with the check permission has to read them.
     *
     * @param memberName whose gear it is about
     * @param answeredBy who entered the answers, which is the member or one of their guardians
     */
    record SelfCheckSubmitted(String memberName, String answeredBy) implements NotificationParams {}

    /**
     * One answer could not be settled and has come back to the member with a reason.
     *
     * @param memberName whose gear it is about
     * @param itemName   the piece or the kind of gear the answer was about
     * @param reason     what the reviewer wrote
     */
    record SelfCheckRowRefused(String memberName, String itemName, String reason) implements NotificationParams {}

    record WaitlistPublicRegistration(String childName, String listName) implements NotificationParams {}

    record WaitlistInvitationAnswered(String childName, String listName, String answer) implements NotificationParams {}

    record StorageWarning(int usedPercent, String usedFormatted, String quotaFormatted) implements NotificationParams {}
}

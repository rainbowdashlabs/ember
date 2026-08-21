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

    /**
     * Says only that something moved. Naming the permission would mean listing a set that may have changed in
     * four ways at once, and the screen it links to shows the answer anyway.
     */
    record ClusterMemberRoleChanged(String clusterName) implements NotificationParams {}

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

    record ProcedureAssigned(String procedureName, String assignedByName) implements NotificationParams {}

    record ProcedureResolvedParams(String procedureName) implements NotificationParams {}

    record ProcedureReopenedParams(String procedureName) implements NotificationParams {}

    record ProcedureItemCheckedParams(String procedureName, String itemTitle, String checkedByName)
            implements NotificationParams {}

    record WaitlistPublicRegistration(String childName, String listName) implements NotificationParams {}

    record StorageWarning(int usedPercent, String usedFormatted, String quotaFormatted) implements NotificationParams {}
}

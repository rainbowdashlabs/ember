/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;

/**
 * Sealed interface for type-safe notification parameters.
 * Each notification type has a corresponding record with its specific fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface NotificationParams {

    record NewNews(String title, String author, String preview) implements NotificationParams {}

    record NewsComment(String newsTitle, String author, String preview) implements NotificationParams {}

    record CommentMention(String entityTitle, String author) implements NotificationParams {}

    record NewEvent(String title, String eventDescription) implements NotificationParams {}

    record EventRegistrationStatus(String eventName, RegistrationStatus status, String eventDescription)
            implements NotificationParams {}

    record ExchangeNewRequest(String memberName, String inventoryName, String reason) implements NotificationParams {}

    record ExchangeStatusChange(ExchangeStatus status, String inventoryName, String reason)
            implements NotificationParams {}

    record MemberAddedToGroup(String groupName) implements NotificationParams {}

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

    record EventReminder(String eventName, int daysBefore, String eventDate) implements NotificationParams {}

    record ProcedureAssigned(String procedureName, String assignedByName) implements NotificationParams {}

    record ProcedureResolvedParams(String procedureName) implements NotificationParams {}

    record ProcedureReopenedParams(String procedureName) implements NotificationParams {}

    record ProcedureItemCheckedParams(String procedureName, String itemTitle, String checkedByName)
            implements NotificationParams {}
}

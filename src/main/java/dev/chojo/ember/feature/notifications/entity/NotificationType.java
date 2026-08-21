/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Enumerates the categories of notifications that can be sent to members.
 * Each type is associated with a {@link NotificationParams} record that defines
 * its expected parameter shape, and a locale key for frontend i18n.
 */
public enum NotificationType {
    NEW_NEWS(NotificationParams.NewNews.class, "notification.newNews"),
    NEWS_COMMENT(NotificationParams.NewsComment.class, "notification.newsComment"),
    COMMENT_MENTION(NotificationParams.CommentMention.class, "notification.commentMention"),
    EVENT_REGISTRATION_STATUS(NotificationParams.EventRegistrationStatus.class, "notification.eventRegistrationStatus"),
    EXCHANGE_STATUS_CHANGE(NotificationParams.ExchangeStatusChange.class, "notification.exchangeStatusChange"),
    EXCHANGE_NEW_REQUEST(NotificationParams.ExchangeNewRequest.class, "notification.exchangeNewRequest"),
    MOVEMENT_DECLINED(NotificationParams.MovementDeclined.class, "notification.movementDeclined"),
    NEW_EVENT(NotificationParams.NewEvent.class, "notification.newEvent"),
    NEW_EVENTS_BATCH(NotificationParams.NewEventsBatch.class, "notification.newEventsBatch"),
    MEMBER_ADDED_TO_GROUP(NotificationParams.MemberAddedToGroup.class, "notification.memberAddedToGroup"),
    PROFILE_FIELD_CHANGED(NotificationParams.ProfileFieldChanged.class, "notification.profileFieldChanged"),
    PROCUREMENT_REQUESTED(NotificationParams.ProcurementRequested.class, "notification.procurementRequested"),
    PROCUREMENT_FULFILLED(NotificationParams.ProcurementFulfilled.class, "notification.procurementFulfilled"),
    NEW_FORM(NotificationParams.NewForm.class, "notification.newForm"),
    LOST_AND_FOUND_NEW(NotificationParams.LostAndFoundNew.class, "notification.lostAndFoundNew"),
    LOST_AND_FOUND_CLAIMED(NotificationParams.LostAndFoundClaimed.class, "notification.lostAndFoundClaimed"),
    WAITLIST_NEW_ENTRY(NotificationParams.WaitlistNewEntry.class, "notification.waitlistNewEntry"),
    LENDING_NEW_REQUEST(NotificationParams.LendingNewRequest.class, "notification.lendingNewRequest"),
    LENDING_STATUS_CHANGE(NotificationParams.LendingStatusChange.class, "notification.lendingStatusChange"),
    LENDING_NEW_MESSAGE(NotificationParams.LendingNewMessage.class, "notification.lendingNewMessage"),
    BOARD_TICKET_UPDATE(NotificationParams.BoardTicketUpdate.class, "notification.boardTicketUpdate"),
    REGISTRATION_DEADLINE_EXPIRED(
            NotificationParams.RegistrationDeadlineExpired.class, "notification.registrationDeadlineExpired"),
    EVENT_CANCELLED(NotificationParams.EventCancelled.class, "notification.eventCancelled"),
    EVENT_REMINDER(NotificationParams.EventReminder.class, "notification.eventReminder"),
    PROCEDURE_ASSIGNED(NotificationParams.ProcedureAssigned.class, "notification.procedureAssigned"),
    PROCEDURE_RESOLVED(NotificationParams.ProcedureResolvedParams.class, "notification.procedureResolved"),
    PROCEDURE_REOPENED(NotificationParams.ProcedureReopenedParams.class, "notification.procedureReopened"),
    PROCEDURE_ITEM_CHECKED(NotificationParams.ProcedureItemCheckedParams.class, "notification.procedureItemChecked"),
    WAITLIST_PUBLIC_REGISTRATION(
            NotificationParams.WaitlistPublicRegistration.class, "notification.waitlistPublicRegistration"),
    STORAGE_WARNING(NotificationParams.StorageWarning.class, "notification.storageWarning"),
    CLUSTER_APPLICATION_SUBMITTED(
            NotificationParams.ClusterApplicationSubmitted.class, "notification.clusterApplicationSubmitted"),
    CLUSTER_APPLICATION_APPROVED(
            NotificationParams.ClusterApplicationApproved.class, "notification.clusterApplicationApproved"),
    CLUSTER_APPLICATION_DENIED(
            NotificationParams.ClusterApplicationDenied.class, "notification.clusterApplicationDenied"),
    CLUSTER_APPLICATION_WITHDRAWN(
            NotificationParams.ClusterApplicationWithdrawn.class, "notification.clusterApplicationWithdrawn"),
    CLUSTER_STATION_RELEASED(NotificationParams.ClusterStationReleased.class, "notification.clusterStationReleased"),
    CLUSTER_MODULE_DENIED(NotificationParams.ClusterModuleDenied.class, "notification.clusterModuleDenied"),
    CLUSTER_QUOTA_CHANGED(NotificationParams.ClusterQuotaChanged.class, "notification.clusterQuotaChanged");

    private final Class<? extends NotificationParams> paramsType;
    private final String localeKey;

    NotificationType(Class<? extends NotificationParams> paramsType, String localeKey) {
        this.paramsType = paramsType;
        this.localeKey = localeKey;
    }

    public Class<? extends NotificationParams> paramsType() {
        return paramsType;
    }

    public String localeKey() {
        return localeKey;
    }
}

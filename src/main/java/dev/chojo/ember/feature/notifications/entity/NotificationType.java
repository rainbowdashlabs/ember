/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.station.entity.StationModule;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Enumerates the categories of notifications that can be sent to members.
 * Each type is associated with a {@link NotificationParams} record that defines
 * its expected parameter shape, and a locale key for frontend i18n.
 *
 * <p>A type of a module names it. While that module is off at a station, its notifications there are
 * kept but neither shown, counted, mailed nor written, and the settings page does not offer the type.
 * Types of the account, the station's members, clusters, federation and the system name none.
 */
public enum NotificationType {
    NEW_NEWS(NotificationParams.NewNews.class, "notification.newNews", StationModule.NEWS),
    NEWS_COMMENT(NotificationParams.NewsComment.class, "notification.newsComment", StationModule.NEWS),
    COMMENT_MENTION(NotificationParams.CommentMention.class, "notification.commentMention"),
    EVENT_COMMENT(NotificationParams.EventComment.class, "notification.eventComment", StationModule.EVENTS),
    EVENT_REGISTRATION_STATUS(
            NotificationParams.EventRegistrationStatus.class,
            "notification.eventRegistrationStatus",
            StationModule.EVENTS),
    MOVEMENT_ADVANCED(
            NotificationParams.MovementMoved.class, "notification.movementAdvanced", StationModule.INVENTORY_MOVEMENTS),
    MOVEMENT_RAISED(
            NotificationParams.MovementRaised.class, "notification.movementRaised", StationModule.INVENTORY_MOVEMENTS),
    MOVEMENT_DECLINED(
            NotificationParams.MovementDeclined.class,
            "notification.movementDeclined",
            StationModule.INVENTORY_MOVEMENTS),
    MOVEMENT_CANCELLED(
            NotificationParams.MovementCancelled.class,
            "notification.movementCancelled",
            StationModule.INVENTORY_MOVEMENTS),
    NEW_EVENT(NotificationParams.NewEvent.class, "notification.newEvent", StationModule.EVENTS),
    NEW_EVENTS_BATCH(NotificationParams.NewEventsBatch.class, "notification.newEventsBatch", StationModule.EVENTS),
    MEMBER_ADDED_TO_GROUP(NotificationParams.MemberAddedToGroup.class, "notification.memberAddedToGroup"),
    PROFILE_FIELD_CHANGED(NotificationParams.ProfileFieldChanged.class, "notification.profileFieldChanged"),
    PROCUREMENT_REQUESTED(
            NotificationParams.ProcurementRequested.class,
            "notification.procurementRequested",
            StationModule.INVENTORY_PROCUREMENT),
    PROCUREMENT_FULFILLED(
            NotificationParams.ProcurementFulfilled.class,
            "notification.procurementFulfilled",
            StationModule.INVENTORY_PROCUREMENT),
    NEW_FORM(NotificationParams.NewForm.class, "notification.newForm", StationModule.FORMS),
    LOST_AND_FOUND_NEW(
            NotificationParams.LostAndFoundNew.class, "notification.lostAndFoundNew", StationModule.LOST_AND_FOUND),
    LOST_AND_FOUND_CLAIMED(
            NotificationParams.LostAndFoundClaimed.class,
            "notification.lostAndFoundClaimed",
            StationModule.LOST_AND_FOUND),
    WAITLIST_NEW_ENTRY(
            NotificationParams.WaitlistNewEntry.class, "notification.waitlistNewEntry", StationModule.WAITING_LIST),
    LENDING_NEW_REQUEST(
            NotificationParams.LendingNewRequest.class,
            "notification.lendingNewRequest",
            StationModule.INVENTORY_LENDING),
    LENDING_STATUS_CHANGE(
            NotificationParams.LendingStatusChange.class,
            "notification.lendingStatusChange",
            StationModule.INVENTORY_LENDING),
    LENDING_NEW_MESSAGE(
            NotificationParams.LendingNewMessage.class,
            "notification.lendingNewMessage",
            StationModule.INVENTORY_LENDING),
    BOARD_TICKET_UPDATE(
            NotificationParams.BoardTicketUpdate.class, "notification.boardTicketUpdate", StationModule.BOARDS),
    REGISTRATION_DEADLINE_EXPIRED(
            NotificationParams.RegistrationDeadlineExpired.class,
            "notification.registrationDeadlineExpired",
            StationModule.EVENTS),
    EVENT_CANCELLED(NotificationParams.EventCancelled.class, "notification.eventCancelled", StationModule.EVENTS),
    EVENT_REMINDER(NotificationParams.EventReminder.class, "notification.eventReminder", StationModule.EVENTS),
    REGISTRATION_CLOSING(
            NotificationParams.RegistrationClosing.class, "notification.registrationClosing", StationModule.EVENTS),
    REGISTRATION_ANSWER_MISSING(
            NotificationParams.RegistrationAnswerMissing.class,
            "notification.registrationAnswerMissing",
            StationModule.EVENTS),
    EVENT_DATE_DROPPED(
            NotificationParams.EventDateDropped.class, "notification.eventDateDropped", StationModule.EVENTS),
    EVENT_MOVED(NotificationParams.EventMoved.class, "notification.eventMoved", StationModule.EVENTS),
    EVENT_DATE_RESTORED(
            NotificationParams.EventDateRestored.class, "notification.eventDateRestored", StationModule.EVENTS),
    EVENT_CHANGED(NotificationParams.EventChangeNotice.class, "notification.eventChanged", StationModule.EVENTS),
    PROCEDURE_ASSIGNED(
            NotificationParams.ProcedureAssigned.class, "notification.procedureAssigned", StationModule.PROCEDURES),
    PROCEDURE_RESOLVED(
            NotificationParams.ProcedureResolvedParams.class,
            "notification.procedureResolved",
            StationModule.PROCEDURES),
    PROCEDURE_REOPENED(
            NotificationParams.ProcedureReopenedParams.class,
            "notification.procedureReopened",
            StationModule.PROCEDURES),
    PROCEDURE_ITEM_CHECKED(
            NotificationParams.ProcedureItemCheckedParams.class,
            "notification.procedureItemChecked",
            StationModule.PROCEDURES),
    PROCEDURE_DOCUMENT_WITHDRAWN(
            NotificationParams.ProcedureDocumentWithdrawnParams.class,
            "notification.procedureDocumentWithdrawn",
            StationModule.PROCEDURES),
    SELF_CHECK_ASSIGNED(
            NotificationParams.SelfCheckAssigned.class,
            "notification.selfCheckAssigned",
            StationModule.INVENTORY_SELF_CHECKS),
    SELF_CHECK_SUBMITTED(
            NotificationParams.SelfCheckSubmitted.class,
            "notification.selfCheckSubmitted",
            StationModule.INVENTORY_SELF_CHECKS),
    SELF_CHECK_ROW_REFUSED(
            NotificationParams.SelfCheckRowRefused.class,
            "notification.selfCheckRowRefused",
            StationModule.INVENTORY_SELF_CHECKS),
    WAITLIST_PUBLIC_REGISTRATION(
            NotificationParams.WaitlistPublicRegistration.class,
            "notification.waitlistPublicRegistration",
            StationModule.WAITING_LIST),
    WAITLIST_INVITATION_ANSWERED(
            NotificationParams.WaitlistInvitationAnswered.class,
            "notification.waitlistInvitationAnswered",
            StationModule.WAITING_LIST),
    WAITLIST_TRIAL_ENDED(
            NotificationParams.WaitlistTrialEnded.class, "notification.waitlistTrialEnded", StationModule.WAITING_LIST),
    STORAGE_WARNING(NotificationParams.StorageWarning.class, "notification.storageWarning"),
    MAILBOX_SUSPENDED(
            NotificationParams.MailboxSuspended.class, "notification.mailboxSuspended", StationModule.DOCUMENTS),
    MAIL_IMPORT_UNBOUND(
            NotificationParams.MailImportUnbound.class, "notification.mailImportUnbound", StationModule.DOCUMENTS),
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
    CLUSTER_ITEM_ISSUED(
            NotificationParams.ClusterItemIssued.class,
            "notification.clusterItemIssued",
            StationModule.INVENTORY_MOVEMENTS),
    CLUSTER_ITEM_LOST(NotificationParams.ClusterItemLost.class, "notification.clusterItemLost"),
    CLUSTER_QUOTA_CHANGED(NotificationParams.ClusterQuotaChanged.class, "notification.clusterQuotaChanged"),
    CLUSTER_MEMBER_ROLE_CHANGED(
            NotificationParams.ClusterMemberRoleChanged.class, "notification.clusterMemberRoleChanged"),
    CLUSTER_FIELD_VALUE_CHANGED(
            NotificationParams.ClusterFieldValueChanged.class, "notification.clusterFieldValueChanged"),
    EXPIRY_REMINDER(NotificationParams.ExpiryReminder.class, "notification.expiryReminder"),
    FEDERATION_REQUEST_RECEIVED(
            NotificationParams.FederationRequestReceived.class, "notification.federationRequestReceived"),
    FEDERATION_REQUEST_ACCEPTED(
            NotificationParams.FederationRequestAnswered.class, "notification.federationRequestAccepted"),
    FEDERATION_REQUEST_DECLINED(
            NotificationParams.FederationRequestAnswered.class, "notification.federationRequestDeclined"),
    NAME_CHANGE_REQUESTED(NotificationParams.NameChangeRequested.class, "notification.nameChangeRequested"),
    NAME_CHANGE_APPROVED(NotificationParams.NameChangeApproved.class, "notification.nameChangeApproved"),
    NAME_CHANGE_DENIED(NotificationParams.NameChangeDenied.class, "notification.nameChangeDenied"),
    SIGNATURE_REQUESTED(NotificationParams.SignatureRequested.class, "notification.signatureRequested"),
    SIGNATURE_REMINDER(NotificationParams.SignatureRequested.class, "notification.signatureReminder"),
    DOCUMENT_SIGNED(NotificationParams.DocumentSigned.class, "notification.documentSigned"),
    DOCUMENT_SCAN_REJECTED(
            NotificationParams.DocumentScanRejected.class,
            "notification.documentScanRejected",
            StationModule.DOCUMENTS),
    ACCOUNT_LINK_ACCEPTED(NotificationParams.AccountLinkAccepted.class, "notification.accountLinkAccepted"),
    ASSOCIATION_LINK_ACCEPTED(NotificationParams.AssociationLinkAccepted.class, "notification.associationLinkAccepted"),
    SIGNATURE_WITHDRAWN(NotificationParams.SignatureWithdrawn.class, "notification.signatureWithdrawn"),
    PARTNER_SIGNATURE_WITHDRAWN(
            NotificationParams.PartnerSignatureWithdrawn.class, "notification.partnerSignatureWithdrawn");

    private final Class<? extends NotificationParams> paramsType;
    private final String localeKey;
    private final @Nullable StationModule module;

    NotificationType(Class<? extends NotificationParams> paramsType, String localeKey) {
        this.paramsType = paramsType;
        this.localeKey = localeKey;
        this.module = null;
    }

    NotificationType(Class<? extends NotificationParams> paramsType, String localeKey, StationModule module) {
        this.paramsType = paramsType;
        this.localeKey = localeKey;
        this.module = module;
    }

    /**
     * The module this type of notification belongs to.
     *
     * @return the module or part of one, empty for a type that belongs to none or, like a mention, takes
     *     its module from what it links to
     */
    public Optional<StationModule> module() {
        return Optional.ofNullable(module);
    }

    /**
     * The module one notification of this type belongs to. A mention belongs to the module of the page
     * it was made on, which its link names.
     *
     * @param link the notification's link, {@code null} where it carries none
     * @return the module, empty for a notification of no module
     */
    public Optional<StationModule> moduleOf(NotificationData.@Nullable NotificationLink link) {
        if (this == COMMENT_MENTION) return link == null ? Optional.empty() : NotificationLinks.moduleOf(link);
        return module();
    }

    public Class<? extends NotificationParams> paramsType() {
        return paramsType;
    }

    public String localeKey() {
        return localeKey;
    }
}

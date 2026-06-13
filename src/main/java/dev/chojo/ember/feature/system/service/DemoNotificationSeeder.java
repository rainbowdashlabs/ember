/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Seeds one notification of every {@link NotificationType} for the demo admin so all 29
 * categories can be reviewed in the dashboard and atom feed at a glance. The rest of the
 * demo's notifications are produced organically by the other seeders' service calls
 * (newsService, eventService, exchangeService, …) — this seeder doesn't compete with them
 * because admin already gets the organic notifications too.
 *
 * <p>Each entry uses representative link metadata so the renderer's per-type enrichment
 * fires (event/lost-and-found/lending/inventory/procedure/board ticket lookups). The entity
 * ids are taken from real seeded records so deep links resolve.
 */
@Singleton
public class DemoNotificationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoNotificationSeeder.class);

    private final NotificationRepository notificationRepository;

    @Inject
    public DemoNotificationSeeder(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public record ShowcaseContext(
            Integer newsId,
            Integer oneTimeEventId,
            Integer recurringEventId,
            String recurringEventDate,
            Integer formId,
            Integer lostAndFoundItemId,
            Integer lendingRequestId,
            Integer boardId,
            String boardKey,
            Integer boardTicketId,
            Integer boardTicketNumber,
            Integer procedureId,
            Integer inventoryId,
            Integer waitlistChildId,
            Integer stationIdForStorage) {}

    public void seedShowcase(StationMember admin, List<StationMember> anfaenger, ShowcaseContext ctx) {
        int memberId = admin.id();
        int otherMemberId =
                anfaenger.isEmpty() ? memberId : anfaenger.getFirst().id();

        seedNewsCategory(memberId, ctx);
        seedEventCategory(memberId, ctx);
        seedInventoryCategory(memberId, ctx);
        seedSocialCategory(memberId, otherMemberId, ctx);
        seedLendingCategory(memberId, ctx);
        seedBoardAndProcedureCategory(memberId, ctx);
        seedMiscCategory(memberId, otherMemberId, ctx);

        log.info("Demo: Seeded showcase notification for every NotificationType");
    }

    private void seedNewsCategory(int memberId, ShowcaseContext ctx) {
        var newsLink = ctx.newsId() != null
                ? new NotificationData.NotificationLink("news-detail", Map.of("id", ctx.newsId()))
                : new NotificationData.NotificationLink("news-list");

        notificationRepository.create(
                memberId,
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        new NotificationParams.NewNews(
                                "Übungsplan Q3 veröffentlicht",
                                "Alice Müller",
                                "Ab nächster Woche rotieren wir Dienstag und Donnerstag — der vollständige Plan steht im Beitrag."),
                        newsLink));

        notificationRepository.create(
                memberId,
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        new NotificationParams.NewsComment(
                                "Übungsplan Q3 veröffentlicht",
                                "Bob Schmidt",
                                "Ich bin am Dienstag etwas später dran, geht das in Ordnung?"),
                        newsLink));

        notificationRepository.create(
                memberId,
                NotificationType.COMMENT_MENTION,
                NotificationData.of(
                        new NotificationParams.CommentMention(
                                "Übungsplan Q3 veröffentlicht",
                                "Charlie Becker",
                                "@Admin könnt ihr die Materialien für Übung 3 vorbereiten?"),
                        newsLink));
    }

    private void seedEventCategory(int memberId, ShowcaseContext ctx) {
        Integer oneTime = ctx.oneTimeEventId();
        Integer recurring = ctx.recurringEventId();
        String recurringDate = ctx.recurringEventDate();
        var oneTimeLink = oneTime != null
                ? new NotificationData.NotificationLink("event-detail", Map.of("id", oneTime))
                : new NotificationData.NotificationLink("events-upcoming");
        var recurringLink = recurring != null && recurringDate != null
                ? new NotificationData.NotificationLink(
                        "event-detail-date", Map.of("id", recurring, "date", recurringDate))
                : oneTimeLink;

        notificationRepository.create(
                memberId,
                NotificationType.NEW_EVENT,
                NotificationData.of(
                        new NotificationParams.NewEvent(
                                "Offenes Training",
                                "Übung für alle Altersgruppen — Treffpunkt am Marktplatz, bitte rechtzeitig erscheinen."),
                        oneTimeLink));

        notificationRepository.create(
                memberId,
                NotificationType.NEW_EVENTS_BATCH,
                NotificationData.of(
                        new NotificationParams.NewEventsBatch(
                                3,
                                "Offenes Training, Übungstag, Sommerfest",
                                LocalDate.now().plusDays(7)),
                        new NotificationData.NotificationLink("events-upcoming")));

        notificationRepository.create(
                memberId,
                NotificationType.EVENT_REGISTRATION_STATUS,
                NotificationData.of(
                        new NotificationParams.EventRegistrationStatus(
                                "Offenes Training", RegistrationStatus.ACCEPTED, "Übung für alle Altersgruppen"),
                        oneTimeLink));

        notificationRepository.create(
                memberId,
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled("Offenes Training", "Schneesturm angekündigt"),
                        oneTimeLink));

        notificationRepository.create(
                memberId,
                NotificationType.EVENT_REMINDER,
                NotificationData.of(
                        new NotificationParams.EventReminder(
                                "Offenes Training", 1, LocalDate.now().plusDays(1)),
                        recurringLink));

        notificationRepository.create(
                memberId,
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                NotificationData.of(
                        new NotificationParams.RegistrationDeadlineExpired("Offenes Training", 4), oneTimeLink));
    }

    private void seedInventoryCategory(int memberId, ShowcaseContext ctx) {
        var exchangeLink = ctx.inventoryId() != null
                ? new NotificationData.NotificationLink("inventory-exchanges", Map.of("id", ctx.inventoryId()))
                : new NotificationData.NotificationLink("inventory-exchanges");
        var procurementLink = ctx.inventoryId() != null
                ? new NotificationData.NotificationLink("inventory-procurement", Map.of("id", ctx.inventoryId()))
                : new NotificationData.NotificationLink("inventory-procurement");

        notificationRepository.create(
                memberId,
                NotificationType.EXCHANGE_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.ExchangeNewRequest(
                                "Tim Berger", "Blouson Größe 152", "Zu klein geworden"),
                        exchangeLink));

        notificationRepository.create(
                memberId,
                NotificationType.EXCHANGE_STATUS_CHANGE,
                NotificationData.of(
                        new NotificationParams.ExchangeStatusChange(
                                ExchangeStatus.DONE, "Blouson Größe 152", "Neuer Blouson Größe 158 ausgegeben"),
                        exchangeLink));

        notificationRepository.create(
                memberId,
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        new NotificationParams.ProcurementRequested("Handschuhe Größe 6"), procurementLink));

        notificationRepository.create(
                memberId,
                NotificationType.PROCUREMENT_FULFILLED,
                NotificationData.of(
                        new NotificationParams.ProcurementFulfilled("Handschuhe Größe 6"), procurementLink));
    }

    private void seedSocialCategory(int memberId, int otherMemberId, ShowcaseContext ctx) {
        notificationRepository.create(
                memberId,
                NotificationType.MEMBER_ADDED_TO_GROUP,
                NotificationData.of(
                        new NotificationParams.MemberAddedToGroup("Wettkampfteam", "Alice Müller"),
                        new NotificationData.NotificationLink("dashboard-overview")));

        notificationRepository.create(
                memberId,
                NotificationType.PROFILE_FIELD_CHANGED,
                NotificationData.of(
                        new NotificationParams.ProfileFieldChanged("Lukas Frank", "Allergien"),
                        new NotificationData.NotificationLink("members-detail", Map.of("id", otherMemberId))));

        var formLink = ctx.formId() != null
                ? new NotificationData.NotificationLink("form-detail", Map.of("id", ctx.formId()))
                : new NotificationData.NotificationLink("forms");
        notificationRepository.create(
                memberId,
                NotificationType.NEW_FORM,
                NotificationData.of(
                        new NotificationParams.NewForm("Fahrt nach Berlin — Teilnahme bestätigen"), formLink));

        Integer lostId = ctx.lostAndFoundItemId();
        var lostLink = lostId != null
                ? new NotificationData.NotificationLink("lost-and-found", Map.of("id", lostId))
                : new NotificationData.NotificationLink("lost-and-found");
        notificationRepository.create(
                memberId,
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(
                        new NotificationParams.LostAndFoundNew("Blaue Jacke Größe M, im Geräteraum gefunden"),
                        lostLink));
        notificationRepository.create(
                memberId,
                NotificationType.LOST_AND_FOUND_CLAIMED,
                NotificationData.of(
                        new NotificationParams.LostAndFoundClaimed("Frieda Vogel", "Blaue Jacke Größe M"), lostLink));

        notificationRepository.create(
                memberId,
                NotificationType.WAITLIST_NEW_ENTRY,
                NotificationData.of(
                        new NotificationParams.WaitlistNewEntry("Lena Schmidt", "Anfänger-Gruppe"),
                        new NotificationData.NotificationLink("dashboard-overview")));
        notificationRepository.create(
                memberId,
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                NotificationData.of(
                        new NotificationParams.WaitlistPublicRegistration("Max Müller", "Anfänger-Gruppe"),
                        new NotificationData.NotificationLink("dashboard-overview")));
    }

    private void seedLendingCategory(int memberId, ShowcaseContext ctx) {
        var lendingLink = ctx.lendingRequestId() != null
                ? new NotificationData.NotificationLink("lending-request", Map.of("id", ctx.lendingRequestId()))
                : new NotificationData.NotificationLink("dashboard-overview");

        notificationRepository.create(
                memberId,
                NotificationType.LENDING_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.LendingNewRequest("FF Musterstadt-Süd", "2 Handfunkgeräte"),
                        lendingLink));
        notificationRepository.create(
                memberId,
                NotificationType.LENDING_STATUS_CHANGE,
                NotificationData.of(
                        new NotificationParams.LendingStatusChange("FF Musterstadt-Süd", LendingStatus.APPROVED),
                        lendingLink));
        notificationRepository.create(
                memberId,
                NotificationType.LENDING_NEW_MESSAGE,
                NotificationData.of(
                        new NotificationParams.LendingNewMessage("FF Musterstadt-Süd", "Bob Schmidt"), lendingLink));
    }

    private void seedBoardAndProcedureCategory(int memberId, ShowcaseContext ctx) {
        Integer boardTicketId = ctx.boardTicketId();
        var ticketLink = boardTicketId != null && ctx.boardKey() != null && ctx.boardTicketNumber() != null
                ? new NotificationData.NotificationLink(
                        "ticket-detail",
                        Map.of(
                                "boardKey", ctx.boardKey(),
                                "ticketNumber", ctx.boardTicketNumber(),
                                "ticketId", boardTicketId))
                : new NotificationData.NotificationLink("dashboard-overview");
        notificationRepository.create(
                memberId,
                NotificationType.BOARD_TICKET_UPDATE,
                NotificationData.of(
                        new NotificationParams.BoardTicketUpdate(
                                "Vorstand",
                                ctx.boardKey() != null ? ctx.boardKey() + "-12" : "VOR-12",
                                "Status nach Erledigt geändert"),
                        ticketLink));

        Integer procedureId = ctx.procedureId();
        var procedureLink = procedureId != null
                ? new NotificationData.NotificationLink("procedures", Map.of("id", procedureId))
                : new NotificationData.NotificationLink("procedures");
        notificationRepository.create(
                memberId,
                NotificationType.PROCEDURE_ASSIGNED,
                NotificationData.of(
                        new NotificationParams.ProcedureAssigned("Quartals-Fahrzeugcheck", "Alice Müller"),
                        procedureLink));
        notificationRepository.create(
                memberId,
                NotificationType.PROCEDURE_RESOLVED,
                NotificationData.of(
                        new NotificationParams.ProcedureResolvedParams("Quartals-Fahrzeugcheck"), procedureLink));
        notificationRepository.create(
                memberId,
                NotificationType.PROCEDURE_REOPENED,
                NotificationData.of(
                        new NotificationParams.ProcedureReopenedParams("Quartals-Fahrzeugcheck"), procedureLink));
        notificationRepository.create(
                memberId,
                NotificationType.PROCEDURE_ITEM_CHECKED,
                NotificationData.of(
                        new NotificationParams.ProcedureItemCheckedParams(
                                "Quartals-Fahrzeugcheck", "Wasserschlauch tauschen", "Bob Schmidt"),
                        procedureLink));
    }

    private void seedMiscCategory(int memberId, int otherMemberId, ShowcaseContext ctx) {
        Integer storageStationId = ctx.stationIdForStorage();
        var storageLink = storageStationId != null
                ? new NotificationData.NotificationLink("station-settings", Map.of("stationId", storageStationId))
                : new NotificationData.NotificationLink("station-settings");
        notificationRepository.create(
                memberId,
                NotificationType.STORAGE_WARNING,
                NotificationData.of(new NotificationParams.StorageWarning(91, "9.1 GiB", "10 GiB"), storageLink));
    }
}

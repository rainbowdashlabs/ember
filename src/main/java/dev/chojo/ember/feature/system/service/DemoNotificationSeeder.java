/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Seeds one notification of every {@link NotificationType} for the demo admin so all 29
 * categories can be reviewed in the dashboard and atom feed at a glance. The rest of the
 * demo's notifications are produced organically by the other seeders' service calls
 * (newsService, eventService, itemMovementService, …) - this seeder doesn't compete with them
 * because admin already gets the organic notifications too.
 *
 * <p>Each entry uses representative link metadata so the renderer's per-type enrichment
 * fires (event/lost-and-found/lending/inventory/procedure/board ticket lookups). The entity
 * ids are taken from real seeded records so deep links resolve.
 */
@Singleton
public class DemoNotificationSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoNotificationSeeder.class);

    private final Notifier notifier;
    private final InventoryRepository inventoryRepository;
    private final BoardService boardService;
    private final BoardTicketService boardTicketService;
    private final ProcedureService procedureService;
    private final LendingService lendingService;
    private final DemoClock clock;

    @Inject
    public DemoNotificationSeeder(
            Notifier notifier,
            InventoryRepository inventoryRepository,
            BoardService boardService,
            BoardTicketService boardTicketService,
            ProcedureService procedureService,
            LendingService lendingService,
            DemoClock clock) {
        this.clock = clock;
        this.notifier = notifier;
        this.inventoryRepository = inventoryRepository;
        this.boardService = boardService;
        this.boardTicketService = boardTicketService;
        this.procedureService = procedureService;
        this.lendingService = lendingService;
    }

    /**
     * Runs last so every entity the showcase links to already exists. The lookups go through the
     * domain services, matching the demo's convention of using services so domain events fire,
     * even though these particular reads are side-effect free.
     */
    @Override
    public int order() {
        return SHOWCASE;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        int stationId = station.stationId();
        LocalDate today = clock.of(station.station()).today();
        var nextMonday =
                today.with(DayOfWeek.MONDAY).plusWeeks(today.getDayOfWeek().getValue() > 1 ? 1 : 0);

        Integer inventoryId = inventoryRepository.findByStation(stationId).stream()
                .filter(inv -> "Blouson".equals(inv.name()))
                .map(Inventory::id)
                .findFirst()
                .orElseGet(() -> inventoryRepository.findByStation(stationId).stream()
                        .map(Inventory::id)
                        .findFirst()
                        .orElse(null));

        Integer lendingRequestId = lendingService.findRequestsByStation(stationId).stream()
                .map(LendingRequest::id)
                .findFirst()
                .orElse(null);

        var board = boardService.findByStation(stationId).stream().findFirst().orElse(null);
        Integer boardId = board == null ? null : board.id();
        String boardKey = board == null ? null : board.shortKey();
        Integer boardTicketId = null;
        Integer boardTicketNumber = null;
        if (board != null) {
            var ticket = boardTicketService.findByBoard(board.id()).stream()
                    .findFirst()
                    .orElse(null);
            if (ticket != null) {
                boardTicketId = ticket.id();
                boardTicketNumber = ticket.ticketNumber();
            }
        }

        Integer procedureId = procedureService.findProceduresByStation(stationId, ProcedureStatus.OPEN).stream()
                .map(Procedure::id)
                .findFirst()
                .orElseGet(() -> procedureService.findProceduresByStation(stationId, ProcedureStatus.RESOLVED).stream()
                        .map(Procedure::id)
                        .findFirst()
                        .orElse(null));

        var lostAndFoundItem = station.lostAndFoundItem();
        var showcase = new ShowcaseContext(
                station.news().firstNewsId(),
                station.events().stadtfestId(),
                station.events().evUebung().id(),
                nextMonday.toString(),
                null,
                lostAndFoundItem == null ? null : lostAndFoundItem.id(),
                lendingRequestId,
                boardId,
                boardKey,
                boardTicketId,
                boardTicketNumber,
                procedureId,
                inventoryId,
                null,
                today);
        seedShowcase(station.adminMember(), station.members().anfaenger(), showcase);
        log.info("Demo: Created showcase notification for every NotificationType");
    }

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

    /** Writes one showcase notification to one member, the way every sender writes one. */
    private void tell(int memberId, NotificationType type, NotificationData data) {
        notifier.notify(StationAudience.member(memberId), type, data, Delivery.EVERY_TIME);
    }

    private void seedNewsCategory(int memberId, ShowcaseContext ctx) {
        Integer newsId = ctx.newsId();
        var newsLink = newsId != null
                ? new NotificationData.NotificationLink("news-detail", Map.of("id", newsId))
                : new NotificationData.NotificationLink("news-list");

        tell(
                memberId,
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        new NotificationParams.NewNews(
                                "Übungsplan Q3 veröffentlicht",
                                "Alice Müller",
                                "Ab nächster Woche rotieren wir Dienstag und Donnerstag - der vollständige Plan steht im Beitrag."),
                        newsLink));

        tell(
                memberId,
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        new NotificationParams.NewsComment(
                                "Übungsplan Q3 veröffentlicht",
                                "Bob Schmidt",
                                "Ich bin am Dienstag etwas später dran, geht das in Ordnung?"),
                        newsLink));

        tell(
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

        tell(
                memberId,
                NotificationType.NEW_EVENT,
                NotificationData.of(
                        new NotificationParams.NewEvent(
                                "Offenes Training",
                                "Übung für alle Altersgruppen - Treffpunkt am Marktplatz, bitte rechtzeitig erscheinen."),
                        oneTimeLink));

        tell(
                memberId,
                NotificationType.NEW_EVENTS_BATCH,
                NotificationData.of(
                        new NotificationParams.NewEventsBatch(
                                3,
                                "Offenes Training, Übungstag, Sommerfest",
                                ctx.today().plusDays(7)),
                        new NotificationData.NotificationLink("events-upcoming")));

        tell(
                memberId,
                NotificationType.EVENT_REGISTRATION_STATUS,
                NotificationData.of(
                        new NotificationParams.EventRegistrationStatus(
                                "Tim Berger",
                                "Offenes Training",
                                RegistrationStatus.ACCEPTED,
                                "Übung für alle Altersgruppen"),
                        oneTimeLink));

        tell(
                memberId,
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled(
                                "Offenes Training",
                                "Schneesturm angekündigt",
                                ctx.today().plusDays(3),
                                CancellationCause.MANUAL),
                        oneTimeLink));

        tell(
                memberId,
                NotificationType.EVENT_DATE_RESTORED,
                NotificationData.of(
                        new NotificationParams.EventDateRestored(
                                "Offenes Training", ctx.today().plusDays(1)),
                        recurringLink));

        tell(
                memberId,
                NotificationType.EVENT_REMINDER,
                NotificationData.of(
                        new NotificationParams.EventReminder(
                                "Offenes Training", 1, ctx.today().plusDays(1)),
                        recurringLink));

        tell(
                memberId,
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                NotificationData.of(
                        new NotificationParams.RegistrationDeadlineExpired("Offenes Training", 4), oneTimeLink));
    }

    private void seedInventoryCategory(int memberId, ShowcaseContext ctx) {
        Integer inventoryId = ctx.inventoryId();
        var exchangeLink = inventoryId != null
                ? new NotificationData.NotificationLink("inventory-movements", Map.of("id", inventoryId))
                : new NotificationData.NotificationLink("inventory-movements");
        var procurementLink = inventoryId != null
                ? new NotificationData.NotificationLink("inventory-procurement", Map.of("id", inventoryId))
                : new NotificationData.NotificationLink("inventory-procurement");

        tell(
                memberId,
                NotificationType.MOVEMENT_RAISED,
                NotificationData.of(
                        new NotificationParams.MovementRaised("Tim Berger", "Blouson Größe 152", "Zu klein geworden"),
                        exchangeLink));

        tell(
                memberId,
                NotificationType.MOVEMENT_ADVANCED,
                NotificationData.of(
                        new NotificationParams.MovementMoved("Ersatz ausgegeben", "Blouson Größe 152", null),
                        exchangeLink));

        tell(
                memberId,
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        new NotificationParams.ProcurementRequested("Handschuhe Größe 6"), procurementLink));

        tell(
                memberId,
                NotificationType.PROCUREMENT_FULFILLED,
                NotificationData.of(
                        new NotificationParams.ProcurementFulfilled("Handschuhe Größe 6"), procurementLink));
    }

    private void seedSocialCategory(int memberId, int otherMemberId, ShowcaseContext ctx) {
        tell(
                memberId,
                NotificationType.MEMBER_ADDED_TO_GROUP,
                NotificationData.of(
                        new NotificationParams.MemberAddedToGroup("Wettkampfteam", "Alice Müller"),
                        new NotificationData.NotificationLink("dashboard-overview")));

        tell(
                memberId,
                NotificationType.PROFILE_FIELD_CHANGED,
                NotificationData.of(
                        new NotificationParams.ProfileFieldChanged("Lukas Frank", "Allergien"),
                        new NotificationData.NotificationLink("members-detail", Map.of("id", otherMemberId))));

        tell(
                memberId,
                NotificationType.EXPIRY_REMINDER,
                NotificationData.of(
                        new NotificationParams.ExpiryReminder(
                                ExpiryReminderKind.EXPIRES_IN,
                                "JuLeiCa Ablaufdatum",
                                "Max Mustermann",
                                ctx.today().plusDays(30),
                                30,
                                null,
                                null),
                        NotificationLinks.ownProfile()));

        Integer formId = ctx.formId();
        var formLink =
                formId != null ? NotificationLinks.form(formId) : new NotificationData.NotificationLink("forms-list");
        tell(
                memberId,
                NotificationType.NEW_FORM,
                NotificationData.of(
                        new NotificationParams.NewForm("Fahrt nach Berlin - Teilnahme bestätigen"), formLink));

        Integer lostId = ctx.lostAndFoundItemId();
        var lostLink = lostId != null
                ? new NotificationData.NotificationLink("lost-and-found", Map.of("id", lostId))
                : new NotificationData.NotificationLink("lost-and-found");
        tell(
                memberId,
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(
                        new NotificationParams.LostAndFoundNew("Blaue Jacke Größe M, im Geräteraum gefunden"),
                        lostLink));
        tell(
                memberId,
                NotificationType.LOST_AND_FOUND_CLAIMED,
                NotificationData.of(
                        new NotificationParams.LostAndFoundClaimed("Frieda Vogel", "Blaue Jacke Größe M"), lostLink));

        tell(
                memberId,
                NotificationType.WAITLIST_NEW_ENTRY,
                NotificationData.of(
                        new NotificationParams.WaitlistNewEntry("Lena Schmidt", "Anfänger-Gruppe"),
                        new NotificationData.NotificationLink("dashboard-overview")));
        tell(
                memberId,
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                NotificationData.of(
                        new NotificationParams.WaitlistPublicRegistration("Max Müller", "Anfänger-Gruppe"),
                        new NotificationData.NotificationLink("dashboard-overview")));
    }

    private void seedLendingCategory(int memberId, ShowcaseContext ctx) {
        Integer lendingRequestId = ctx.lendingRequestId();
        var lendingLink = lendingRequestId != null
                ? NotificationLinks.lendingRequest(lendingRequestId)
                : new NotificationData.NotificationLink("dashboard-overview");

        tell(
                memberId,
                NotificationType.LENDING_NEW_REQUEST,
                NotificationData.of(
                        new NotificationParams.LendingNewRequest("FF Musterstadt-Süd", "2 Handfunkgeräte"),
                        lendingLink));
        tell(
                memberId,
                NotificationType.LENDING_STATUS_CHANGE,
                NotificationData.of(
                        new NotificationParams.LendingStatusChange("FF Musterstadt-Süd", LendingStatus.APPROVED),
                        lendingLink));
        tell(
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
        tell(
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
                ? new NotificationData.NotificationLink("procedure-detail", Map.of("id", procedureId))
                : new NotificationData.NotificationLink("procedure-list");
        tell(
                memberId,
                NotificationType.PROCEDURE_ASSIGNED,
                NotificationData.of(
                        new NotificationParams.ProcedureAssigned("Quartals-Fahrzeugcheck", "Alice Müller"),
                        procedureLink));
        tell(
                memberId,
                NotificationType.PROCEDURE_RESOLVED,
                NotificationData.of(
                        new NotificationParams.ProcedureResolvedParams("Quartals-Fahrzeugcheck"), procedureLink));
        tell(
                memberId,
                NotificationType.PROCEDURE_REOPENED,
                NotificationData.of(
                        new NotificationParams.ProcedureReopenedParams("Quartals-Fahrzeugcheck"), procedureLink));
        tell(
                memberId,
                NotificationType.PROCEDURE_ITEM_CHECKED,
                NotificationData.of(
                        new NotificationParams.ProcedureItemCheckedParams(
                                "Quartals-Fahrzeugcheck", "Wasserschlauch tauschen", "Bob Schmidt"),
                        procedureLink));
    }

    private void seedMiscCategory(int memberId, int otherMemberId, ShowcaseContext ctx) {
        tell(
                memberId,
                NotificationType.STORAGE_WARNING,
                NotificationData.of(
                        new NotificationParams.StorageWarning(91, "9.1 GiB", "10 GiB"),
                        NotificationLinks.stationStorage()));
    }

    /**
     * The seeded records the showcase links to, and the station's today its dates are counted from.
     */
    public record ShowcaseContext(
            @Nullable Integer newsId,
            @Nullable Integer oneTimeEventId,
            @Nullable Integer recurringEventId,
            @Nullable String recurringEventDate,
            @Nullable Integer formId,
            @Nullable Integer lostAndFoundItemId,
            @Nullable Integer lendingRequestId,
            @Nullable Integer boardId,
            @Nullable String boardKey,
            @Nullable Integer boardTicketId,
            @Nullable Integer boardTicketNumber,
            @Nullable Integer procedureId,
            @Nullable Integer inventoryId,
            @Nullable Integer waitlistChildId,
            LocalDate today) {}
}

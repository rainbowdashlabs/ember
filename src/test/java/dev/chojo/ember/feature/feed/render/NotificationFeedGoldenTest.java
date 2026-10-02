/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.notifications.entity.ExpiryReminderKind;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The notification feeds, written out whole and compared byte for byte with what they were.
 *
 * <p>One notification of every kind, each pointing at something its enrichment can find, is rendered
 * into an Atom feed, an RSS feed and a compact Atom feed. Any change to what a reader receives shows
 * up here as a difference, which is the point: the renderer can be reorganised freely as long as
 * these files stay the same. A missing file is written and the test fails, so a new one is looked at
 * before it is committed.
 */
class NotificationFeedGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/feed");
    private static final Instant CREATED = Instant.parse("2026-09-01T08:15:00Z");
    private static final String BASE_URL = "https://ember.example.com";
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-00000000cafe");

    /** Labels the mocked bundle knows; every other key comes back as itself, as a missing one would. */
    private static final Map<String, String> KNOWN_LABELS = Map.of(
            "by", "Von",
            "preview", "Vorschau",
            "status", "Status",
            "event", "Termin",
            "when", "Wann",
            "reason", "Grund",
            "description", "Beschreibung",
            "label.link", "Öffnen",
            "eventType.RECURRING", "Wöchentlich",
            "progressFormat", "{checked} von {total}");

    private NotificationText notificationService;
    private EventCrudService crudService;
    private EventFieldService eventFieldService;
    private LostAndFoundService lostAndFoundService;
    private LendingService lendingService;
    private StorageQuotaService storageQuotaService;
    private InventoryService inventoryService;
    private BoardTicketService boardTicketService;
    private ProcedureService procedureService;
    private StationRepository stationRepository;
    private NotificationFeedRenderer renderer;

    @BeforeEach
    void setup() {
        notificationService = mock(NotificationText.class);
        crudService = mock(EventCrudService.class);
        eventFieldService = mock(EventFieldService.class);
        lostAndFoundService = mock(LostAndFoundService.class);
        lendingService = mock(LendingService.class);
        storageQuotaService = mock(StorageQuotaService.class);
        inventoryService = mock(InventoryService.class);
        boardTicketService = mock(BoardTicketService.class);
        procedureService = mock(ProcedureService.class);
        stationRepository = mock(StationRepository.class);

        when(notificationService.resolveCategory(any(), any()))
                .thenAnswer(inv -> "Kategorie " + ((NotificationType) inv.getArgument(1)).name());
        when(notificationService.resolveMessage(any(), any()))
                .thenAnswer(inv -> "Nachricht <" + ((Notification) inv.getArgument(1)).type() + "> & mehr");
        when(notificationService.resolveFeedTitle(any(), any()))
                .thenAnswer(inv -> "Titel " + ((Notification) inv.getArgument(1)).type());
        when(notificationService.resolveStatusWithSymbol(any(), any())).thenAnswer(inv -> "✓ " + inv.getArgument(1));
        when(notificationService.resolveNotificationUrl(any(), any(), any())).thenAnswer(inv -> {
            NotificationData data = inv.getArgument(2);
            return data.link() == null
                    ? null
                    : BASE_URL + "/deep/" + data.link().route();
        });
        when(notificationService.resolveLocalized(any(), any(), any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(2);
            String value = KNOWN_LABELS.getOrDefault(key, key);
            Map<String, String> params = inv.getArgument(3);
            if (params == null) return value;
            for (var entry : params.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return value;
        });

        when(stationRepository.findById(1)).thenReturn(Optional.of(berlin()));
        stubEnrichments();
        renderer = new NotificationFeedRenderer(
                notificationService,
                stationRepository,
                FeedContributors.all(
                        crudService,
                        eventFieldService,
                        mock(OccurrenceCalendar.class),
                        lostAndFoundService,
                        lendingService,
                        storageQuotaService,
                        inventoryService,
                        boardTicketService,
                        procedureService));
    }

    private void stubEnrichments() {
        when(crudService.findById(anyInt())).thenReturn(Optional.empty());
        when(crudService.findById(42))
                .thenReturn(Optional.of(event(
                        42,
                        StationEvent.EventType.ONE_TIME,
                        Instant.parse("2026-09-19T07:00:00Z"),
                        Instant.parse("2026-09-19T12:00:00Z"),
                        null,
                        null)));
        when(crudService.findById(43))
                .thenReturn(Optional.of(event(
                        43,
                        StationEvent.EventType.RECURRING,
                        Instant.parse("2026-09-19T16:00:00Z"),
                        Instant.parse("2026-09-21T10:00:00Z"),
                        Instant.parse("2026-09-15T20:00:00Z"),
                        12)));
        when(eventFieldService.findByEvent(anyInt(), any())).thenReturn(List.of());
        when(eventFieldService.findByEvent(eq(42), any()))
                .thenReturn(List.of(
                        new AppointmentField(
                                1,
                                42,
                                "Treffpunkt",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "Marktplatz",
                                0,
                                true,
                                null,
                                true),
                        new AppointmentField(
                                2,
                                42,
                                "Leer",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                " ",
                                1,
                                false,
                                null,
                                false)));
        when(eventFieldService.displayValue(any())).thenAnswer(inv -> {
            AppointmentField field = inv.getArgument(0);
            return field.value().trim();
        });
        when(lostAndFoundService.findById(anyInt())).thenReturn(Optional.empty());
        when(lostAndFoundService.findById(17))
                .thenReturn(Optional.of(new LostAndFoundItem(
                        17,
                        1,
                        "Blaue Jacke",
                        LocalDate.of(2026, 6, 12),
                        2,
                        Instant.parse("2026-06-14T10:30:00Z"),
                        2,
                        CREATED)));
        when(lendingService.findRequest(anyInt())).thenReturn(Optional.empty());
        when(lendingService.findRequest(55))
                .thenReturn(Optional.of(new LendingRequest(
                        55,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        LendingStatus.REQUESTED,
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 10, 7),
                        99,
                        CREATED,
                        CREATED,
                        null,
                        null,
                        "")));
        when(storageQuotaService.getUsage(anyInt())).thenReturn(List.of());
        when(storageQuotaService.getUsage(7))
                .thenReturn(List.of(
                        new StorageUsage(7, StorageCategory.IMAGE_AVATAR, 480L * 1024 * 1024, 10, CREATED),
                        new StorageUsage(7, StorageCategory.KB_FILES, 5L * 1024 * 1024 * 1024, 100, CREATED),
                        new StorageUsage(7, StorageCategory.BOARD_ATTACHMENTS, 0, 0, CREATED)));
        when(inventoryService.findById(anyInt())).thenReturn(Optional.empty());
        when(inventoryService.findById(99))
                .thenReturn(Optional.of(
                        new Inventory(99, 1, "Schlauch 25m", InventoryType.INTERNAL, false, true, false, null, null)));
        when(boardTicketService.findById(anyInt())).thenReturn(Optional.empty());
        when(boardTicketService.findById(123))
                .thenReturn(Optional.of(new BoardTicket(
                        123,
                        7,
                        1,
                        42,
                        "Neue Helme bestellen",
                        "desc",
                        new MemberIdentity(
                                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                                "Alice Müller",
                                null,
                                null,
                                null),
                        TicketPriority.HIGH,
                        null,
                        0,
                        null,
                        CREATED,
                        CREATED,
                        CREATED,
                        0,
                        0,
                        0)));
        when(procedureService.findItems(anyInt())).thenReturn(List.of());
        when(procedureService.findItems(80))
                .thenReturn(List.of(
                        new ProcedureItem(1, 80, "A", "", "", true, false, 0, true, CREATED, null),
                        new ProcedureItem(2, 80, "B", "", "", true, false, 0, false, null, null)));
    }

    /** One notification of every kind, each with what its details and enrichment read. */
    private static List<Notification> everyKind() {
        var list = new ArrayList<Notification>();
        var event42 = Map.<String, Object>of("id", 42);
        var event43 = Map.<String, Object>of("id", 43);
        add(
                list,
                NotificationType.NEW_NEWS,
                new NotificationParams.NewNews("Dienstplan", "Anna", "Erste\nZweite <b>"),
                "news-detail",
                Map.of("id", 5));
        add(
                list,
                NotificationType.NEWS_COMMENT,
                new NotificationParams.NewsComment("Dienstplan", "Ben", "Gute Idee"),
                "news-detail",
                Map.of("id", 5));
        add(
                list,
                NotificationType.COMMENT_MENTION,
                new NotificationParams.CommentMention("Protokoll", "Cleo", "@du schau mal"),
                "kb-file",
                Map.of("id", 6));
        add(
                list,
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Konzertprobe"),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.NEW_EVENTS_BATCH,
                new NotificationParams.NewEventsBatch(3, "A, B, C", LocalDate.of(2026, 9, 20)),
                "events",
                null);
        add(
                list,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus(
                        "Tim", "Probe", RegistrationStatus.ACCEPTED, "Mit Noten"),
                "event-detail",
                event43);
        add(
                list,
                NotificationType.EVENT_CANCELLED,
                new NotificationParams.EventCancelled("Probe", "Wetter", null, CancellationCause.MANUAL),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.EVENT_REMINDER,
                new NotificationParams.EventReminder("Probe", 3, LocalDate.of(2026, 9, 19)),
                "event-detail",
                event43);
        add(
                list,
                NotificationType.REGISTRATION_CLOSING,
                new NotificationParams.RegistrationClosing("Probe", 2, "Tim"),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.REGISTRATION_ANSWER_MISSING,
                new NotificationParams.RegistrationAnswerMissing("Probe", LocalDate.of(2026, 9, 19), "Tim"),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.EVENT_DATE_DROPPED,
                new NotificationParams.EventDateDropped(
                        "Probe", LocalDate.of(2026, 9, 19), "Tim", LocalDate.of(2026, 9, 26)),
                "event-detail",
                event43);
        add(
                list,
                NotificationType.EVENT_MOVED,
                new NotificationParams.EventMoved("Probe", LocalDate.of(2026, 9, 19), "18:00", "Tim"),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.EXPIRY_REMINDER,
                new NotificationParams.ExpiryReminder(
                        ExpiryReminderKind.values()[0],
                        "Führerschein",
                        "Tim",
                        LocalDate.of(2026, 12, 31),
                        30,
                        "Tim, Ben",
                        2),
                "member-detail",
                Map.of("id", 3));
        add(
                list,
                NotificationType.MOVEMENT_RAISED,
                new NotificationParams.MovementRaised("Tim", "Schlauch 25m", "Kaputt"),
                "inventory",
                Map.of("id", 99));
        add(
                list,
                NotificationType.MOVEMENT_ADVANCED,
                new NotificationParams.MovementMoved("Ausgegeben", "Schlauch 25m", StepActor.values()[0]),
                "inventory",
                Map.of("id", 99));
        add(
                list,
                NotificationType.MOVEMENT_DECLINED,
                new NotificationParams.MovementDeclined("Schlauch 25m", "Nein"),
                "inventory",
                Map.of("id", 99));
        add(
                list,
                NotificationType.MOVEMENT_CANCELLED,
                new NotificationParams.MovementCancelled("Schlauch 25m", "Helm", "Doch nicht", false),
                "inventory",
                Map.of("id", 99));
        add(
                list,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke \"groß\""),
                "lost-and-found",
                Map.of("id", 17));
        add(
                list,
                NotificationType.LOST_AND_FOUND_CLAIMED,
                new NotificationParams.LostAndFoundClaimed("Frieda", "Blaue Jacke"),
                "lost-and-found",
                Map.of("id", 17));
        add(
                list,
                NotificationType.LENDING_NEW_REQUEST,
                new NotificationParams.LendingNewRequest("FF Süd", "2 Funkgeräte"),
                "lending-request",
                Map.of("id", 55));
        add(
                list,
                NotificationType.LENDING_STATUS_CHANGE,
                new NotificationParams.LendingStatusChange("FF Süd", LendingStatus.APPROVED),
                "lending-request",
                Map.of("id", 55));
        add(
                list,
                NotificationType.LENDING_NEW_MESSAGE,
                new NotificationParams.LendingNewMessage("FF Süd", "Dora"),
                "lending-request",
                Map.of("id", 56));
        add(
                list,
                NotificationType.BOARD_TICKET_UPDATE,
                new NotificationParams.BoardTicketUpdate("Vorstand", "VOR-42", "nach Erledigt"),
                "ticket-detail",
                Map.of("boardKey", "VOR", "ticketNumber", 42, "ticketId", 123));
        add(
                list,
                NotificationType.STORAGE_WARNING,
                new NotificationParams.StorageWarning(91, "9.1 GiB", "10 GiB"),
                "station-settings",
                Map.of("stationId", 7));
        add(
                list,
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                new NotificationParams.RegistrationDeadlineExpired("Probe", 4),
                "event-detail",
                event42);
        add(
                list,
                NotificationType.WAITLIST_NEW_ENTRY,
                new NotificationParams.WaitlistNewEntry("Emil", "Jugend"),
                "waitlist",
                null);
        add(
                list,
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                new NotificationParams.WaitlistPublicRegistration("Emil", "Jugend"),
                "waitlist",
                null);
        add(
                list,
                NotificationType.WAITLIST_INVITATION_ANSWERED,
                new NotificationParams.WaitlistInvitationAnswered("Emil", "Jugend", "YES"),
                "waitlist",
                null);
        add(
                list,
                NotificationType.MEMBER_ADDED_TO_GROUP,
                new NotificationParams.MemberAddedToGroup("Atemschutz", "Fritz"),
                "groups",
                null);
        add(
                list,
                NotificationType.PROFILE_FIELD_CHANGED,
                new NotificationParams.ProfileFieldChanged("Tim", "Telefon"),
                "member-detail",
                Map.of("id", 3));
        add(
                list,
                NotificationType.PROCUREMENT_REQUESTED,
                new NotificationParams.ProcurementRequested("Schlauch 25m"),
                "inventory-procurement",
                Map.of("id", 99));
        add(
                list,
                NotificationType.PROCUREMENT_FULFILLED,
                new NotificationParams.ProcurementFulfilled("Schlauch 25m"),
                "inventory-procurement",
                Map.of("id", 99));
        add(list, NotificationType.NEW_FORM, new NotificationParams.NewForm("Umfrage"), "form", Map.of("id", 8));
        add(
                list,
                NotificationType.PROCEDURE_ASSIGNED,
                new NotificationParams.ProcedureAssigned("Fahrzeugcheck", "Gerd"),
                "procedure-detail",
                Map.of("id", 80));
        add(
                list,
                NotificationType.PROCEDURE_RESOLVED,
                new NotificationParams.ProcedureResolvedParams("Fahrzeugcheck"),
                "procedure-detail",
                Map.of("id", 80));
        add(
                list,
                NotificationType.PROCEDURE_REOPENED,
                new NotificationParams.ProcedureReopenedParams("Fahrzeugcheck"),
                "procedure-detail",
                Map.of("id", 81));
        add(
                list,
                NotificationType.PROCEDURE_ITEM_CHECKED,
                new NotificationParams.ProcedureItemCheckedParams("Fahrzeugcheck", "Reifen", "Hanna"),
                "procedure-detail",
                Map.of("id", 80));
        add(
                list,
                NotificationType.SELF_CHECK_ASSIGNED,
                new NotificationParams.SelfCheckAssigned("Tim", "Gerd", "2026-10-01"),
                "self-check",
                null);
        add(
                list,
                NotificationType.SELF_CHECK_SUBMITTED,
                new NotificationParams.SelfCheckSubmitted("Tim", "Tim"),
                "self-check",
                null);
        add(
                list,
                NotificationType.SELF_CHECK_ROW_REFUSED,
                new NotificationParams.SelfCheckRowRefused("Tim", "Helm", "Fehlt"),
                "self-check",
                null);
        add(
                list,
                NotificationType.MAILBOX_SUSPENDED,
                new NotificationParams.MailboxSuspended("info@", "Passwort"),
                "mail",
                null);
        add(list, NotificationType.MAIL_IMPORT_UNBOUND, new NotificationParams.MailImportUnbound(4), "mail", null);
        add(
                list,
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                new NotificationParams.ClusterApplicationSubmitted("FF Nord"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_APPLICATION_APPROVED,
                new NotificationParams.ClusterApplicationApproved("Kreis"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_APPLICATION_DENIED,
                new NotificationParams.ClusterApplicationDenied("Kreis", "Nein"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_APPLICATION_WITHDRAWN,
                new NotificationParams.ClusterApplicationWithdrawn("FF Nord"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_STATION_RELEASED,
                new NotificationParams.ClusterStationReleased("Kreis"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_MODULE_DENIED,
                new NotificationParams.ClusterModuleDenied("Kreis", "BOARDS"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_ITEM_ISSUED,
                new NotificationParams.ClusterItemIssued("Kreis", "Helm"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_ITEM_LOST,
                new NotificationParams.ClusterItemLost("Helm", "FF Nord"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_QUOTA_CHANGED,
                new NotificationParams.ClusterQuotaChanged("Kreis", "10 GiB"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_MEMBER_ROLE_CHANGED,
                new NotificationParams.ClusterMemberRoleChanged("Kreis"),
                "cluster",
                null);
        add(
                list,
                NotificationType.CLUSTER_FIELD_VALUE_CHANGED,
                new NotificationParams.ClusterFieldValueChanged("Kreis", "Telefon"),
                "cluster",
                null);
        add(list, NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Ohne Termin", null), null, null);
        add(
                list,
                NotificationType.EVENT_CANCELLED,
                new NotificationParams.EventCancelled(
                        "Probe", null, LocalDate.of(2026, 9, 19), CancellationCause.THRESHOLD),
                "event-detail",
                event43);
        add(
                list,
                NotificationType.EVENT_DATE_RESTORED,
                new NotificationParams.EventDateRestored("Probe", LocalDate.of(2026, 9, 26)),
                "event-detail",
                event43);
        return list;
    }

    private static void add(
            List<Notification> list,
            NotificationType type,
            NotificationParams params,
            String route,
            Map<String, Object> routeParams) {
        NotificationData data;
        if (route == null) {
            data = NotificationData.of(params);
        } else if (routeParams == null) {
            data = NotificationData.of(params, new NotificationData.NotificationLink(route));
        } else {
            data = NotificationData.of(params, new NotificationData.NotificationLink(route, routeParams));
        }
        int id = list.size() + 1;
        list.add(new Notification(id, 1, null, type, data, CREATED.plusSeconds(id), null));
    }

    private String feed(String feedType, boolean verbose, boolean images) throws Exception {
        var ctx = new NotificationFeedRenderer.RenderContext("de", BASE_URL, "TOKEN", verbose, images, STATION_UID);
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(feedType);
        feed.setTitle("Benachrichtigungen");
        feed.setDescription("Alles, was passiert ist");
        feed.setLanguage("de");
        feed.setLink(BASE_URL + "/station/dashboard/overview?station=" + STATION_UID);
        if ("atom_1.0".equals(feedType)) feed.setUri("urn:ember:notifications:1");
        feed.setEntries(everyKind().stream().map(n -> renderer.render(n, ctx)).toList());
        return new SyndFeedOutput().outputString(feed);
    }

    /**
     * Compares the feed with its file. The feed library ends lines with CRLF and the repository keeps
     * text files with LF, so both sides are read with LF; every other byte has to match.
     */
    private static void assertMatchesGolden(String name, String actual) throws IOException {
        Path file = GOLDEN.resolve(name);
        String written = actual.replace("\r\n", "\n");
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, written, StandardCharsets.UTF_8);
            fail("Wrote " + file + ", which did not exist yet. Read it, then commit it.");
        }
        assertEquals(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), written, name);
    }

    @Test
    void theAtomFeedIsWhatItWas() throws Exception {
        assertMatchesGolden("notification-feed.atom.xml", feed("atom_1.0", true, true));
    }

    @Test
    void theRssFeedIsWhatItWas() throws Exception {
        assertMatchesGolden("notification-feed.rss.xml", feed("rss_2.0", true, true));
    }

    @Test
    void theCompactFeedIsWhatItWas() throws Exception {
        assertMatchesGolden("notification-feed-compact.atom.xml", feed("atom_1.0", false, false));
    }

    private static StationEvent event(
            int id,
            StationEvent.EventType type,
            Instant start,
            Instant end,
            Instant registrationDeadline,
            Integer registrationLimit) {
        return new StationEvent(
                id,
                1,
                "Probe",
                "Konzert",
                type,
                null,
                start,
                end,
                null,
                registrationDeadline != null,
                registrationDeadline,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                registrationLimit,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static Station berlin() {
        return new Station(
                1,
                null,
                "Test",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }
}

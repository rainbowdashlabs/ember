/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.rome.feed.synd.SyndCategory;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.entity.AppointmentField;
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
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
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
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationFeedRendererTest {

    private NotificationText notificationService;
    private NotificationFeedRenderer renderer;

    private EventCrudService crudService;
    private EventFieldService eventFieldService;
    private LostAndFoundService lostAndFoundService;
    private LendingService lendingService;
    private StorageQuotaService storageQuotaService;
    private InventoryService inventoryService;
    private BoardTicketService boardTicketService;
    private ProcedureService procedureService;
    private StationRepository stationRepository;

    /**
     * Every lookup finds nothing until a test stubs it, and every text comes back distinguishable:
     * categories as {@code loc:} plus the type, titles as {@code title:} plus the type, statuses with
     * a check mark, and translations as their key with the placeholders filled. A label whose key
     * comes back unchanged falls back to the renderer's capitalised display label.
     */
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
        when(crudService.findById(ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(eventFieldService.findByEvent(ArgumentMatchers.anyInt(), any())).thenReturn(List.of());
        when(eventFieldService.displayValue(ArgumentMatchers.any())).thenAnswer(inv -> {
            AppointmentField field = inv.getArgument(0);
            return field == null || field.value() == null ? "" : field.value().trim();
        });
        when(lostAndFoundService.findById(ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(lendingService.findRequest(ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(storageQuotaService.getUsage(ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(inventoryService.findById(ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(boardTicketService.findById(ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(procedureService.findItems(ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(notificationService.resolveCategory(any(), any()))
                .thenAnswer(inv -> "loc:" + ((NotificationType) inv.getArgument(1)).name());
        when(notificationService.resolveMessage(any(), any())).thenReturn("MESSAGE");
        when(notificationService.resolveFeedTitle(any(), any()))
                .thenAnswer(inv ->
                        "title:" + ((Notification) inv.getArgument(1)).type().name());
        when(notificationService.resolveStatusWithSymbol(any(), any())).thenAnswer(inv -> "✓ " + inv.getArgument(1));
        when(notificationService.resolveLocalized(any(), any(), any(), any())).thenAnswer(inv -> {
            String value = inv.getArgument(2);
            @SuppressWarnings("unchecked")
            var params = (Map<String, String>) inv.getArgument(3);
            if (params == null) return value;
            for (var entry : params.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return value;
        });
        when(notificationService.resolveNotificationUrl(any(), any(), any())).thenReturn(null);
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

    private NotificationFeedRenderer.RenderContext richCtx() {
        return new NotificationFeedRenderer.RenderContext("de", "https://ember.example.com", "TOKEN", true, true, null);
    }

    /** The bare category would make every News notification read as just "News" in the reader inbox. */
    @Test
    void entryTitleComesFromResolveFeedTitle() {
        var n = notification(100, NotificationType.NEW_NEWS, new NotificationParams.NewNews("T", "A", "P"));
        var entry = renderer.render(n, richCtx());
        assertEquals("title:NEW_NEWS", entry.getTitle());
    }

    /** The status row goes through the one helper that decides locale and symbol. */
    @Test
    void statusBodyUsesLocalisedSymbolFromService() {
        var n = notification(
                101,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus("Tim Berger", "Probe", RegistrationStatus.ACCEPTED, ""));
        var entry = renderer.render(n, richCtx());
        var html = entry.getContents().getFirst().getValue();
        assertTrue(html.contains("✓ ACCEPTED"), "Body should embed status from service helper");
    }

    @Test
    void everyEntryHasALinkEvenWithoutMetadata() {
        var n = notification(
                1, NotificationType.MEMBER_ADDED_TO_GROUP, new NotificationParams.MemberAddedToGroup("Alpha", null));
        var entry = renderer.render(n, richCtx());
        assertNotNull(entry.getLink());
        assertEquals("https://ember.example.com/station/dashboard/overview", entry.getLink());
    }

    @Test
    void resolvedDeepLinkOverridesDashboardFallback() {
        when(notificationService.resolveNotificationUrl(any(), any(), any()))
                .thenReturn("https://ember.example.com/station/events/42");
        var n = notification(2, NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"));
        var entry = renderer.render(n, richCtx());
        assertEquals("https://ember.example.com/station/events/42", entry.getLink());
    }

    @Test
    void carriesShortSummaryAndRichHtmlContent() {
        var n = notification(3, NotificationType.NEW_EVENT, new NotificationParams.NewEvent("Probe", "Konzert"));
        var entry = renderer.render(n, richCtx());
        assertEquals("text/plain", entry.getDescription().getType());
        assertEquals("MESSAGE", entry.getDescription().getValue(), "the summary keeps inbox rows short");
        var html = entry.getContents().stream()
                .filter(c -> "text/html".equals(c.getType()))
                .findFirst()
                .orElseThrow();
        assertTrue(html.getValue().contains("<dl"));
        assertTrue(html.getValue().contains("<dt"));
    }

    @Test
    void authorSetFromActorWhenAvailable() {
        var n = notification(4, NotificationType.NEW_NEWS, new NotificationParams.NewNews("Title", "Alice", "Preview"));
        var entry = renderer.render(n, richCtx());
        assertEquals("Alice", entry.getAuthor());
    }

    /** The localised label is for people, the enum name for filters that work across locales. */
    @Test
    void categoryCarriesLocalisedLabelAndStableEnumName() {
        var n = notification(
                5, NotificationType.STORAGE_WARNING, new NotificationParams.StorageWarning(91, "9.1 GB", "10 GB"));
        var entry = renderer.render(n, richCtx());
        var names = entry.getCategories().stream().map(SyndCategory::getName).toList();
        assertEquals(2, names.size());
        assertTrue(names.contains("loc:STORAGE_WARNING"), "Localised label missing: " + names);
        assertTrue(names.contains("STORAGE_WARNING"), "Stable enum-name fallback missing: " + names);
    }

    @Test
    void lostAndFoundEmbedsImageViaTokenRoute() {
        var n = notification(
                6,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var entry = renderer.render(n, richCtx());
        var html = entry.getContents().getFirst().getValue();
        assertTrue(html.contains("/api/v1/public/feed/TOKEN/lost-and-found/17/image"));
        assertTrue(html.contains("alt=\"Blaue Jacke\""));
        var media = entry.getModules().stream()
                .filter(MediaEntryModuleImpl.class::isInstance)
                .map(MediaEntryModuleImpl.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(1, media.getMediaContents().length, "readers like Thunderbird show the media thumbnail");
        assertEquals("image", media.getMediaContents()[0].getMedium());
    }

    @Test
    void imagesFlagOmitsImgTagAndMediaModule() {
        var n = notification(
                7,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var noImages = new NotificationFeedRenderer.RenderContext(
                "de", "https://ember.example.com", "TOKEN", true, false, null);
        var entry = renderer.render(n, noImages);
        assertFalse(entry.getContents().getFirst().getValue().contains("<img"));
        assertTrue(entry.getModules() == null
                || entry.getModules().stream().noneMatch(MediaEntryModuleImpl.class::isInstance));
    }

    @Test
    void compactModeHasHeadlineAndLinkButNoDetailBlock() {
        var n = notification(
                8,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus(
                        "Tim Berger", "Probe", RegistrationStatus.ACCEPTED, "Konzert"));
        var compact = new NotificationFeedRenderer.RenderContext(
                "de", "https://ember.example.com", "TOKEN", false, true, null);
        var entry = renderer.render(n, compact);
        var html = entry.getContents().getFirst().getValue();
        assertTrue(html.contains("MESSAGE"));
        assertFalse(html.contains("<dl"));
    }

    @Test
    void detailBlockRendersStatusWithUnicodeMarker() {
        var n = notification(
                9,
                NotificationType.EVENT_REGISTRATION_STATUS,
                new NotificationParams.EventRegistrationStatus(
                        "Tim Berger", "Probe", RegistrationStatus.ACCEPTED, "Konzert"));
        var entry = renderer.render(n, richCtx());
        var html = entry.getContents().getFirst().getValue();
        assertTrue(html.contains("✓ ACCEPTED"), "Description should contain check-mark + ACCEPTED");
        assertTrue(html.contains("Probe"));
    }

    @Test
    void newEventBodyIncludesEventTimesAndCustomFieldValues() {
        var start = Instant.parse("2027-09-15T17:00:00Z");
        var end = Instant.parse("2027-09-15T19:00:00Z");
        var event = stubEvent(42, start, end);
        when(crudService.findById(42)).thenReturn(Optional.of(event));
        when(eventFieldService.findByEvent(ArgumentMatchers.eq(42), any()))
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
                                "Empty",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "",
                                1,
                                false,
                                null,
                                false)));

        var n = notification(
                20,
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Konzertprobe"),
                new NotificationData.NotificationLink("event-detail", Map.of("id", 42)));
        var entry = renderer.render(n, richCtx());
        var html = entry.getContents().getFirst().getValue();
        assertTrue(html.contains("Treffpunkt"), "Field name should be rendered");
        assertTrue(html.contains("Marktplatz"), "Field value should be rendered");
        assertFalse(html.contains(">Empty<"), "Blank field should be skipped");
        assertTrue(html.contains("When"), "When (range-merged) label should be present");
        assertFalse(html.contains("Starts"), "Same-day events shouldn't render separate Starts row");
    }

    @Test
    void lostAndFoundEnrichmentAddsFindDate() {
        when(lostAndFoundService.findById(17))
                .thenReturn(Optional.of(new LostAndFoundItem(
                        17, 1, "Blaue Jacke", LocalDate.of(2026, 6, 12), null, null, 2, Instant.now())));
        var n = notification(
                50,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Found on"), "Find date label should be present: " + html);
        assertTrue(html.contains("2026"), "Find date year should land in body: " + html);
    }

    @Test
    void lostAndFoundClaimedEnrichmentAddsClaimDate() {
        var claimedAt = Instant.parse("2026-06-14T10:30:00Z");
        when(lostAndFoundService.findById(17))
                .thenReturn(Optional.of(new LostAndFoundItem(
                        17, 1, "Blaue Jacke", LocalDate.of(2026, 6, 12), 2, claimedAt, 2, Instant.now())));
        var n = notification(
                51,
                NotificationType.LOST_AND_FOUND_CLAIMED,
                new NotificationParams.LostAndFoundClaimed("Frieda", "Blaue Jacke"),
                new NotificationData.NotificationLink("lost-and-found", Map.of("id", 17)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Found on"), "Original find date should still be present");
        assertTrue(html.contains("Claimed on"), "Claim date label should be present");
    }

    @Test
    void lendingEnrichmentMergesRequestedDateRange() {
        when(lendingService.findRequest(42))
                .thenReturn(Optional.of(new LendingRequest(
                        42,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        LendingStatus.REQUESTED,
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 10, 7),
                        99,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        "")));
        var n = notification(
                52,
                NotificationType.LENDING_NEW_REQUEST,
                new NotificationParams.LendingNewRequest("FF Musterstadt-Süd", "2 Handfunkgeräte"),
                new NotificationData.NotificationLink("lending-request", Map.of("id", 42)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Needed"), "Needed label should be present");
        assertTrue(html.contains("–"), "Range should be merged with en-dash: " + html);
    }

    @Test
    void lendingEnrichmentSilentlyNoOpsOnMissingId() {
        var n = notification(
                53,
                NotificationType.LENDING_NEW_REQUEST,
                new NotificationParams.LendingNewRequest("Some Station", "Radios"),
                new NotificationData.NotificationLink("lending-request"));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertFalse(html.contains("Needed"));
        assertTrue(html.contains("Some Station"));
    }

    /** The categories are listed as bullets under their label, the largest first. */
    @Test
    void storageWarningEnrichmentAddsCategoryBreakdown() {
        var now = Instant.now();
        when(storageQuotaService.getUsage(7))
                .thenReturn(List.of(
                        new StorageUsage(7, StorageCategory.IMAGE_AVATAR, 480L * 1024 * 1024, 10, now),
                        new StorageUsage(7, StorageCategory.KB_FILES, 5L * 1024 * 1024 * 1024, 100, now),
                        new StorageUsage(7, StorageCategory.BOARD_ATTACHMENTS, 2L * 1024 * 1024 * 1024, 50, now)));
        var n = notification(
                60,
                NotificationType.STORAGE_WARNING,
                new NotificationParams.StorageWarning(91, "9.1 GiB", "10 GiB"),
                new NotificationData.NotificationLink("station-settings", Map.of("stationId", 7)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Largest categories"), "Header should be present: " + html);
        assertTrue(html.contains("•"), "Bullet marker should be present");
        int kbPos = html.indexOf("KB_FILES");
        int boardPos = html.indexOf("BOARD_ATTACHMENTS");
        assertTrue(kbPos > 0 && boardPos > 0, "Both category labels should appear in body");
        assertTrue(kbPos < boardPos, "KB_FILES (largest) should appear before BOARD_ATTACHMENTS");
        assertTrue(html.contains("GiB"));
    }

    @Test
    void procurementEnrichmentAddsInventoryType() {
        when(inventoryService.findById(99))
                .thenReturn(Optional.of(
                        new Inventory(99, 1, "Schlauch 25m", InventoryType.INTERNAL, false, true, false, null, null)));
        var n = notification(
                70,
                NotificationType.PROCUREMENT_REQUESTED,
                new NotificationParams.ProcurementRequested("Schlauch 25m"),
                new NotificationData.NotificationLink("inventory-procurement", Map.of("id", 99)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Type"), "Type row should be present: " + html);
        assertTrue(html.contains("INTERNAL"), "Inventory type should land in body: " + html);
    }

    @Test
    void procedureProgressEnrichmentMergesCheckedAndTotalIntoSingleRow() {
        var items = List.of(
                procedureItem(1, 80, "A", true),
                procedureItem(2, 80, "B", true),
                procedureItem(3, 80, "C", true),
                procedureItem(4, 80, "D", false),
                procedureItem(5, 80, "E", false));
        when(procedureService.findItems(80)).thenReturn(items);
        var n = notification(
                73,
                NotificationType.PROCEDURE_ASSIGNED,
                new NotificationParams.ProcedureAssigned("Quarterly truck check", "Alice"),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", 80)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Progress"), "Progress row label should be present: " + html);
        assertTrue(html.contains("3"), "Checked count should appear");
        assertTrue(html.contains("5"), "Total count should appear");
    }

    @Test
    void procedureProgressNoOpsOnEmptyItemList() {
        var n = notification(
                74,
                NotificationType.PROCEDURE_RESOLVED,
                new NotificationParams.ProcedureResolvedParams("Empty procedure"),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", 81)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertFalse(html.contains("Progress"));
    }

    private static ProcedureItem procedureItem(int id, int procedureId, String title, boolean checked) {
        return new ProcedureItem(
                id, procedureId, title, "", "", true, false, 0, checked, checked ? Instant.now() : null, null);
    }

    @Test
    void boardTicketEnrichmentAddsTitleAssigneeAndPriority() {
        var assignee = new MemberIdentity(UUID.randomUUID(), UUID.randomUUID(), "Alice Müller", null, null, null);
        var ticket = new BoardTicket(
                123,
                7,
                1,
                42,
                "Order new helmets for trainee group",
                "desc",
                assignee,
                TicketPriority.HIGH,
                null,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                0,
                0,
                0);
        when(boardTicketService.findById(123)).thenReturn(Optional.of(ticket));
        var n = notification(
                72,
                NotificationType.BOARD_TICKET_UPDATE,
                new NotificationParams.BoardTicketUpdate("Vorstand", "VOR-42", "moved to Done"),
                new NotificationData.NotificationLink(
                        "ticket-detail", Map.of("boardKey", "DEV", "ticketNumber", 42, "ticketId", 123)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertTrue(html.contains("Order new helmets"), "Ticket title should land in body: " + html);
        assertTrue(html.contains("Alice Müller"), "Assignee name should be present");
        assertTrue(html.contains("HIGH"), "Priority should be present (mock echoes enum name)");
    }

    @Test
    void procurementSilentlyNoOpsOnMissingInventoryId() {
        var n = notification(
                71,
                NotificationType.PROCUREMENT_FULFILLED,
                new NotificationParams.ProcurementFulfilled("Schlauch 25m"),
                new NotificationData.NotificationLink("inventory-procurement"));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertFalse(html.contains("Type"));
        assertTrue(html.contains("Schlauch 25m"));
    }

    @Test
    void storageWarningSilentlyNoOpsOnMissingStationId() {
        var n = notification(
                61,
                NotificationType.STORAGE_WARNING,
                new NotificationParams.StorageWarning(91, "9.1 GiB", "10 GiB"),
                new NotificationData.NotificationLink("station-settings"));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();
        assertFalse(html.contains("Largest categories"));
        assertTrue(html.contains("91%"));
    }

    private static StationEvent stubEvent(int id, Instant start, Instant end) {
        return new StationEvent(
                id,
                1,
                "Probe",
                "Konzert",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * An appointment is written in the clock of the station holding it, not the server's.
     *
     * <p>The one this is about ran from 09:00 to 14:00 in Berlin and reached the feed as 07:00 to
     * 12:00, because the machine rendering it keeps UTC. Both times are asserted, since reading the
     * start right and the end wrong is exactly what a half-fixed range looks like.
     */
    @Test
    void anAppointmentIsWrittenInTheStationsClockAndNotTheMachines() {
        when(stationRepository.findById(1)).thenReturn(Optional.of(berlinStation()));
        when(crudService.findById(42))
                .thenReturn(Optional.of(
                        stubEvent(42, Instant.parse("2026-09-19T07:00:00Z"), Instant.parse("2026-09-19T12:00:00Z"))));

        var n = notification(
                21,
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Konzertprobe"),
                new NotificationData.NotificationLink("event-detail", Map.of("id", 42)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();

        assertTrue(html.contains("09:00"), "the appointment starts when the station says it does: " + html);
        assertTrue(html.contains("14:00"), "and ends when it says it does: " + html);
        assertFalse(html.contains("07:00"), "not in the machine's clock: " + html);
    }

    /** A station with no zone falls back to UTC rather than to whatever the machine keeps. */
    @Test
    void aStationWithoutAZoneIsReadInUtc() {
        when(stationRepository.findById(1)).thenReturn(Optional.empty());
        when(crudService.findById(43))
                .thenReturn(Optional.of(
                        stubEvent(43, Instant.parse("2026-09-19T07:00:00Z"), Instant.parse("2026-09-19T12:00:00Z"))));

        var n = notification(
                22,
                NotificationType.NEW_EVENT,
                new NotificationParams.NewEvent("Probe", "Konzertprobe"),
                new NotificationData.NotificationLink("event-detail", Map.of("id", 43)));
        var html = renderer.render(n, richCtx()).getContents().getFirst().getValue();

        assertTrue(html.contains("07:00"), "UTC is the fallback: " + html);
    }

    private static Station berlinStation() {
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

    @Test
    void htmlEscapesUserSuppliedContent() {
        var n = notification(
                10,
                NotificationType.LOST_AND_FOUND_NEW,
                new NotificationParams.LostAndFoundNew("<script>alert(1)</script>"));
        var entry = renderer.render(n, richCtx());
        var html = entry.getContents().getFirst().getValue();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    private static Notification notification(int id, NotificationType type, NotificationParams params) {
        return notification(id, type, params, null);
    }

    private static Notification notification(
            int id, NotificationType type, NotificationParams params, NotificationData.NotificationLink link) {
        var data = link == null ? NotificationData.of(params) : NotificationData.of(params, link);
        return new Notification(id, 1, null, type, data, Instant.now(), null);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsServiceTest extends RepositoryTestBase {
    private static NewsService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;

    @BeforeAll
    static void setup() {
        service = newNewsService(new DomainEventBus(Set.of()));
        station = stationRepo.create("NewsStation");
        account = accountRepo.create("news-svc@test.com", "News", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var news = service.create(
                station.id(),
                "Test News",
                "Content of the news article",
                authorIdentity,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertNotNull(news);
        assertEquals("Test News", news.title());
        newsId = news.id();
    }

    /**
     * The HTML a reader is served is rendered here, from the Markdown, and never taken from
     * whoever asked for the entry. A browser's rendering is a convenience; this is the copy that
     * every reader is handed as markup, so it comes from a renderer and a sanitiser we control.
     */
    @Test
    @Order(7)
    void theBodyIsRenderedFromTheMarkdownAndSanitised() {
        var written = service.create(
                station.id(),
                "Mitbringen",
                "Bitte mitbringen:\n\n- Helm\n- Parka\n\n<script>alert(1)</script>",
                stationMemberRepo.resolveIdentity(member.id()),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        String html = service.findById(written.id()).orElseThrow().contentHtml();
        assertTrue(html.contains("<ul>") && html.contains("<li>Helm</li>"), "the list is a list: " + html);
        assertFalse(html.contains("<script"), "and nothing that could run came with it: " + html);

        service.delete(written.id());
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(newsId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = service.findByStation(station.id(), 0, 100);
        assertTrue(list.stream().anyMatch(n -> n.id() == newsId));
    }

    @Test
    @Order(10)
    void update() {
        var result =
                service.update(newsId, "Updated News", "Updated content", List.of(), List.of(), List.of(), List.of());
        assertTrue(result.isPresent());
        assertEquals("Updated News", result.get().title());
    }

    @Test
    @Order(4)
    void findVisibleForMember() {
        var list = service.findVisibleForMember(station.id(), member.id(), false, 0, 100);
        assertTrue(list.stream().anyMatch(n -> n.id() == newsId));
    }

    @Test
    @Order(6)
    void findRestrictions() {
        var restrictions = service.findRestrictions(newsId);
        assertNotNull(restrictions);
    }

    @Test
    @Order(28)
    void recordAndListViewers() {
        // Idempotent: two calls only produce one row.
        service.recordView(newsId, member.id());
        service.recordView(newsId, member.id());

        var summary = service.findViewerSummary(newsId, station.id());
        assertEquals(1, summary.seen().size(), "the only member should appear in the seen list");
        assertNotNull(summary.seen().getFirst().seenAt());
        assertEquals(member.uid(), summary.seen().getFirst().member().memberUid());
        // The only eligible member has now seen the news, so unseen is empty.
        assertEquals(0, summary.unseen().size());
    }

    @Test
    @Order(29)
    void countViewsAndHasViewed() {
        // Order 28 (recordAndListViewers) already recorded a view for member.
        assertEquals(1, service.countViews(newsId));
        assertTrue(service.hasViewed(newsId, member.id()));
        assertFalse(service.hasViewed(newsId, -42));
    }

    @Test
    @Order(29)
    void deleteNonExistentNews() {
        assertFalse(service.delete(-999));
    }

    @Test
    @Order(30)
    void delete() {
        assertTrue(service.delete(newsId));
        assertTrue(service.findById(newsId).isEmpty());
    }

    /**
     * An entry the instance publishes to every station at once.
     *
     * <p>It belongs to no station and has no author, which is the whole of what makes it a system
     * entry, and it turns up in a station's own list beside what that station wrote.
     */
    @Test
    @Order(40)
    void aSystemEntryBelongsToNoStationAndIsReadInOne() {
        var entry =
                service.createSystem("Wartungsarbeiten", "Am Freitag kurz nicht erreichbar.", List.of(), true, false);
        try {
            assertTrue(entry.systemEntry());
            assertNull(entry.author());
            assertTrue(service.isVisibleForMember(entry.id(), member.id(), false));
            assertTrue(
                    service.findVisibleForMember(station.id(), member.id(), false, 0, 50).stream()
                            .anyMatch(n -> n.id() == entry.id()),
                    "the station reads it alongside its own");
            assertTrue(
                    service.findSystem(0, 50).stream().anyMatch(n -> n.id() == entry.id()),
                    "the instance sees what it has published");
        } finally {
            service.delete(entry.id());
        }
    }

    /**
     * A system entry restricted to a user type the member does not have is not theirs to read, and
     * the restriction holds when the entry is asked for by id rather than listed.
     */
    @Test
    @Order(41)
    void aSystemEntryRestrictedToAnotherUserTypeIsNotVisible() {
        var entry =
                service.createSystem("Nur Betreuer", "Für die Leitung.", List.of(StationUserType.MANAGER), true, false);
        try {
            assertFalse(
                    service.isVisibleForMember(entry.id(), member.id(), false),
                    "a member who is not of that type does not read it");
        } finally {
            service.delete(entry.id());
        }
    }

    /**
     * Notifying is asked for rather than assumed, and when it is asked for every station is told:
     * the entry is one row, but a notification is addressed to somebody.
     */
    @Test
    @Order(43)
    void askingForANotificationTellsEveryStation() {
        var published = new java.util.ArrayList<Object>();
        var notifyingService = newNewsService(new DomainEventBus(Set.of()) {
            @Override
            public void publish(dev.chojo.ember.event.DomainEvent event) {
                published.add(event);
            }
        });
        var quiet = notifyingService.createSystem("Leise", "Nichts.", List.of(), true, false);
        int afterQuiet = published.size();
        var loud = notifyingService.createSystem("Laut", "Etwas.", List.of(), true, true);
        try {
            assertEquals(0, afterQuiet, "a quiet entry tells nobody");
            assertTrue(published.size() >= 1, "a loud one tells the stations");
        } finally {
            service.delete(quiet.id());
            service.delete(loud.id());
        }
    }

    /**
     * An entry is read by whoever it is addressed to, and a missing entry and one kept from the
     * member are refused alike.
     */
    @Test
    @Order(44)
    void anEntryIsReadableOnlyByItsAudience() {
        var open = service.createSystem("Offen", "Für alle.", List.of(), true, false);
        var kept =
                service.createSystem("Nur Betreuer", "Für die Leitung.", List.of(StationUserType.MANAGER), true, false);
        var reader = stationSession(member);
        try {
            assertEquals(open.id(), service.requireReadable(reader, open.id()).id());
            assertEquals(
                    Refusal.NEWS_NOT_HERE_OR_NOT_YOURS,
                    assertThrows(RefusalResponse.class, () -> service.requireReadable(reader, kept.id()))
                            .refusal());
            assertEquals(
                    Refusal.NEWS_NOT_HERE_OR_NOT_YOURS,
                    assertThrows(RefusalResponse.class, () -> service.requireReadable(reader, -1))
                            .refusal());
        } finally {
            service.delete(open.id());
            service.delete(kept.id());
        }
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.news.service.PublicBlogService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * What a comment on a news entry does today, pinned over the real routes, service and storage: who
 * reads, writes, changes and removes it, how it is listed and counted, and whom it tells.
 */
class NewsCommentBehaviourTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station station;
    private static Station elsewhere;
    private static Account authorAccount;
    private static Account otherAccount;
    private static Account strangerAccount;
    private static StationMember author;
    private static StationMember other;
    private static StationMember stranger;
    private static NewsService news;
    private static int entryId;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("News Comment Behaviour");
        elsewhere = stationRepo.create("News Comment Elsewhere");
        authorAccount = accountRepo.create("news-comment-author@test.com", "Anna", "Author");
        otherAccount = accountRepo.create("news-comment-other@test.com", "Otto", "Other");
        strangerAccount = accountRepo.create("news-comment-stranger@test.com", "Stella", "Stranger");
        author = stationMemberRepo.create(station.id(), authorAccount.id());
        other = stationMemberRepo.create(station.id(), otherAccount.id());
        stranger = stationMemberRepo.create(elsewhere.id(), strangerAccount.id());

        news = new NewsService(
                newsRepo,
                contentBlocks(),
                noCellDescriptions(),
                stationRepo,
                restrictionService,
                BUS,
                stationMemberRepo,
                memberLookupService,
                memberNameResolver,
                new CommentMentions(memberLookupService, BUS));
        entryId = newsRepo.create(station.id(), "Neuigkeit", "Text", "<p>Text</p>", null)
                .id();
        harness = RouteHarness.serving(new NewsRoutes(
                        news,
                        mock(NewsAttachmentService.class),
                        mock(NewsFederationService.class),
                        new PublicBlogService(stationRepo),
                        memberNameResolver,
                        memberIdentityFactory,
                        mock(EmailService.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(authorAccount.id());
        accountRepo.delete(otherAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    @BeforeEach
    void forgetEvents() {
        reset(BUS);
    }

    private static UserSession as(StationMember member, StationPermission... permissions) {
        var signed = signedIn(member, permissions);
        var stationUid = stationRepo.findById(member.stationId()).orElseThrow().uid();
        return new UserSession(
                signed.account(),
                signed.sessionId(),
                member.stationId(),
                stationUid,
                member,
                signed.permissions(),
                Set.of(),
                null);
    }

    private static Response post(StationMember member, int entry, String requestBody) {
        return harness.request(client ->
                client.post(PREFIX + "/news/%d/comments".formatted(entry), body(requestBody), harness.as(as(member))));
    }

    private static int write(StationMember member, int entry, String content) {
        return json(post(member, entry, "{\"content\": \"%s\"}".formatted(content)))
                .path("id")
                .asInt();
    }

    private static JsonNode list(StationMember member, int entry) {
        return json(harness.request(
                client -> client.get(PREFIX + "/news/%d/comments".formatted(entry), harness.as(as(member)))));
    }

    private static Response change(UserSession session, int commentId, String content) {
        return harness.request(client -> client.put(
                PREFIX + "/news/comments/" + commentId,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(session)));
    }

    private static Response remove(UserSession session, int commentId) {
        return harness.request(
                client -> client.delete(PREFIX + "/news/comments/" + commentId, null, harness.as(session)));
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    private static List<String> contents(JsonNode comments) {
        var texts = new ArrayList<String>();
        comments.forEach(comment -> texts.add(comment.path("content").asString()));
        return texts;
    }

    @Test
    void anotherStationNeitherReadsNorWrites() {
        harness.run((server, client) -> {
            assertEquals(
                    Refusal.NEWS_NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/news/%d/comments".formatted(entryId), harness.as(as(stranger)))));
            assertEquals(
                    Refusal.NEWS_NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.post(
                            PREFIX + "/news/%d/comments".formatted(entryId),
                            body("{\"content\": \"x\"}"),
                            harness.as(as(stranger)))));
        });
    }

    @Test
    void aMemberOutsideTheAudienceNeitherReadsNorWrites() {
        int kept = newsRepo.create(station.id(), "Nur Otto", "Text", "<p>Text</p>", null)
                .id();
        restrictionRepo.setRestrictions(
                RestrictionType.NEWS,
                kept,
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(other.id()), null));

        assertEquals(Refusal.NEWS_NOT_HERE_OR_NOT_YOURS, refusalOf(post(author, kept, "{\"content\": \"x\"}")));
        assertEquals(201, post(other, kept, "{\"content\": \"drin\"}").code());
    }

    @Test
    void onlyTheAuthorChangesACommentAndTheEditLeavesNoMark() {
        int id = write(author, entryId, "alt");

        assertEquals(
                Refusal.NEWS_COMMENT_NOT_YOURS_TO_EDIT,
                refusalOf(change(as(other, StationPermission.NEWS_MANAGER), id, "fremd")));
        var changed = json(change(as(author), id, "neu"));

        assertEquals("neu", changed.path("content").asString());
        assertTrue(changed.path("updatedAt").isMissingNode(), "a news comment never shows an edit");
    }

    @Test
    void anEmptyChangeIsRefused() {
        int id = write(author, entryId, "alt");

        assertEquals(Refusal.NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE, refusalOf(change(as(author), id, " ")));
    }

    @Test
    void theAuthorOrANewsManagerRemovesAComment() {
        int own = write(author, entryId, "eigen");
        int foreign = write(author, entryId, "fremd");
        int managed = write(author, entryId, "verwaltet");

        assertEquals(Refusal.NEWS_COMMENT_NOT_YOURS_TO_DELETE, refusalOf(remove(as(other), foreign)));
        assertEquals(204, remove(as(author), own).code());
        assertEquals(
                204, remove(as(other, StationPermission.NEWS_MANAGER), managed).code());
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == own
                        && deleted.entityType() == CommentEntityType.NEWS));
    }

    // TODO: enable once removing a news comment checks that it hangs under an entry of the caller's station
    @Disabled
    @Test
    void aNewsManagerOfAnotherStationCannotRemoveAComment() {
        int id = write(author, entryId, "bleibt");

        var answer = remove(as(stranger, StationPermission.NEWS_MANAGER), id);

        assertEquals(Refusal.NEWS_COMMENT_NOT_HERE_ON_DELETE, refusalOf(answer));
        assertTrue(newsRepo.findCommentById(id).isPresent());
    }

    @Test
    void theCountTakesPlaceholdersAlong() {
        int entry = newsRepo.create(station.id(), "Zaehlen", "Text", "<p>Text</p>", null)
                .id();
        int parent = write(author, entry, "Eltern");
        json(post(other, entry, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));

        remove(as(author), parent);

        assertTrue(newsRepo.findCommentById(parent).orElseThrow().deleted());
        assertEquals(2, news.countComments(entry));
    }

    @Test
    void underASystemEntryAStationReadsOnlyWhatItWrote() {
        var system = newsRepo.createSystem("An alle", "Text", "<p>Text</p>", true);
        try {
            write(author, system.id(), "von hier");
            write(stranger, system.id(), "von dort");

            assertEquals(List.of("von hier"), contents(list(other, system.id())));
            assertEquals(List.of("von dort"), contents(list(stranger, system.id())));
        } finally {
            newsRepo.delete(system.id());
        }
    }

    @Test
    void everyCommentIsAnnouncedWithItsPreviewCutByThreeDots() {
        int parent = write(author, entryId, "Frage");
        reset(BUS);

        json(post(other, entryId, "{\"content\": \"%s\", \"parentId\": %d}".formatted("y".repeat(120), parent)));
        write(other, entryId, "oben");

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(2, created.size());
        assertEquals(author.id(), created.getFirst().parentAuthorId());
        assertEquals("y".repeat(100) + "...", created.getFirst().preview());
        assertNull(created.get(1).parentAuthorId());
        assertEquals(CommentEntityType.NEWS, created.get(1).entityType());
    }

    @Test
    void theListingIsOldestFirst() {
        int entry = newsRepo.create(station.id(), "Reihe", "Text", "<p>Text</p>", null)
                .id();
        write(author, entry, "eins");
        write(other, entry, "zwei");
        write(author, entry, "drei");

        assertEquals(List.of("eins", "zwei", "drei"), contents(list(author, entry)));
    }
}

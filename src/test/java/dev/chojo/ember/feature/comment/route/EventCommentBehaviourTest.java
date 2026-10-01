/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * What a comment on an appointment does today, pinned over the real routes, service and storage:
 * who reads, writes, changes and removes it, how it is listed and whom it tells.
 */
class EventCommentBehaviourTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station station;
    private static Station elsewhere;
    private static Account authorAccount;
    private static Account otherAccount;
    private static Account strangerAccount;
    private static StationMember author;
    private static StationMember other;
    private static StationMember stranger;
    private static int eventId;
    private static int secondEventId;
    private static int hiddenEventId;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Comment Behaviour");
        elsewhere = stationRepo.create("Event Comment Elsewhere");
        authorAccount = accountRepo.create("event-comment-author@test.com", "Anna", "Author");
        otherAccount = accountRepo.create("event-comment-other@test.com", "Otto", "Other");
        strangerAccount = accountRepo.create("event-comment-stranger@test.com", "Stella", "Stranger");
        author = stationMemberRepo.create(station.id(), authorAccount.id());
        other = stationMemberRepo.create(station.id(), otherAccount.id());
        stranger = stationMemberRepo.create(elsewhere.id(), strangerAccount.id());
        eventId = event("Sommerfest");
        secondEventId = event("Winterfest");
        hiddenEventId = event("Vorstandssitzung");
        var services = newEventServices(new DomainEventBus(Set.of()));
        services.restriction()
                .setViewRestrictions(
                        hiddenEventId,
                        new RestrictionSelection(List.of(), List.of(), List.of(), List.of(other.id()), null));

        var comments = new CommentService(
                eventCommentRepo,
                BUS,
                newStationMemberService(null, null),
                stationRepo,
                new CommentMentions(memberLookupService, BUS));
        var visibility =
                new EventVisibility(services.crud(), services.restriction(), new GuardianPolicy(stationMemberRepo));
        harness = RouteHarness.serving(
                        new EventCommentRoutes(comments, visibility, memberIdentityFactory, memberNameResolver))
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

    private static int event(String name) {
        return eventRepo
                .create(
                        station.id(),
                        name,
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    private static UserSession as(StationMember member, StationPermission... permissions) {
        return signedIn(member, permissions);
    }

    private static int write(StationMember member, int event, String content) {
        return json(post(member, event, "{\"content\": \"%s\"}".formatted(content)))
                .path("id")
                .asInt();
    }

    private static Response post(StationMember member, int event, String requestBody) {
        return harness.request(client -> client.post(
                PREFIX + "/events/%d/comments".formatted(event), body(requestBody), harness.as(as(member))));
    }

    private static JsonNode list(StationMember member, int event, String query) {
        return json(harness.request(client ->
                client.get(PREFIX + "/events/%d/comments%s".formatted(event, query), harness.as(as(member)))));
    }

    private static Response change(UserSession session, int commentId, String content) {
        return harness.request(client -> client.put(
                PREFIX + "/events/comments/" + commentId,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(session)));
    }

    private static Response remove(UserSession session, int commentId) {
        return harness.request(
                client -> client.delete(PREFIX + "/events/comments/" + commentId, null, harness.as(session)));
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void anotherStationNeitherReadsNorWrites() {
        write(author, eventId, "Hallo");

        harness.run((server, client) -> {
            assertEquals(
                    Refusal.NOT_YOURS_TO_OPEN,
                    refusalOf(client.get(PREFIX + "/events/%d/comments".formatted(eventId), harness.as(as(stranger)))));
            assertEquals(
                    Refusal.NOT_YOURS_TO_OPEN,
                    refusalOf(client.post(
                            PREFIX + "/events/%d/comments".formatted(eventId),
                            body("{\"content\": \"x\"}"),
                            harness.as(as(stranger)))));
        });
    }

    @Test
    void aMemberOutsideTheAudienceNeitherReadsNorWrites() {
        harness.run((server, client) -> {
            assertEquals(
                    Refusal.EVENT_NOT_YOURS_TO_SEE,
                    refusalOf(client.get(
                            PREFIX + "/events/%d/comments".formatted(hiddenEventId), harness.as(as(author)))));
            assertEquals(
                    Refusal.EVENT_NOT_YOURS_TO_SEE,
                    refusalOf(client.post(
                            PREFIX + "/events/%d/comments".formatted(hiddenEventId),
                            body("{\"content\": \"x\"}"),
                            harness.as(as(author)))));
        });
        assertEquals(201, post(other, hiddenEventId, "{\"content\": \"drin\"}").code());
    }

    @Test
    void onlyTheAuthorChangesAComment() {
        int id = write(author, eventId, "alt");

        assertEquals(
                Refusal.COMMENT_NOT_YOURS_TO_CHANGE,
                refusalOf(change(as(other, StationPermission.EVENT_MANAGER), id, "fremd")));
        var changed = json(change(as(author), id, "neu"));

        assertEquals("neu", changed.path("content").asString());
        assertFalse(changed.path("updatedAt").isMissingNode(), "an edited comment carries its edit time");
    }

    @Test
    void anEmptyChangeIsRefused() {
        int id = write(author, eventId, "alt");

        assertEquals(Refusal.COMMENT_CHANGE_NEEDS_TEXT, refusalOf(change(as(author), id, " ")));
    }

    @Test
    void theAuthorOrAnEventManagerOfTheStationRemovesAComment() {
        int own = write(author, eventId, "eigen");
        int foreign = write(author, eventId, "fremd");
        int managed = write(author, eventId, "verwaltet");

        assertEquals(Refusal.COMMENT_NOT_YOURS_TO_DELETE, refusalOf(remove(as(other), foreign)));
        assertEquals(
                Refusal.NOT_YOURS_TO_OPEN, refusalOf(remove(as(stranger, StationPermission.EVENT_MANAGER), managed)));
        assertEquals(204, remove(as(author), own).code());
        assertEquals(
                204, remove(as(other, StationPermission.EVENT_MANAGER), managed).code());
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == own
                        && deleted.stationId() == station.id()));
    }

    @Test
    void aCommentWithRepliesLeavesAPlaceholderAndOneWithoutVanishes() {
        int parent = write(author, eventId, "Eltern");
        json(post(other, eventId, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));
        int single = write(author, eventId, "allein");

        remove(as(author), parent);
        remove(as(author), single);

        var placeholder = eventCommentRepo.findById(parent).orElseThrow();
        assertTrue(placeholder.deleted());
        assertEquals("", placeholder.content());
        assertTrue(eventCommentRepo.findById(single).isEmpty());
    }

    @Test
    void aReplyMayNameAParentOnAnotherAppointment() {
        int parent = write(author, secondEventId, "anderswo");

        var reply = json(post(other, eventId, "{\"content\": \"quer\", \"parentId\": %d}".formatted(parent)));

        assertEquals(parent, reply.path("parentId").asInt());
    }

    @Test
    void theListingIsOldestFirstAndCanBeNarrowedToOneDay() {
        int day = event("Serie");
        json(post(author, day, "{\"content\": \"ganz\"}"));
        json(post(author, day, "{\"content\": \"am Tag\", \"eventDate\": \"2027-05-01\"}"));
        json(post(author, day, "{\"content\": \"spaeter\"}"));

        assertEquals(List.of("ganz", "am Tag", "spaeter"), contents(list(author, day, "")));
        assertEquals(List.of("am Tag"), contents(list(author, day, "?date=2027-05-01")));
        assertEquals(List.of("ganz", "spaeter"), contents(list(author, day, "?scope=date&date=none")));
        assertEquals(
                Refusal.COMMENT_DAY_NOT_A_DATE,
                refusalOf(harness.request(client -> client.get(
                        PREFIX + "/events/%d/comments?date=morgen".formatted(day), harness.as(as(author))))));
    }

    @Test
    void aReplyTellsTheParentsAuthorWithAShortenedPreview() {
        int parent = write(author, eventId, "Frage");
        reset(BUS);

        String longText = "x".repeat(120);
        json(post(other, eventId, "{\"content\": \"%s\", \"parentId\": %d}".formatted(longText, parent)));

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(author.id(), created.getFirst().parentAuthorId());
        assertEquals("x".repeat(100) + "…", created.getFirst().preview());
    }

    @Test
    void aTopLevelCommentTellsNobodyButTheMentioned() {
        var mentioned = stationMemberRepo.findById(other.id()).orElseThrow();
        write(author, eventId, "Hallo @[%s/%s:Otto]".formatted(station.uid(), mentioned.uid()));

        var events = published();
        assertTrue(events.stream().noneMatch(CommentCreated.class::isInstance));
        assertTrue(events.stream()
                .anyMatch(event ->
                        event instanceof MentionedInComment mention && mention.mentionedMemberId() == other.id()));
    }

    private static List<String> contents(JsonNode comments) {
        var texts = new ArrayList<String>();
        comments.forEach(comment -> texts.add(comment.path("content").asString()));
        return texts;
    }
}

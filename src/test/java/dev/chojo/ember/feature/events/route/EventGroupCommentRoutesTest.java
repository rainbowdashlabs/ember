/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * The comments on an event group over the real routes, service and storage: who reads, writes,
 * changes and removes them, that a comment is reached only through its own event group, and whom a
 * comment tells.
 */
class EventGroupCommentRoutesTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station station;
    private static Station elsewhere;
    private static final List<Integer> accounts = new ArrayList<>();
    private static StationMember author;
    private static StationMember other;
    private static StationMember stranger;
    private static int eventGroupId;
    private static int secondEventGroupId;
    private static int hiddenEventGroupId;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Group Comments");
        elsewhere = stationRepo.create("Event Group Comments Elsewhere");
        author = member(station, "event-group-comment-author@test.com", "Anna");
        other = member(station, "event-group-comment-other@test.com", "Otto");
        stranger = member(elsewhere, "event-group-comment-stranger@test.com", "Stella");
        eventGroupId = eventGroup("Jugendflamme 1", RestrictionAudience.empty());
        secondEventGroupId = eventGroup("Jugendflamme 2", RestrictionAudience.empty());
        hiddenEventGroupId = eventGroup(
                "Leistungsabzeichen",
                new RestrictionAudience(List.of(), List.of(), List.of(), List.of(other.id()), RestrictionMode.AND));
        harness = RouteHarness.serving(
                        new EventGroupCommentRoutes(newCommentService(BUS), memberIdentityFactory, memberNameResolver))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void forgetEvents() {
        reset(BUS);
    }

    private static StationMember member(Station at, String mail, String name) {
        Account account = accountRepo.create(mail, name, "Kommentar");
        accounts.add(account.id());
        return stationMemberRepo.create(at.id(), account.id());
    }

    private static int eventGroup(String name, RestrictionAudience view) {
        return newEventGroupService()
                .create(
                        station.id(),
                        new EventGroupDetails(name, null, null, null, false, null),
                        List.of(new EventGroupSetDraft(null, "Übung", 1)),
                        RestrictionAudience.empty(),
                        view,
                        null)
                .id();
    }

    /** A member of the station, who holds the general user right besides the ones given. */
    private static UserSession as(StationMember member, StationPermission... permissions) {
        var held = new ArrayList<>(List.of(permissions));
        held.add(StationPermission.USER);
        return stationSession(member, held.toArray(StationPermission[]::new)).user();
    }

    @Test
    void aSignedInMemberTheEventGroupIsMeantForNeedsNoFurtherRightAsOnAnAppointment() {
        var bare = stationSession(author).user();

        assertEquals(200, read(bare, eventGroupId).code());
        assertEquals(
                201, post(bare, eventGroupId, "{\"content\": \"ohne Recht\"}").code());
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(read(bare, hiddenEventGroupId)));
    }

    private static String comments(int group) {
        return PREFIX + "/events/groups/%d/comments".formatted(group);
    }

    private static Response post(UserSession session, int group, String requestBody) {
        return harness.request(client -> client.post(comments(group), body(requestBody), harness.as(session)));
    }

    private static int write(StationMember member, int group, String content) {
        return json(post(as(member), group, "{\"content\": \"%s\"}".formatted(content)))
                .path("id")
                .asInt();
    }

    private static Response read(UserSession session, int group) {
        return harness.request(client -> client.get(comments(group), harness.as(session)));
    }

    private static Response change(UserSession session, int group, int commentId, String content) {
        return harness.request(client -> client.put(
                comments(group) + "/" + commentId,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(session)));
    }

    private static Response remove(UserSession session, int group, int commentId) {
        return harness.request(client -> client.delete(comments(group) + "/" + commentId, null, harness.as(session)));
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
    void whoMayOpenTheEventGroupReadsAndWritesOldestFirst() {
        int group = eventGroup("Lesen", RestrictionAudience.empty());
        write(author, group, "erste");
        write(other, group, "zweite");

        assertEquals(List.of("erste", "zweite"), contents(json(read(as(author), group))));
    }

    @Test
    void anEmptyCommentIsRefused() {
        assertEquals(
                CommentRefusal.COMMENT_NEEDS_TEXT, refusalOf(post(as(author), eventGroupId, "{\"content\": \" \"}")));
    }

    @Test
    void aMemberTheEventGroupIsNotMeantForNeitherReadsNorWrites() {
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(read(as(author), hiddenEventGroupId)));
        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                refusalOf(post(as(author), hiddenEventGroupId, "{\"content\": \"x\"}")));
        assertEquals(
                201,
                post(as(other), hiddenEventGroupId, "{\"content\": \"drin\"}").code());
    }

    @Test
    void whoeverRunsEventsReadsAHiddenEventGroup() {
        for (var permission : List.of(
                StationPermission.EVENT_EDIT, StationPermission.EVENT_MANAGER, StationPermission.EVENT_REGISTRATION)) {
            assertEquals(200, read(as(author, permission), hiddenEventGroupId).code(), permission.name());
        }
    }

    @Test
    void anotherStationNeitherReadsNorWrites() {
        write(author, eventGroupId, "Hallo");

        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(read(as(stranger), eventGroupId)));
        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                refusalOf(post(as(stranger, StationPermission.EVENT_MANAGER), eventGroupId, "{\"content\": \"x\"}")));
    }

    @Test
    void onlyTheAuthorChangesAComment() {
        int id = write(author, eventGroupId, "alt");

        assertEquals(
                CommentRefusal.COMMENT_NOT_YOURS_TO_CHANGE,
                refusalOf(change(as(other, StationPermission.EVENT_MANAGER), eventGroupId, id, "fremd")));
        assertEquals(CommentRefusal.COMMENT_CHANGE_NEEDS_TEXT, refusalOf(change(as(author), eventGroupId, id, " ")));
        assertEquals(
                "neu",
                json(change(as(author), eventGroupId, id, "neu"))
                        .path("content")
                        .asString());
    }

    @Test
    void aCommentIsOnlyReachedThroughItsOwnEventGroup() {
        int id = write(author, eventGroupId, "hier");

        assertEquals(
                CommentRefusal.COMMENT_NOT_HERE_ON_CHANGE,
                refusalOf(change(as(author), secondEventGroupId, id, "dort")));
        assertEquals(CommentRefusal.COMMENT_NOT_HERE_ON_DELETE, refusalOf(remove(as(author), secondEventGroupId, id)));
    }

    @Test
    void aCommentUnderAHiddenEventGroupStaysOutOfReach() {
        int id = write(other, hiddenEventGroupId, "geheim");

        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(remove(as(author), hiddenEventGroupId, id)));
    }

    @Test
    void theAuthorOrAnEventManagerRemovesAComment() {
        int own = write(author, eventGroupId, "eigen");
        int foreign = write(author, eventGroupId, "fremd");

        assertEquals(CommentRefusal.COMMENT_NOT_YOURS_TO_DELETE, refusalOf(remove(as(other), eventGroupId, foreign)));
        assertEquals(
                CommentRefusal.COMMENT_NOT_YOURS_TO_DELETE,
                refusalOf(remove(as(other, StationPermission.EVENT_EDIT), eventGroupId, foreign)));
        assertEquals(204, remove(as(author), eventGroupId, own).code());
        assertEquals(
                204,
                remove(as(other, StationPermission.EVENT_MANAGER), eventGroupId, foreign)
                        .code());
        assertTrue(commentRepo.findById(CommentEntityType.EVENT_GROUP, foreign).isEmpty());
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == own
                        && deleted.entityType() == CommentEntityType.EVENT_GROUP));
    }

    @Test
    void aReplyToACommentOnAnotherEventGroupIsRefused() {
        int parent = write(author, secondEventGroupId, "anderswo");

        var answer = post(as(other), eventGroupId, "{\"content\": \"quer\", \"parentId\": %d}".formatted(parent));

        assertEquals(CommentRefusal.COMMENT_PARENT_ELSEWHERE, refusalOf(answer));
    }

    @Test
    void aReplyTellsTheParentsAuthor() {
        int parent = write(author, eventGroupId, "Frage");
        reset(BUS);

        json(post(as(other), eventGroupId, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(author.id(), created.getFirst().parentAuthorId());
    }

    /** A new thread tells the appointment managers and opens on the event group's page. */
    @Test
    void aTopLevelCommentTellsTheManagersAndTheMentioned() {
        var mentioned = stationMemberRepo.findById(other.id()).orElseThrow();
        int id = write(author, eventGroupId, "Hallo @[%s/%s:Otto]".formatted(station.uid(), mentioned.uid()));

        var events = published();
        var created = events.stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(CommentEntityType.EVENT_GROUP, created.getFirst().entityType());
        assertEquals("Jugendflamme 1", created.getFirst().entityTitle());
        assertEquals(
                NotificationLinks.comment(NotificationLinks.eventGroup(eventGroupId), id),
                created.getFirst().link());
        assertEquals(
                StationAudience.holders(station.id(), StationPermission.EVENT_MANAGER)
                        .except(author.id())
                        .except(other.id()),
                created.getFirst().toldOfNewThread());
        assertTrue(events.stream()
                .anyMatch(event ->
                        event instanceof MentionedInComment mention && mention.mentionedMemberId() == other.id()));
    }
}

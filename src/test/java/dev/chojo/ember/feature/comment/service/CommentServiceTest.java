/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * The comment service on appointment comments, which is the kind bound to it today, and on a target
 * of its own for what no appointment does.
 */
class CommentServiceTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static CommentService service;
    private static Station station;
    private static Station elsewhere;
    private static Account aliceAccount;
    private static Account bobAccount;
    private static Account carolAccount;
    private static StationMember alice;
    private static StationMember bob;
    private static StationMember carol;
    private static int eventId;
    private static int otherEventId;

    @BeforeAll
    static void setup() {
        service = newCommentService(BUS);
        station = stationRepo.create("CommentStation");
        elsewhere = stationRepo.create("CommentElsewhere");
        aliceAccount = accountRepo.create("comment1@test.com", "Alice", "Author");
        bobAccount = accountRepo.create("comment2@test.com", "Bob", "Mentioned");
        carolAccount = accountRepo.create("comment3@test.com", "Carol", "Elsewhere");
        alice = stationMemberRepo.create(station.id(), aliceAccount.id());
        bob = stationMemberRepo.create(station.id(), bobAccount.id());
        carol = stationMemberRepo.create(elsewhere.id(), carolAccount.id());
        eventId = event("Test Event");
        otherEventId = event("Other Event");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(aliceAccount.id());
        accountRepo.delete(bobAccount.id());
        accountRepo.delete(carolAccount.id());
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

    private static MemberIdentity identity(StationMember member) {
        return memberIdentityFactory.local(member.stationId(), member.id());
    }

    private static CommentWriter writer(StationMember member, String name) {
        return CommentWriter.local(identity(member), name);
    }

    private static Comment write(StationMember member, String name, Integer parentId, String content) {
        return service.create(
                stationSession(member),
                CommentEntityType.EVENT,
                eventId,
                writer(member, name),
                new NewComment(parentId, null, content));
    }

    private static String mention(Station memberStation, StationMember member, String name) {
        return "@[" + memberStation.uid() + "/" + member.uid() + ":" + name + "]";
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void aTargetIsFoundWithItsStationAndTitle() {
        var target = service.target(CommentEntityType.EVENT, eventId).orElseThrow();

        assertEquals(station.id(), target.stationId());
        assertEquals("Test Event", target.title());
        assertTrue(service.target(CommentEntityType.EVENT, -1).isEmpty());
    }

    @Test
    void aMissingTargetIsRefusedTheWayItsKindNamesIt() {
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.requireReadable(stationSession(alice), CommentEntityType.EVENT, -1));

        assertEquals(Refusal.EVENT_NOT_HERE, refused.refusal());
        assertEquals(
                eventId,
                service.requireReadable(stationSession(alice), CommentEntityType.EVENT, eventId)
                        .id());
    }

    @Test
    void aReplyTellsTheParentsAuthorAndCutsThePreview() {
        var parent = write(alice, "Alice", null, "Frage");
        reset(BUS);

        var reply = write(bob, "Bob", parent.id(), "b".repeat(120));

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(alice.id(), created.parentAuthorId());
        assertEquals(bob.id(), created.authorMemberId());
        assertEquals("Bob", created.authorName());
        assertEquals("Test Event", created.entityTitle());
        assertEquals("b".repeat(100) + "…", created.preview());
        assertNull(created.alsoTold());
        assertEquals(NotificationLinks.comment(NotificationLinks.event(eventId), reply.id()), created.link());
    }

    @Test
    void aReplyToOneselfAndATopLevelCommentTellNobody() {
        var parent = write(alice, "Alice", null, "Ich");
        write(alice, "Alice", parent.id(), "Ich nochmal");

        assertTrue(published().stream().noneMatch(CommentCreated.class::isInstance));
    }

    @Test
    void anAnswerTakesItsParentsOccurrenceDateWhateverItWasSentWith() {
        var day = LocalDate.of(2026, 3, 14);
        var parent = service.create(
                stationSession(alice),
                CommentEntityType.EVENT,
                eventId,
                writer(alice, "Alice"),
                new NewComment(null, day, "An diesem Tag"));

        var undated = service.create(
                stationSession(bob),
                CommentEntityType.EVENT,
                eventId,
                writer(bob, "Bob"),
                new NewComment(parent.id(), null, "Antwort ohne Tag"));
        var otherDay = service.create(
                stationSession(bob),
                CommentEntityType.EVENT,
                eventId,
                writer(bob, "Bob"),
                new NewComment(parent.id(), day.plusDays(7), "Antwort mit anderem Tag"));

        assertEquals(day, undated.eventDate());
        assertEquals(day, otherDay.eventDate());
    }

    @Test
    void anAnswerToACommentOnAnotherTargetIsRefused() {
        var elsewhereComment = service.create(
                stationSession(alice),
                CommentEntityType.EVENT,
                otherEventId,
                writer(alice, "Alice"),
                new NewComment(null, null, "anderswo"));
        var onNews = commentRepo.create(
                CommentEntityType.NEWS,
                newsRepo.create(station.id(), "Neu", "x", "x", null).id(),
                null,
                null,
                identity(alice),
                "Neuigkeit");

        for (int parentId : List.of(elsewhereComment.id(), onNews.id(), -1)) {
            var refused = assertThrows(RefusalResponse.class, () -> write(bob, "Bob", parentId, "quer"));
            assertEquals(Refusal.COMMENT_PARENT_ELSEWHERE, refused.refusal());
        }
    }

    @Test
    void aMentionTellsTheMentionedMemberOnceAndNeverTheAuthor() {
        String content = "Hey " + mention(station, bob, "Bob") + " und " + mention(station, bob, "Bob") + " und "
                + mention(station, alice, "Alice") + " und " + mention(elsewhere, carol, "Carol");

        var comment = write(alice, "Alice", null, content);

        var mentioned = published().stream()
                .filter(MentionedInComment.class::isInstance)
                .map(MentionedInComment.class::cast)
                .toList();
        assertEquals(1, mentioned.size());
        assertEquals(bob.id(), mentioned.getFirst().mentionedMemberId());
        assertEquals(alice.id(), mentioned.getFirst().authorMemberId());
        assertEquals(comment.id(), mentioned.getFirst().commentId());
        assertEquals(CommentEntityType.EVENT, mentioned.getFirst().entityType());
    }

    @Test
    void aCommentFromAPartnerTellsNobody() {
        var parent = write(alice, "Alice", null, "Frage");
        reset(BUS);
        var target = service.target(CommentEntityType.EVENT, eventId).orElseThrow();

        var comment = service.createOn(
                target,
                CommentWriter.partner(new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()), "Partner"),
                new NewComment(parent.id(), null, "Antwort " + mention(station, bob, "Bob")));

        assertEquals(parent.id(), comment.parentId());
        assertTrue(published().isEmpty());
    }

    @Test
    void theListingFollowsTheFilter() {
        int event = event("Liste");
        var whole = service.create(
                stationSession(alice),
                CommentEntityType.EVENT,
                event,
                writer(alice, "Alice"),
                new NewComment(null, null, "ganz"));
        var dated = service.create(
                stationSession(alice),
                CommentEntityType.EVENT,
                event,
                writer(alice, "Alice"),
                new NewComment(null, LocalDate.of(2027, 6, 1), "am Tag"));

        assertEquals(
                2,
                service.list(CommentEntityType.EVENT, event, CommentFilter.ALL).size());
        assertEquals(2, service.count(CommentEntityType.EVENT, event));
        assertEquals(
                List.of(whole.id()),
                service.list(CommentEntityType.EVENT, event, new CommentFilter.Occurrence(null)).stream()
                        .map(Comment::id)
                        .toList());
        assertEquals(
                List.of(dated.id()),
                service
                        .list(CommentEntityType.EVENT, event, new CommentFilter.Occurrence(LocalDate.of(2027, 6, 1)))
                        .stream()
                        .map(Comment::id)
                        .toList());
        assertEquals(
                2,
                service.list(CommentEntityType.EVENT, event, new CommentFilter.FromStation(station.uid()))
                        .size());
    }

    @Test
    void onlyTheAuthorOrAModeratorMayModify() {
        var comment = write(alice, "Alice", null, "meins");

        assertTrue(service.mayModify(stationSession(alice), identity(alice), comment, Moderation.EDIT));
        assertFalse(service.mayModify(stationSession(bob), identity(bob), comment, Moderation.EDIT));
        assertFalse(service.mayModify(stationSession(bob), identity(bob), comment, Moderation.DELETE));
        assertTrue(service.mayModify(
                stationSession(bob, StationPermission.EVENT_MANAGER), identity(bob), comment, Moderation.DELETE));
        assertFalse(service.mayModify(
                stationSession(bob, StationPermission.EVENT_MANAGER), identity(bob), comment, Moderation.EDIT));
    }

    @Test
    void anotherStationIsRefusedTheComment() {
        var comment = write(alice, "Alice", null, "intern");

        service.requireSameStation(stationSession(bob), comment);
        var refused =
                assertThrows(RefusalResponse.class, () -> service.requireSameStation(stationSession(carol), comment));
        assertEquals(Refusal.NOT_YOURS_TO_OPEN, refused.refusal());
    }

    @Test
    void anEditAnnouncesOnlyTheMentionsItAdds() {
        String kept = "Hallo " + mention(station, bob, "Bob");
        var comment = write(alice, "Alice", null, "Hallo");
        reset(BUS);

        var edited = service.update(comment, writer(alice, "Alice"), kept).orElseThrow();
        assertEquals(kept, edited.content());
        assertEquals(1, published().size());
        reset(BUS);

        service.update(edited, writer(alice, "Alice"), kept + ", Tippfehler");
        assertTrue(published().isEmpty());
    }

    @Test
    void anEditFromAPartnerAnnouncesNothing() {
        var comment = write(alice, "Alice", null, "Hallo");
        reset(BUS);

        service.update(
                comment, CommentWriter.partner(identity(alice), "Partner"), "Hallo " + mention(station, bob, "Bob"));

        assertTrue(published().isEmpty());
    }

    @Test
    void anEditOfAGoneCommentReportsNothing() {
        var comment = write(alice, "Alice", null, "weg");
        service.delete(comment);

        assertTrue(service.update(comment, writer(alice, "Alice"), "neu").isEmpty());
        assertFalse(service.delete(comment));
    }

    @Test
    void aRemovalLeavesAPlaceholderUnderRepliesAndIsAnnounced() {
        var parent = write(alice, "Alice", null, "Eltern");
        var reply = write(bob, "Bob", parent.id(), "Antwort");
        reset(BUS);

        assertTrue(service.delete(parent));

        var placeholder = service.findById(CommentEntityType.EVENT, parent.id()).orElseThrow();
        assertTrue(placeholder.deleted());
        assertTrue(service.findById(CommentEntityType.EVENT, reply.id()).isPresent());
        var deleted = (CommentDeleted) published().getFirst();
        assertEquals(parent.id(), deleted.commentId());
        assertEquals(station.id(), deleted.stationId());
        assertEquals(NotificationLinks.comment(NotificationLinks.event(eventId), parent.id()), deleted.link());
        assertTrue(service.findById(CommentEntityType.NEWS, parent.id()).isEmpty());
    }

    /**
     * A target may name people besides the thread to tell about every comment, and a target owned
     * by no station still takes comments, which then tell within the station they were written
     * from.
     */
    @Test
    void aTargetCanTellOthersAboutEveryComment() {
        int news = newsRepo.createSystem("An alle", "x", "x", true).id();
        var others = StationAudience.member(bob.id());
        var target = new TargetInfo(CommentEntityType.NEWS, news, null, "An alle", null, true);
        var link = new NotificationLink("somewhere");
        var told = new CommentService(
                commentRepo,
                Map.of(
                        CommentEntityType.NEWS,
                        new FixedTarget(target, new CreatedAudience(false, false, others), link)),
                BUS,
                newStationMemberService(null, null),
                stationRepo,
                new CommentMentions(memberLookupService, BUS));

        var comment = told.createOn(target, writer(alice, "Alice"), new NewComment(null, null, "Hallo"));

        var created = (CommentCreated) published().getFirst();
        assertEquals(others, created.alsoTold());
        assertEquals(station.id(), created.stationId());
        assertEquals(alice.id(), created.authorMemberId());
        assertNull(comment.stationId());
        newsRepo.delete(news);
    }

    /** A target that answers the same for every member, for what no appointment does. */
    private record FixedTarget(TargetInfo target, CreatedAudience audience, NotificationLink page)
            implements CommentTarget {
        @Override
        public CommentEntityType type() {
            return target.type();
        }

        @Override
        public Optional<TargetInfo> find(int targetId) {
            return Optional.of(target);
        }

        @Override
        public Refusal missing() {
            return Refusal.NEWS_NOT_HERE_OR_NOT_YOURS;
        }

        @Override
        public void requireReadable(StationSession session, TargetInfo target) {}

        @Override
        public void requireWritable(StationSession session, TargetInfo target) {}

        @Override
        public boolean mayModerate(StationSession session, TargetInfo target, Moderation action) {
            return false;
        }

        @Override
        public CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin) {
            return audience;
        }

        @Override
        public NotificationLink link(TargetInfo target, int commentId) {
            return NotificationLinks.comment(page, commentId);
        }
    }
}

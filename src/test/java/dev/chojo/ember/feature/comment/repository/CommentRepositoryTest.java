/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one store of comments: every kind of target, kept apart by the column that names it.
 */
class CommentRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static MemberIdentity author;
    private static int boardId;
    private static int laneId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Comment Repository");
        account = accountRepo.create("comment-repository@test.com", "Cora", "Comment");
        member = stationMemberRepo.create(station.id(), account.id());
        author = memberIdentityFactory.local(station.id(), member.id());
        boardId = boardRepo.create(station.id(), "Comment Board", null, "CRB").id();
        laneId = boardRepo.createLane(boardId, "Open", null, 0).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int event() {
        return eventRepo
                .create(
                        station.id(),
                        "Termin",
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

    private static int news() {
        return newsRepo.create(station.id(), "Neuigkeit", "Text", "<p>Text</p>", null)
                .id();
    }

    private static int file() {
        return knowledgeBaseRepo
                .createFile(station.id(), null, "Datei", "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id())
                .id();
    }

    private static int ticket() {
        int number = boardRepo.nextTicketNumber(boardId);
        return boardTicketRepo
                .createTicket(boardId, laneId, number, "Aufgabe", "", null, TicketPriority.MEDIUM, null, 0, author)
                .id();
    }

    private static Comment write(CommentEntityType type, int target, String content) {
        return commentRepo.create(type, target, null, null, author, content);
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    void everyKindIsStoredWithItsTargetAndTheTargetsStation() {
        int event = event();
        int news = news();
        int file = file();
        int ticket = ticket();

        var onEvent = write(CommentEntityType.EVENT, event, "Termin");
        var onNews = write(CommentEntityType.NEWS, news, "Neuigkeit");
        var onFile = write(CommentEntityType.KB, file, "Datei");
        var onTicket = write(CommentEntityType.BOARD_TICKET, ticket, "Aufgabe");

        assertEquals(CommentEntityType.EVENT, onEvent.type());
        assertEquals(event, onEvent.targetId());
        assertEquals(CommentEntityType.NEWS, onNews.type());
        assertEquals(news, onNews.targetId());
        assertEquals(CommentEntityType.KB, onFile.type());
        assertEquals(file, onFile.targetId());
        assertEquals(CommentEntityType.BOARD_TICKET, onTicket.type());
        assertEquals(ticket, onTicket.targetId());
        for (var comment : List.of(onEvent, onNews, onFile, onTicket)) {
            assertEquals(station.id(), comment.stationId());
            assertTrue(author.sameMember(comment.author()));
            assertFalse(comment.deleted());
            assertNotNull(comment.createdAt());
            assertNull(comment.updatedAt());
        }
    }

    @Test
    void aCommentUnderASystemEntryBelongsToNoStation() {
        var system = newsRepo.createSystem("An alle", "Text", "<p>Text</p>", true);
        try {
            var comment = write(CommentEntityType.NEWS, system.id(), "von hier");

            assertNull(comment.stationId());
        } finally {
            newsRepo.delete(system.id());
        }
    }

    @Test
    void aCommentWithoutAuthorKeepsNone() {
        var comment = commentRepo.create(CommentEntityType.EVENT, event(), null, null, null, "anonym");

        assertNull(comment.author());
    }

    @Test
    void aCommentIsFoundOnlyAsItsOwnKind() {
        var comment = write(CommentEntityType.NEWS, news(), "Neuigkeit");

        assertTrue(commentRepo.findById(CommentEntityType.NEWS, comment.id()).isPresent());
        assertTrue(commentRepo.findById(CommentEntityType.EVENT, comment.id()).isEmpty());
        assertTrue(commentRepo.findById(CommentEntityType.NEWS, -1).isEmpty());
        assertFalse(commentRepo.update(CommentEntityType.KB, comment.id(), "fremd"));
        assertFalse(commentRepo.delete(CommentEntityType.BOARD_TICKET, comment.id()));
        assertEquals(
                "Neuigkeit",
                commentRepo
                        .findById(CommentEntityType.NEWS, comment.id())
                        .orElseThrow()
                        .content());
    }

    @Test
    void aListingIsOldestFirstWithTheIdBreakingTies() throws SQLException {
        int file = file();
        var first = write(CommentEntityType.KB, file, "eins");
        var second = write(CommentEntityType.KB, file, "zwei");
        var third = write(CommentEntityType.KB, file, "drei");
        execute("UPDATE comment SET created_at = '2027-01-01T10:00:00Z' WHERE kb_file_id = %d;".formatted(file));

        var listed = commentRepo.findByTarget(CommentEntityType.KB, file);

        assertEquals(
                List.of(first.id(), second.id(), third.id()),
                listed.stream().map(Comment::id).toList());
        assertTrue(commentRepo.findByTarget(CommentEntityType.KB, -1).isEmpty());
    }

    @Test
    void anOccurrenceListingKeepsWholeAppointmentAndDatedCommentsApart() {
        int event = event();
        var whole = write(CommentEntityType.EVENT, event, "ganz");
        var june = commentRepo.create(CommentEntityType.EVENT, event, LocalDate.of(2027, 6, 1), null, author, "Juni");
        commentRepo.create(CommentEntityType.EVENT, event, LocalDate.of(2027, 7, 1), null, author, "Juli");

        assertEquals(
                List.of(whole.id()),
                commentRepo.findByEventOccurrence(event, null).stream()
                        .map(Comment::id)
                        .toList());
        var dated = commentRepo.findByEventOccurrence(event, LocalDate.of(2027, 6, 1));
        assertEquals(List.of(june.id()), dated.stream().map(Comment::id).toList());
        assertEquals(LocalDate.of(2027, 6, 1), dated.getFirst().eventDate());
        assertEquals(3, commentRepo.findByTarget(CommentEntityType.EVENT, event).size());
    }

    @Test
    void aListingFromOneStationLeavesTheOthersOut() {
        int news = news();
        write(CommentEntityType.NEWS, news, "von hier");
        var stranger = new MemberIdentity(UUID.randomUUID(), UUID.randomUUID());
        commentRepo.create(CommentEntityType.NEWS, news, null, null, stranger, "von dort");

        var ours = commentRepo.findByTargetFrom(CommentEntityType.NEWS, news, station.uid());
        var theirs = commentRepo.findByTargetFrom(CommentEntityType.NEWS, news, stranger.stationUid());

        assertEquals(List.of("von hier"), ours.stream().map(Comment::content).toList());
        assertEquals(List.of("von dort"), theirs.stream().map(Comment::content).toList());
    }

    @Test
    void anEditIsRecorded() {
        var comment = write(CommentEntityType.BOARD_TICKET, ticket(), "alt");

        assertTrue(commentRepo.update(CommentEntityType.BOARD_TICKET, comment.id(), "neu"));

        var edited = commentRepo
                .findById(CommentEntityType.BOARD_TICKET, comment.id())
                .orElseThrow();
        assertEquals("neu", edited.content());
        assertNotNull(edited.updatedAt());
    }

    @Test
    void aCommentWithRepliesStaysAsAPlaceholderAndGoesOnceItHasNone() {
        int news = news();
        var parent = write(CommentEntityType.NEWS, news, "Eltern");
        var reply = commentRepo.create(CommentEntityType.NEWS, news, null, parent.id(), author, "Antwort");

        assertTrue(commentRepo.hasChildren(parent.id()));
        assertFalse(commentRepo.hasChildren(reply.id()));
        assertEquals(2, commentRepo.count(CommentEntityType.NEWS, news));

        assertTrue(commentRepo.delete(CommentEntityType.NEWS, parent.id()));
        var placeholder =
                commentRepo.findById(CommentEntityType.NEWS, parent.id()).orElseThrow();
        assertTrue(placeholder.deleted());
        assertEquals("", placeholder.content());
        assertEquals(2, commentRepo.count(CommentEntityType.NEWS, news));

        assertTrue(commentRepo.delete(CommentEntityType.NEWS, reply.id()));
        assertTrue(commentRepo.delete(CommentEntityType.NEWS, parent.id()));
        assertEquals(0, commentRepo.count(CommentEntityType.NEWS, news));
        assertFalse(commentRepo.delete(CommentEntityType.NEWS, parent.id()));
    }

    @Test
    void aReplyOutlivesItsParentWithoutOne() throws SQLException {
        int file = file();
        var parent = write(CommentEntityType.KB, file, "Eltern");
        var reply = commentRepo.create(CommentEntityType.KB, file, null, parent.id(), author, "Antwort");

        execute("DELETE FROM comment WHERE id = %d;".formatted(parent.id()));

        assertNull(commentRepo
                .findById(CommentEntityType.KB, reply.id())
                .orElseThrow()
                .parentId());
    }

    @Test
    void everyTargetTakesItsCommentsWithIt() {
        int event = event();
        int news = news();
        int file = file();
        int ticket = ticket();
        var onEvent = write(CommentEntityType.EVENT, event, "a");
        var onNews = write(CommentEntityType.NEWS, news, "b");
        var onFile = write(CommentEntityType.KB, file, "c");
        var onTicket = write(CommentEntityType.BOARD_TICKET, ticket, "d");

        eventRepo.delete(event);
        newsRepo.delete(news);
        knowledgeBaseRepo.purgeFile(file);
        query("DELETE FROM board_ticket WHERE id = :id;")
                .single(call().bind("id", ticket))
                .delete();

        assertTrue(commentRepo.findById(CommentEntityType.EVENT, onEvent.id()).isEmpty());
        assertTrue(commentRepo.findById(CommentEntityType.NEWS, onNews.id()).isEmpty());
        assertTrue(commentRepo.findById(CommentEntityType.KB, onFile.id()).isEmpty());
        assertTrue(commentRepo
                .findById(CommentEntityType.BOARD_TICKET, onTicket.id())
                .isEmpty());
    }

    @Test
    void aCommentNamesExactlyOneTarget() {
        int event = event();
        int news = news();

        assertThrows(
                SQLException.class,
                () -> execute(
                        "INSERT INTO comment (station_id, content) VALUES (%d, 'nichts');".formatted(station.id())));
        assertThrows(
                SQLException.class,
                () -> execute(
                        "INSERT INTO comment (station_id, event_id, news_id, content) VALUES (%d, %d, %d, 'beides');"
                                .formatted(station.id(), event, news)));
        assertThrows(
                SQLException.class,
                () -> execute(
                        "INSERT INTO comment (station_id, news_id, event_date, content) VALUES (%d, %d, '2027-01-01', 'datiert');"
                                .formatted(station.id(), news)));
        assertThrows(
                SQLException.class,
                () -> execute("INSERT INTO comment (event_id, content) VALUES (%d, 'ohne Wache');".formatted(event)));
    }

    @Test
    void anAppointmentCommentNamesItsAppointment() {
        int event = event();
        var comment = write(CommentEntityType.EVENT, event, "Wo?");

        var commented = commentRepo.findCommentedEvent(comment.id()).orElseThrow();

        assertEquals(event, commented.id());
        assertEquals("Termin", commented.name());
        assertTrue(commentRepo.findCommentedEvent(-1).isEmpty());
    }
}

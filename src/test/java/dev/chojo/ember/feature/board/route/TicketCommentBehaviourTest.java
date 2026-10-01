/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.BoardActivityType;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardAttachmentService;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * What a comment on a board ticket does today, pinned over the real routes, service and storage:
 * who reads, writes, changes and removes it, how the activity lists it and whom it tells.
 */
class TicketCommentBehaviourTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station station;
    private static Account writerAccount;
    private static Account otherAccount;
    private static Account readerAccount;
    private static StationMember writer;
    private static StationMember other;
    private static StationMember reader;
    private static BoardTicketService tickets;
    private static int boardId;
    private static int laneId;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Ticket Comment Behaviour");
        writerAccount = accountRepo.create("ticket-comment-writer@test.com", "Wanda", "Writer");
        otherAccount = accountRepo.create("ticket-comment-other@test.com", "Otto", "Other");
        readerAccount = accountRepo.create("ticket-comment-reader@test.com", "Rita", "Reader");
        writer = stationMemberRepo.create(station.id(), writerAccount.id());
        other = stationMemberRepo.create(station.id(), otherAccount.id());
        reader = stationMemberRepo.create(station.id(), readerAccount.id());

        var memberService = newStationMemberService(null, null);
        var boards = new BoardService(
                boardRepo,
                memberService,
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo),
                new UserTagService(userTagRepo, memberGroupRepo));
        var backend = new LocalStorageBackend();
        tickets = new BoardTicketService(
                boardTicketRepo,
                commentRepo,
                boardRepo,
                boards,
                BUS,
                memberService,
                memberIdentityFactory,
                memberNameResolver,
                new BoardAttachmentService(
                        new StorageService(new StorageBackendResolver(backend), backend), stationRepo, backend),
                new CommentMentions(memberLookupService, BUS));

        var board = boards.createWithPreset(station.id(), "Ticket Comments", "", "TCB", LanePreset.SIMPLE);
        boardId = board.id();
        laneId = boards.findLanes(boardId).getFirst().id();
        var crew = memberGroupRepo.create(station.id(), "Ticket Comment Crew");
        memberGroupRepo.addMember(crew.id(), writer.id());
        memberGroupRepo.addMember(crew.id(), other.id());
        boards.setEditAccess(boardId, List.of(), List.of(crew.id()), List.of());

        harness = RouteHarness.serving(new BoardTicketDetailRoutes(
                        tickets,
                        boards,
                        memberNameResolver,
                        new BoardRouteGuards(boards, tickets, memberIdentityFactory)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(writerAccount.id());
        accountRepo.delete(otherAccount.id());
        accountRepo.delete(readerAccount.id());
    }

    @BeforeEach
    void forgetEvents() {
        reset(BUS);
    }

    private static int ticket() {
        return tickets.createTicket(
                        boardId,
                        laneId,
                        "Aufgabe",
                        "",
                        null,
                        TicketPriority.MEDIUM,
                        null,
                        memberIdentityFactory.local(station.id(), writer.id()))
                .ticketNumber();
    }

    private static int ticketId(int number) {
        return tickets.findByBoardAndNumber(boardId, number).orElseThrow().id();
    }

    private static String comments(int number) {
        return PREFIX + "/boards/TCB/tickets/%d/comments".formatted(number);
    }

    private static UserSession as(StationMember member) {
        return signedIn(member, StationPermission.BOARD_USE);
    }

    private static Response post(StationMember member, int number, String requestBody) {
        return harness.request(client -> client.post(comments(number), body(requestBody), harness.as(as(member))));
    }

    private static int write(StationMember member, int number, String content) {
        return json(post(member, number, "{\"content\": \"%s\"}".formatted(content)))
                .path("id")
                .asInt();
    }

    private static Response change(StationMember member, int number, int commentId, String content) {
        return harness.request(client -> client.put(
                comments(number) + "/" + commentId,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(as(member))));
    }

    private static Response remove(StationMember member, int number, int commentId) {
        return harness.request(
                client -> client.delete(comments(number) + "/" + commentId, null, harness.as(as(member))));
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void aReaderOfTheBoardReadsButDoesNotWrite() {
        int number = ticket();
        write(writer, number, "Hallo");

        var listed = json(harness.request(client -> client.get(comments(number), harness.as(as(reader)))));

        assertEquals(1, listed.size());
        assertEquals(Refusal.BOARD_NOT_YOURS_TO_EDIT, refusalOf(post(reader, number, "{\"content\": \"x\"}")));
    }

    @Test
    void anyoneWhoMayEditTheTicketChangesAndRemovesAnyComment() {
        int number = ticket();
        int id = write(writer, number, "von Wanda");
        int second = write(writer, number, "auch von Wanda");

        assertEquals(200, change(other, number, id, "von Otto umgeschrieben").code());
        assertEquals(
                "von Otto umgeschrieben",
                tickets.findComments(ticketId(number)).getFirst().content());
        assertEquals(204, remove(other, number, second).code());
        assertEquals(Refusal.BOARD_NOT_YOURS_TO_EDIT, refusalOf(remove(reader, number, id)));
        assertTrue(published().stream()
                .anyMatch(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == second
                        && deleted.entityType() == CommentEntityType.BOARD_TICKET));
    }

    @Test
    void anEmptyChangeIsSaved() {
        int number = ticket();
        int id = write(writer, number, "alt");

        assertEquals(200, change(writer, number, id, "").code());
        assertEquals("", tickets.findComments(ticketId(number)).getFirst().content());
    }

    @Test
    void aCommentOfAnotherTicketIsNotHere() {
        int first = ticket();
        int second = ticket();
        int id = write(writer, first, "hier");

        assertEquals(Refusal.TICKET_COMMENT_NOT_HERE, refusalOf(remove(writer, second, id)));
    }

    @Test
    void aReplyMayNameAParentOnAnotherTicket() {
        int parent = write(writer, ticket(), "anderswo");

        var reply = json(post(other, ticket(), "{\"content\": \"quer\", \"parentId\": %d}".formatted(parent)));

        assertEquals(parent, reply.path("parentId").asInt());
    }

    @Test
    void theActivityLeavesRemovedCommentsOutAndTheListingKeepsTheirPlaceholders() {
        int number = ticket();
        int parent = write(writer, number, "Eltern");
        json(post(other, number, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));

        remove(writer, number, parent);

        var listed = json(harness.request(client -> client.get(comments(number), harness.as(as(writer)))));
        var activity = tickets.findActivity(ticketId(number)).stream()
                .filter(entry -> entry.type() == BoardActivityType.COMMENT)
                .toList();
        assertEquals(2, listed.size());
        assertTrue(listed.get(0).path("deleted").asBoolean());
        assertEquals(1, activity.size());
    }

    @Test
    void aCommentTellsTheWatchersAndNoParentAuthor() {
        int number = ticket();
        tickets.watchTicket(ticketId(number), writer.id());
        int parent = write(other, number, "Frage");
        reset(BUS);

        write(writer, number, "Antwort ohne Bezug");
        json(post(writer, number, "{\"content\": \"Antwort\", \"parentId\": %d}".formatted(parent)));

        var events = published();
        assertTrue(events.stream().noneMatch(CommentCreated.class::isInstance));
        var changes = events.stream()
                .filter(BoardTicketChanged.class::isInstance)
                .map(BoardTicketChanged.class::cast)
                .toList();
        assertEquals(2, changes.size());
        assertEquals("Neuer Kommentar", changes.getFirst().changeDescription());
        assertEquals(List.of(writer.id()), changes.getFirst().watcherMemberIds());
    }

    @Test
    void aMentionNamesTheTicketKeyAsAuthorAndCutsWithAnEllipsis() {
        int number = ticket();
        var mentioned = stationMemberRepo.findById(other.id()).orElseThrow();

        write(writer, number, "@[%s/%s:Otto] %s".formatted(station.uid(), mentioned.uid(), "m".repeat(120)));

        var mention = published().stream()
                .filter(MentionedInComment.class::isInstance)
                .map(MentionedInComment.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("TCB-" + number, mention.authorName());
        assertTrue(mention.preview().endsWith("…"));
        assertEquals(101, mention.preview().length());
    }

    @Test
    void theListingIsOldestFirst() {
        int number = ticket();
        write(writer, number, "eins");
        write(other, number, "zwei");
        write(writer, number, "drei");

        var listed = json(harness.request(client -> client.get(comments(number), harness.as(as(writer)))));

        var texts = new ArrayList<String>();
        listed.forEach(comment -> texts.add(comment.path("content").asString()));
        assertEquals(List.of("eins", "zwei", "drei"), texts);
    }
}

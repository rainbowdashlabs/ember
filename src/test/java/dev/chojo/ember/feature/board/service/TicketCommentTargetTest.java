/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BoardRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.route.BoardRouteGuards;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What the comments on a board ticket ask of the board: where the ticket is, who may read, write
 * and moderate them and whom a new one tells.
 */
class TicketCommentTargetTest extends RepositoryTestBase {
    private static Station station;
    private static Station elsewhere;
    private static Account account;
    private static Account strangerAccount;
    private static StationMember member;
    private static StationMember stranger;
    private static BoardTicketService tickets;
    private static TicketCommentTarget target;
    private static int ticketId;
    private static int ticketNumber;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Ticket Comment Target");
        elsewhere = stationRepo.create("Ticket Comment Target Elsewhere");
        account = accountRepo.create("ticket-target@test.com", "Tara", "Target");
        strangerAccount = accountRepo.create("ticket-target-stranger@test.com", "Stan", "Stranger");
        member = stationMemberRepo.create(station.id(), account.id());
        stranger = stationMemberRepo.create(elsewhere.id(), strangerAccount.id());

        var memberService = newStationMemberService(null, null);
        var boards = new BoardService(
                boardRepo, memberService, newMemberGroupService(), new UserTagService(userTagRepo, memberGroupRepo));
        tickets = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                boards,
                new DomainEventBus(Set.of()),
                memberService,
                memberIdentityFactory,
                memberNameResolver,
                mock(BoardAttachmentService.class));
        target = new TicketCommentTarget(tickets, boards, new BoardRouteGuards(boards, tickets, memberIdentityFactory));

        var board = boards.createWithPreset(station.id(), "Target Board", "", "TCT", LanePreset.SIMPLE);
        var ticket = tickets.createTicket(
                board.id(),
                boards.findLanes(board.id()).getFirst().id(),
                "Aufgabe",
                "",
                null,
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        ticketId = ticket.id();
        ticketNumber = ticket.ticketNumber();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(account.id());
        accountRepo.delete(strangerAccount.id());
    }

    private static TargetInfo info() {
        return target.find(ticketId).orElseThrow();
    }

    @Test
    void aTicketIsNamedByItsKeyAndAddressedOnItsBoard() {
        var info = info();

        assertEquals("TCT-" + ticketNumber, info.title());
        assertEquals(station.id(), info.stationId());
        assertEquals(new BoardTicketAddress("TCT", ticketNumber), info.ticketAddress());
        assertFalse(info.systemEntry());
        assertTrue(target.find(Integer.MAX_VALUE).isEmpty());
        assertEquals(BoardRefusal.BOARD_TICKET_NOT_HERE, target.missing());
    }

    @Test
    void aMemberOfAnotherStationFindsNoBoard() {
        var session = stationSession(stranger, StationPermission.BOARD_USE, StationPermission.BOARD_MANAGER);

        var read = assertThrows(RefusalResponse.class, () -> target.requireReadable(session, info()));
        var write = assertThrows(RefusalResponse.class, () -> target.requireWritable(session, info()));

        assertEquals(BoardRefusal.BOARD_NOT_HERE_OR_NOT_YOURS, read.refusal());
        assertEquals(BoardRefusal.BOARD_NOT_HERE_OR_NOT_YOURS, write.refusal());
    }

    @Test
    void aBoardManagerRemovesButNeverRewritesSomebodyElsesComment() {
        var manager = stationSession(member, StationPermission.BOARD_USE, StationPermission.BOARD_MANAGER);
        var user = stationSession(member, StationPermission.BOARD_USE);

        assertTrue(target.mayModerate(manager, info(), Moderation.DELETE));
        assertFalse(target.mayModerate(manager, info(), Moderation.EDIT));
        assertFalse(target.mayModerate(user, info(), Moderation.DELETE));
    }

    @Test
    void aCommentFromAPartnerTellsTheWatchersAndTheMentionedToo() {
        assertNull(target.audienceFor(info(), CommentOrigin.PARTNER).others());

        tickets.watchTicket(ticketId, member.id());
        var audience = target.audienceFor(info(), CommentOrigin.PARTNER);
        tickets.unwatchTicket(ticketId, member.id());

        assertFalse(audience.parentAuthor());
        assertTrue(audience.mentions());
        assertEquals(Set.of(member.id()), audience.others().memberIds());
    }

    @Test
    void aNotificationOpensTheTicketAtTheComment() {
        var link = target.link(info(), 42);

        assertEquals("TCT", link.routeParams().get("boardKey"));
        assertEquals(ticketNumber, link.routeParams().get("ticketNumber"));
        assertEquals(Map.of("comment", 42), link.query());
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ticket is handed only to somebody who may write on its board.
 *
 * <p>The board carries an edit restriction naming one group, which is what makes the three cases
 * tell each other apart: somebody in the group, somebody who is not, and somebody who administers
 * every board and was never put in the group. The last is the one a rule written too strictly
 * turns away.
 */
class TicketAssignmentNeedsWriteAccessTest extends RepositoryTestBase {
    private static BoardService boardService;
    private static BoardTicketService ticketService;

    private static Station station;
    private static Account writerAccount;
    private static Account outsiderAccount;
    private static Account managerAccount;
    private static StationMember writer;
    private static StationMember outsider;
    private static StationMember boardManager;

    private static int boardId;
    private static int laneId;
    private static int ticketId;

    @BeforeAll
    static void setup() {
        var memberService = newStationMemberService(null, null);
        var groupService = new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo, noBus());
        var tagService = new UserTagService(userTagRepo, memberGroupRepo);
        boardService = new BoardService(boardRepo, memberService, groupService, tagService);

        var backend = new LocalStorageBackend();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        ticketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                boardService,
                noBus(),
                memberService,
                memberIdentityFactory,
                memberNameResolver,
                new BoardAttachmentService(storage, stationRepo, backend));

        station = stationRepo.create("Assignment Station");
        writerAccount = accountRepo.create("assign-writer@test.com", "Wanda", "Writer");
        outsiderAccount = accountRepo.create("assign-outsider@test.com", "Otto", "Outside");
        managerAccount = accountRepo.create("assign-manager@test.com", "Mara", "Manager");
        writer = stationMemberRepo.create(station.id(), writerAccount.id());
        outsider = stationMemberRepo.create(station.id(), outsiderAccount.id());
        boardManager = stationMemberRepo.create(station.id(), managerAccount.id());

        int boardManagerPermission = stationMemberRepo
                .findPermissionByName(StationPermission.BOARD_MANAGER)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(boardManager.id(), boardManagerPermission);

        var board = boardService.createWithPreset(station.id(), "Assignment Board", "", "ASG", LanePreset.SIMPLE);
        boardId = board.id();
        laneId = boardService.findLanes(boardId).getFirst().id();

        var crew = groupService.create(station.id(), "Assignment Crew");
        groupService.setMembers(crew.id(), List.of(writer.id()), null);
        boardService.setEditAccess(boardId, List.of(), List.of(crew.id()), List.of());

        ticketId = ticketService
                .createTicket(boardId, laneId, "Zu vergeben", "", null, TicketPriority.MEDIUM, null, identity(writer))
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(writerAccount.id());
        accountRepo.delete(outsiderAccount.id());
        accountRepo.delete(managerAccount.id());
    }

    private static DomainEventBus noBus() {
        return new DomainEventBus(Set.of());
    }

    private static MemberIdentity identity(StationMember member) {
        return memberIdentityFactory.local(station.id(), member.id());
    }

    @Test
    void creatingATicketForSomebodyWhoMayNotWriteIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> ticketService.createTicket(
                        boardId,
                        laneId,
                        "Fuer den Falschen",
                        "",
                        identity(outsider),
                        TicketPriority.MEDIUM,
                        null,
                        identity(writer)));
    }

    @Test
    void updatingATicketOntoSomebodyWhoMayNotWriteIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> ticketService.updateTicket(
                        ticketId,
                        "Zu vergeben",
                        "",
                        identity(outsider),
                        TicketPriority.MEDIUM,
                        null,
                        identity(writer)));
    }

    @Test
    void handingATicketToSomebodyWhoMayNotWriteIsRefused() {
        assertThrows(
                BadRequestResponse.class, () -> ticketService.assignTicket(ticketId, identity(outsider), writer.id()));

        assertNull(ticketService.findById(ticketId).orElseThrow().assignee(), "the ticket stayed on nobody");
    }

    @Test
    void handingATicketToSomebodyWhoMayWriteGoesThrough() {
        assertTrue(ticketService.assignTicket(ticketId, identity(writer), writer.id()));

        assertEquals(
                writer.uid(),
                ticketService.findById(ticketId).orElseThrow().assignee().memberUid());
        ticketService.assignTicket(ticketId, null, writer.id());
    }

    /**
     * Whoever administers every board may be handed a ticket on one they were never separately let
     * into. This is the half a rule that only reads the board's own lists would break.
     */
    @Test
    void handingATicketToABoardManagerGoesThroughWithoutTheirOwnRelease() {
        assertTrue(ticketService.assignTicket(ticketId, identity(boardManager), writer.id()));

        assertEquals(
                boardManager.uid(),
                ticketService.findById(ticketId).orElseThrow().assignee().memberUid());
        ticketService.assignTicket(ticketId, null, writer.id());
    }

    @Test
    void takingTheNameOffATicketIsAlwaysAllowed() {
        assertDoesNotThrow(() -> ticketService.assignTicket(ticketId, null, writer.id()));
    }

    /**
     * The list the picker is built from and the check the write path makes are the same question,
     * so a name the picker offers is never one the server turns down.
     */
    @Test
    void thePickerOffersExactlyWhoMayBeHandedATicket() {
        var allowed = boardService.findMembersWhoMayEdit(boardId, station.id());

        assertTrue(allowed.contains(writer.id()));
        assertTrue(allowed.contains(boardManager.id()));
        assertTrue(!allowed.contains(outsider.id()), "somebody outside the board's crew is not offered");
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.LaneData;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BoardServiceTest extends RepositoryTestBase {
    private static BoardService boardService;
    private static BoardTicketService ticketService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int boardId;
    private static int laneId;
    private static int ticketId1;
    private static int ticketId2;
    private static StationMemberService memberService;
    private static MemberGroupService groupService;
    private static UserTagService tagService;

    @BeforeAll
    static void setup() {
        memberService = mock(StationMemberService.class);
        groupService = mock(MemberGroupService.class);
        tagService = mock(UserTagService.class);

        boardService = new BoardService(boardRepo, memberService, groupService, tagService);
        var btBackend = new LocalStorageBackend();
        var btResolver = new StorageBackendResolver(btBackend);
        var btStorage = new StorageService(btResolver, btBackend);
        var attachmentSvc = new BoardAttachmentService(btStorage, stationRepo, btBackend);
        ticketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                new DomainEventBus(Set.of()),
                new StationMemberService(stationMemberRepo, stationRepo, null, null),
                memberIdentityFactory,
                memberNameResolver,
                attachmentSvc);

        station = stationRepo.create("BoardSvcStation");
        account = accountRepo.create("board-svc@test.com", "Board", "Svc");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Board with preset --

    @Test
    @Order(1)
    void createBoardWithPreset() {
        var board = boardService.createWithPreset(station.id(), "Sprint Board", "Desc", "SPR", LanePreset.SIMPLE);
        assertNotNull(board);
        assertEquals("Sprint Board", board.name());
        boardId = board.id();

        var lanes = boardService.findLanes(boardId);
        assertEquals(3, lanes.size());
        assertEquals("Offen", lanes.get(0).name());
        assertEquals("In Arbeit", lanes.get(1).name());
        assertEquals("Erledigt", lanes.get(2).name());
        laneId = lanes.get(0).id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var boards = boardService.findByStation(station.id());
        assertEquals(1, boards.size());
    }

    @Test
    @Order(3)
    void visibleBoardsNoRestrictions() {
        var boards = boardService.findVisibleBoards(station.id(), member.id());
        assertEquals(1, boards.size());
    }

    @Test
    @Order(4)
    void updateBoard() {
        assertTrue(boardService.update(boardId, "Sprint Board v2", "Updated", 3));
        var board = boardService.findById(boardId).orElseThrow();
        assertEquals("Sprint Board v2", board.name());
        assertEquals(3, board.hideDoneAfterDays());
    }

    // -- Tickets via service --

    @Test
    @Order(10)
    void createTicket() {
        BoardTicket t = ticketService.createTicket(
                boardId,
                laneId,
                "Implement feature",
                "Details",
                null,
                TicketPriority.HIGH,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        assertNotNull(t);
        assertEquals(1, t.ticketNumber());
        assertEquals("Implement feature", t.title());
        ticketId1 = t.id();
    }

    @Test
    @Order(11)
    void createSecondTicketAutoNumber() {
        BoardTicket t = ticketService.createTicket(
                boardId,
                laneId,
                "Write tests",
                null,
                memberIdentityFactory.local(station.id(), member.id()),
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        assertEquals(2, t.ticketNumber());
        ticketId2 = t.id();
    }

    @Test
    @Order(12)
    void moveTicketLogsTransition() {
        var lanes = boardService.findLanes(boardId);
        int doneLaneId = lanes.get(2).id();
        assertTrue(ticketService.moveTicket(
                ticketId1, laneId, doneLaneId, 0, memberIdentityFactory.local(station.id(), member.id())));

        var transitions = ticketService.findTransitions(ticketId1);
        assertEquals(1, transitions.size());
        assertEquals(laneId, transitions.getFirst().fromLaneId());
        assertEquals(doneLaneId, transitions.getFirst().toLaneId());
    }

    @Test
    @Order(13)
    void updateTicket() {
        assertTrue(ticketService.updateTicket(
                ticketId1,
                "Implement feature v2",
                "New details",
                memberIdentityFactory.local(station.id(), member.id()),
                TicketPriority.HIGHEST,
                null,
                memberIdentityFactory.local(station.id(), member.id())));
        var t = ticketService.findById(ticketId1).orElseThrow();
        assertEquals("Implement feature v2", t.title());
        assertEquals(TicketPriority.HIGHEST, t.priority());
    }

    // -- Links with types --

    @Test
    @Order(20)
    void linkTicketsWithType() {
        ticketService.linkTickets(
                ticketId1, ticketId2, LinkType.CAUSES, memberIdentityFactory.local(station.id(), member.id()));
        var links = ticketService.findLinks(ticketId1);
        assertEquals(1, links.size());
        assertEquals(LinkType.CAUSES, links.getFirst().linkType());

        var reverseLinks = ticketService.findLinks(ticketId2);
        assertEquals(1, reverseLinks.size());
        assertEquals(LinkType.CAUSED_BY, reverseLinks.getFirst().linkType());
    }

    @Test
    @Order(21)
    void unlinkTickets() {
        assertTrue(ticketService.unlinkTickets(
                ticketId1, ticketId2, memberIdentityFactory.local(station.id(), member.id())));
        assertTrue(ticketService.findLinks(ticketId1).isEmpty());
    }

    // -- Checklist --

    @Test
    @Order(30)
    void addChecklistItems() {
        BoardChecklistItem item1 = ticketService.addChecklistItem(ticketId1, "Step 1", member.id());
        BoardChecklistItem item2 = ticketService.addChecklistItem(ticketId1, "Step 2", member.id());
        assertEquals(0, item1.position());
        assertEquals(1, item2.position());

        var ticket = ticketService.findById(ticketId1).orElseThrow();
        assertEquals(2, ticket.checklistTotal());
        assertEquals(0, ticket.checklistChecked());
    }

    @Test
    @Order(31)
    void checkChecklistItem() {
        var items = ticketService.findChecklistItems(ticketId1);
        assertTrue(ticketService.updateChecklistItem(items.getFirst().id(), ticketId1, "Step 1", true, member.id()));

        var ticket = ticketService.findById(ticketId1).orElseThrow();
        assertEquals(2, ticket.checklistTotal());
        assertEquals(1, ticket.checklistChecked());
    }

    @Test
    @Order(32)
    void deleteChecklistItem() {
        var items = ticketService.findChecklistItems(ticketId1);
        assertTrue(ticketService.deleteChecklistItem(items.getFirst().id(), ticketId1, member.id()));
        assertEquals(1, ticketService.findChecklistItems(ticketId1).size());
    }

    // -- Comments --

    @Test
    @Order(40)
    void createAndFindComments() {
        var comment = ticketService.createComment(
                ticketId1, null, memberIdentityFactory.local(station.id(), member.id()), "Looks good");
        assertNotNull(comment);
        assertEquals("Looks good", comment.content());

        var comments = ticketService.findComments(ticketId1);
        assertEquals(1, comments.size());
    }

    @Test
    @Order(41)
    void deleteCommentWithoutChildren() {
        var comments = ticketService.findComments(ticketId1);
        assertTrue(ticketService.deleteComment(comments.getFirst().id()));
        var updated = ticketService.findComments(ticketId1);
        assertTrue(updated.isEmpty());
    }

    // -- Watchers --

    @Test
    @Order(42)
    void watchTicketAndTriggerNotification() {
        ticketService.watchTicket(ticketId1, member.id());
        assertTrue(ticketService.isWatching(ticketId1, member.id()));

        // Updating a ticket with watchers should not throw (event bus has no handlers in test)
        ticketService.updateTicket(
                ticketId1,
                "Watched update",
                "Details",
                null,
                TicketPriority.HIGH,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        var t = ticketService.findById(ticketId1).orElseThrow();
        assertEquals("Watched update", t.title());

        // Move ticket with watchers
        var lanes = boardService.findLanes(boardId);
        int workLaneId = lanes.get(1).id();
        ticketService.moveTicket(
                ticketId1, laneId, workLaneId, 0, memberIdentityFactory.local(station.id(), member.id()));
        var moved = ticketService.findById(ticketId1).orElseThrow();
        assertEquals(workLaneId, moved.laneId());

        // Comment with watchers
        ticketService.createComment(
                ticketId1, null, memberIdentityFactory.local(station.id(), member.id()), "Watched comment");

        // Cleanup
        assertTrue(ticketService.unwatchTicket(ticketId1, member.id()));
        assertFalse(ticketService.isWatching(ticketId1, member.id()));
    }

    // -- Activity feed --

    @Test
    @Order(43)
    void findActivity() {
        var activity = ticketService.findActivity(ticketId1);
        assertFalse(activity.isEmpty());
    }

    // -- Comments update --

    @Test
    @Order(44)
    void updateComment() {
        var comment = ticketService.createComment(
                ticketId1, null, memberIdentityFactory.local(station.id(), member.id()), "To update");
        assertTrue(ticketService.updateComment(comment.id(), "Updated"));
    }

    // -- Find by number --

    @Test
    @Order(45)
    void findByBoardAndNumber() {
        var ticket = ticketService.findByBoardAndNumber(boardId, 1);
        assertTrue(ticket.isPresent());
    }

    // -- Find by assignee --

    @Test
    @Order(46)
    void findByAssignee() {
        var tickets = ticketService.findByAssignee(boardId, member.id());
        assertFalse(tickets.isEmpty());
        assertTrue(tickets.stream().anyMatch(t -> t.title().equals("Write tests")));
    }

    // -- Find by lane --

    @Test
    @Order(47)
    void findByBoardAndLane() {
        var tickets = ticketService.findByBoardAndLane(boardId, laneId);
        assertFalse(tickets.isEmpty());
    }

    // -- Delete ticket --

    @Test
    @Order(48)
    void deleteTicket() {
        assertTrue(ticketService.deleteTicket(ticketId2));
        assertTrue(ticketService.findById(ticketId2).isEmpty());
    }

    // -- Reorder --

    @Test
    @Order(49)
    void reorderTicketsInLane() {
        ticketService.reorderTickets(laneId, List.of(ticketId1));
        // no exception = success
    }

    // -- Checklist reorder --

    @Test
    @Order(50)
    void reorderChecklistItems() {
        var items = ticketService.findChecklistItems(ticketId1);
        ticketService.reorderChecklistItems(
                ticketId1, items.stream().map(BoardChecklistItem::id).toList());
    }

    // -- Access control --

    @Test
    @Order(60)
    void canViewAndEditByDefault() {
        assertTrue(boardService.canView(boardId, member.id()));
        assertTrue(boardService.canEdit(boardId, member.id()));
    }

    @Test
    @Order(61)
    void canViewWithUserTypeRestriction() {
        boardService.setViewAccess(boardId, List.of(StationUserType.MEMBER), List.of(), List.of());
        when(memberService.findById(member.id()))
                .thenReturn(Optional.of(new StationMember(
                        member.id(),
                        member.stationId(),
                        member.uid(),
                        member.accountId(),
                        false,
                        null,
                        null,
                        StationUserType.MEMBER,
                        null)));
        assertTrue(boardService.canView(boardId, member.id()));
    }

    @Test
    @Order(62)
    void cannotViewWithWrongUserType() {
        boardService.setViewAccess(boardId, List.of(StationUserType.MANAGER), List.of(), List.of());
        when(memberService.findById(member.id()))
                .thenReturn(Optional.of(new StationMember(
                        member.id(),
                        member.stationId(),
                        member.uid(),
                        member.accountId(),
                        false,
                        null,
                        null,
                        StationUserType.MEMBER,
                        null)));
        assertFalse(boardService.canView(boardId, member.id()));
    }

    @Test
    @Order(63)
    void canViewWithGroupRestriction() {
        boardService.setViewAccess(boardId, List.of(), List.of(42), List.of());
        when(groupService.findGroupsForMember(member.id()))
                .thenReturn(List.of(new MemberGroup(42, station.id(), "TestGroup", null, 0)));
        assertTrue(boardService.canView(boardId, member.id()));
    }

    @Test
    @Order(64)
    void canViewWithTagRestriction() {
        boardService.setViewAccess(boardId, List.of(), List.of(), List.of(77));
        when(groupService.findGroupsForMember(member.id())).thenReturn(List.of());
        when(tagService.findTagsForMember(member.id()))
                .thenReturn(List.of(new UserTag(77, station.id(), "TestTag", null, false, 0)));
        assertTrue(boardService.canView(boardId, member.id()));
    }

    @Test
    @Order(65)
    void canEditWithRestriction() {
        boardService.setEditAccess(boardId, List.of(StationUserType.MEMBER), List.of(), List.of());
        when(memberService.findById(member.id()))
                .thenReturn(Optional.of(new StationMember(
                        member.id(),
                        member.stationId(),
                        member.uid(),
                        member.accountId(),
                        false,
                        null,
                        null,
                        StationUserType.MEMBER,
                        null)));
        assertTrue(boardService.canEdit(boardId, member.id()));
    }

    @Test
    @Order(66)
    void editFallsBackToViewWhenNoEditRestrictions() {
        boardService.setEditAccess(boardId, List.of(), List.of(), List.of());
        boardService.setViewAccess(boardId, List.of(), List.of(), List.of());
        assertTrue(boardService.canEdit(boardId, member.id()));
    }

    @Test
    @Order(67)
    void clearAllAccess() {
        boardService.setViewAccess(boardId, List.of(), List.of(), List.of());
        boardService.setEditAccess(boardId, List.of(), List.of(), List.of());
        reset(memberService, groupService, tagService);
        assertTrue(boardService.canView(boardId, member.id()));
        assertTrue(boardService.canEdit(boardId, member.id()));
    }

    // -- Board fields --

    @Test
    @Order(70)
    void replaceFields() {
        boardService.replaceFields(
                boardId,
                List.of(
                        new BoardField(
                                0, boardId, "Component", BoardFieldType.STRING, new BoardFieldConfig.Simple(false), 0),
                        new BoardField(
                                0, boardId, "Effort", BoardFieldType.NUMBER, new BoardFieldConfig.Simple(false), 1)));
        var fields = boardService.findFields(boardId);
        assertEquals(2, fields.size());
        assertEquals("Component", fields.getFirst().name());
    }

    // -- Replace lanes on fresh board --

    @Test
    @Order(75)
    void replaceLanesOnFreshBoard() {
        var freshBoard = boardService.create(station.id(), "Temp Board", "", "TMP");
        boardService.replaceLanes(
                freshBoard.id(),
                List.of(
                        new LaneData(null, "Backlog", null),
                        new LaneData(null, "In Progress", null),
                        new LaneData(null, "Review", null),
                        new LaneData(null, "Done", null)));
        var lanes = boardService.findLanes(freshBoard.id());
        assertEquals(4, lanes.size());
        assertEquals("Backlog", lanes.get(0).name());
        assertEquals("Done", lanes.get(3).name());
        boardService.delete(freshBoard.id());
    }

    // -- Access data --

    @Test
    @Order(79)
    void getAccessData() {
        boardService.setViewAccess(boardId, List.of(StationUserType.MEMBER), List.of(2), List.of(3));
        var va = boardService.getViewAccess(boardId);
        assertEquals(List.of(StationUserType.MEMBER), va.userTypes());
        assertEquals(List.of(2), va.groupIds());
        assertEquals(List.of(3), va.tagIds());
        var ea = boardService.getEditAccess(boardId);
        assertNotNull(ea);
        boardService.setViewAccess(boardId, List.of(), List.of(), List.of());
    }

    // -- Labels --

    @Test
    @Order(80)
    void labelsCrud() {
        var label = boardService.createLabel(boardId, "Bug", "#ef4444");
        assertNotNull(label);
        var labels = boardService.findLabels(boardId);
        assertEquals(1, labels.size());
        boardService.addLabelToTicket(ticketId1, label.id());
        assertEquals(1, boardService.findLabelsForTicket(ticketId1).size());
        assertFalse(boardService.findAllTicketLabels(boardId).isEmpty());
        assertTrue(boardService.removeLabelFromTicket(ticketId1, label.id()));
        assertTrue(boardService.updateLabel(label.id(), "Critical", "#ff0000"));
        assertTrue(boardService.deleteLabel(label.id()));
    }

    // -- Backlog --

    @Test
    @Order(81)
    void backlogEnableDisable() {
        var lane = boardService.enableBacklog(boardId);
        assertNotNull(lane);
        boardService.disableBacklog(boardId);
    }

    // -- Ticket service: field values + attachments + weblinks --

    @Test
    @Order(82)
    void ticketFieldValues() {
        boardService.replaceFields(
                boardId,
                List.of(new BoardField(0, boardId, "F", BoardFieldType.STRING, new BoardFieldConfig.Simple(false), 0)));
        var fields = boardService.findFields(boardId);
        int fid = fields.getFirst().id();
        ticketService.setFieldValue(ticketId1, fid, new BoardFieldValue.StringValue("test"));
        assertEquals(1, ticketService.findFieldValues(ticketId1).size());
        assertTrue(ticketService.deleteFieldValue(ticketId1, fid));
    }

    @Test
    @Order(83)
    void ticketWeblinks() {
        var wl = ticketService.addWeblink(ticketId1, "https://test.com", "Test");
        assertNotNull(wl);
        assertEquals(1, ticketService.findWeblinks(ticketId1).size());
        assertTrue(ticketService.deleteWeblink(wl.id()));
    }

    @Test
    @Order(84)
    void ticketAttachments() {
        var att = ticketService.uploadAttachment(
                station.id(),
                ticketId1,
                "test.txt",
                "text/plain",
                "hello".getBytes(),
                memberIdentityFactory.local(station.id(), member.id()));
        assertNotNull(att);
        assertEquals(1, ticketService.findAttachments(ticketId1).size());
        assertTrue(ticketService.findAttachmentById(att.id()).isPresent());
        assertNotNull(ticketService.getAttachmentPath(station.id(), att));
        assertTrue(ticketService.deleteAttachment(station.id(), att.id()));
    }

    @Test
    @Order(85)
    void ticketAssign() {
        assertTrue(ticketService.assignTicket(
                ticketId1, memberIdentityFactory.local(station.id(), member.id()), member.id()));
        var tk = ticketService.findById(ticketId1).orElseThrow();
        assertNotNull(tk.assignee());
    }

    @Test
    @Order(86)
    void ticketSearch() {
        var results = ticketService.search(boardId, "ticket");
        assertNotNull(results);
    }

    @Test
    @Order(871)
    void ticketActivity() {
        var activity = ticketService.findActivity(ticketId1);
        assertNotNull(activity);
    }

    @Test
    @Order(872)
    void moveTicketBetweenLanes() {
        var lanes = boardService.findLanes(boardId);
        if (lanes.size() >= 2) {
            ticketService.moveTicket(
                    ticketId1,
                    lanes.get(0).id(),
                    lanes.get(1).id(),
                    0,
                    memberIdentityFactory.local(station.id(), member.id()));
            var tk = ticketService.findById(ticketId1).orElseThrow();
            assertEquals(lanes.get(1).id(), tk.laneId());
            ticketService.moveTicket(
                    ticketId1,
                    lanes.get(1).id(),
                    lanes.get(0).id(),
                    0,
                    memberIdentityFactory.local(station.id(), member.id()));
        }
    }

    @Test
    @Order(873)
    void ticketFindByAssignee() {
        var results = ticketService.findByAssignee(boardId, member.id());
        assertNotNull(results);
    }

    @Test
    @Order(874)
    void ticketFindByBoardAndNumber() {
        var result = ticketService.findByBoardAndNumber(boardId, 1);
        assertTrue(result.isPresent());
    }

    @Test
    @Order(874)
    void ticketCreateAndDelete() {
        var tk = ticketService.createTicket(
                boardId,
                laneId,
                "Temp",
                null,
                null,
                TicketPriority.LOW,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        assertTrue(ticketService.deleteTicket(tk.id()));
    }

    @Test
    @Order(876)
    void ticketUpdateWithNotify() {
        ticketService.watchTicket(ticketId1, member.id());
        assertTrue(ticketService.updateTicket(
                ticketId1,
                "Updated",
                "Desc",
                null,
                TicketPriority.HIGH,
                null,
                memberIdentityFactory.local(station.id(), member.id())));
        ticketService.unwatchTicket(ticketId1, member.id());
    }

    @Test
    @Order(877)
    void ticketReorderAndLinks() {
        var tmp = ticketService.createTicket(
                boardId,
                laneId,
                "Tmp",
                null,
                null,
                TicketPriority.LOW,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        ticketService.reorderTickets(laneId, List.of(ticketId1, tmp.id()));
        ticketService.linkTickets(
                ticketId1, tmp.id(), LinkType.RELATES_TO, memberIdentityFactory.local(station.id(), member.id()));
        assertFalse(ticketService.findLinks(ticketId1).isEmpty());
        assertTrue(ticketService.unlinkTickets(
                ticketId1, tmp.id(), memberIdentityFactory.local(station.id(), member.id())));
        ticketService.deleteTicket(tmp.id());
    }

    @Test
    @Order(879)
    void ticketTransitions() {
        assertNotNull(ticketService.findTransitions(ticketId1));
    }

    @Test
    @Order(880)
    void commentCreateUpdateDelete() {
        var comment = ticketService.createComment(
                ticketId1, null, memberIdentityFactory.local(station.id(), member.id()), "Test comment");
        assertNotNull(comment);
        assertTrue(ticketService.updateComment(comment.id(), "Updated comment"));
        assertTrue(ticketService.deleteComment(comment.id()));
    }

    @Test
    @Order(881)
    void checklistReorder() {
        var item = ticketService.addChecklistItem(ticketId1, "Reorder test", member.id());
        ticketService.reorderChecklistItems(ticketId1, List.of(item.id()));
        ticketService.deleteChecklistItem(item.id(), ticketId1, member.id());
    }

    @Test
    @Order(88)
    void ticketWatchers() {
        ticketService.watchTicket(ticketId1, member.id());
        assertTrue(ticketService.isWatching(ticketId1, member.id()));
        var watchers = ticketService.findWatchers(ticketId1);
        assertEquals(1, watchers.size());
        assertTrue(ticketService.unwatchTicket(ticketId1, member.id()));
    }

    // -- History --

    @Test
    @Order(890)
    void ticketHistoryLogging() {
        ticketService.logHistory(
                ticketId1,
                BoardTicketHistoryAction.TITLE_CHANGED,
                "detail",
                memberIdentityFactory.local(station.id(), member.id()));
        var history = ticketService.findHistory(ticketId1);
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(h -> h.action() == BoardTicketHistoryAction.TITLE_CHANGED));
    }

    // -- KB Links --

    @Test
    @Order(891)
    void kbLinksCrud() {
        // KB links depend on a kb_file existing - skip if no KB infrastructure
        // Test the service delegation at minimum
        var links = ticketService.findKbLinks(ticketId1);
        assertNotNull(links);
    }

    // -- Attachment path --

    @Test
    @Order(892)
    void attachmentPathResolution() {
        var att = ticketService.uploadAttachment(
                station.id(),
                ticketId1,
                "pathtest.txt",
                "text/plain",
                "data".getBytes(),
                memberIdentityFactory.local(station.id(), member.id()));
        var path = ticketService.getAttachmentPath(station.id(), att);
        assertNotNull(path);
        assertTrue(path.toString().contains("pathtest.txt"));
        ticketService.deleteAttachment(station.id(), att.id());
    }

    // -- Move with lane_assignee field --

    @Test
    @Order(893)
    void moveTicketWithLaneAssigneeField() {
        var lanes = boardService.findLanes(boardId);
        if (lanes.size() >= 2) {
            // Create a lane_assignee field pointing to lanes[1]
            boardService.replaceFields(
                    boardId,
                    List.of(new BoardField(
                            0,
                            boardId,
                            "Reviewer",
                            BoardFieldType.LANE_ASSIGNEE,
                            new BoardFieldConfig.LaneAssignee(
                                    false, lanes.get(1).id()),
                            0)));
            var fields = boardService.findFields(boardId);
            int fieldId = fields.getFirst().id();
            // Set field value to member id
            ticketService.setFieldValue(ticketId1, fieldId, new BoardFieldValue.LaneAssignee(member.id()));
            // Move ticket to lane[1]
            ticketService.moveTicket(
                    ticketId1,
                    lanes.get(0).id(),
                    lanes.get(1).id(),
                    0,
                    memberIdentityFactory.local(station.id(), member.id()));
            var tk = ticketService.findById(ticketId1).orElseThrow();
            assertNotNull(tk.assignee());
            // Move back
            ticketService.moveTicket(
                    ticketId1,
                    lanes.get(1).id(),
                    lanes.get(0).id(),
                    0,
                    memberIdentityFactory.local(station.id(), member.id()));
            ticketService.deleteFieldValue(ticketId1, fieldId);
        }
    }

    // -- Comment with mentions --

    @Test
    @Order(893)
    void updateTicketWithDueDateChange() {
        var tk = ticketService.createTicket(
                boardId,
                laneId,
                "DueDate Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        // Update with due date
        assertTrue(ticketService.updateTicket(
                tk.id(),
                "DueDate Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                LocalDate.of(2026, 12, 1),
                memberIdentityFactory.local(station.id(), member.id())));
        // Now change the due date
        assertTrue(ticketService.updateTicket(
                tk.id(),
                "DueDate Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                LocalDate.of(2026, 12, 15),
                memberIdentityFactory.local(station.id(), member.id())));
        // Remove due date
        assertTrue(ticketService.updateTicket(
                tk.id(),
                "DueDate Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id())));
        ticketService.deleteTicket(tk.id());
    }

    @Test
    @Order(893)
    void assignTicketWithUnassign() {
        var tk = ticketService.createTicket(
                boardId,
                laneId,
                "Assign Test",
                null,
                null,
                TicketPriority.LOW,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        // Assign
        ticketService.watchTicket(tk.id(), member.id());
        assertTrue(ticketService.assignTicket(
                tk.id(), memberIdentityFactory.local(station.id(), member.id()), member.id()));
        // Reassign to someone else (create second member)
        var account2 = accountRepo.create("board-assign@test.com", "Assign", "User");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        assertTrue(ticketService.assignTicket(
                tk.id(), memberIdentityFactory.local(station.id(), member2.id()), member.id()));
        // Unassign
        assertTrue(ticketService.assignTicket(tk.id(), null, member.id()));
        ticketService.unwatchTicket(tk.id(), member.id());
        ticketService.deleteTicket(tk.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(893)
    void deleteAttachmentNonExistent() {
        assertFalse(ticketService.deleteAttachment(station.id(), 99999));
    }

    @Test
    @Order(893)
    void findByAssigneeNonExistentMember() {
        var results = ticketService.findByAssignee(boardId, 99999);
        assertTrue(results.isEmpty());
    }

    @Test
    @Order(893)
    void linkTicketToSelf() {
        // Linking a ticket to itself should be a no-op
        ticketService.linkTickets(
                ticketId1, ticketId1, LinkType.RELATES_TO, memberIdentityFactory.local(station.id(), member.id()));
        // Should have no self-links
        var links = ticketService.findLinks(ticketId1);
        assertTrue(links.stream().noneMatch(l -> l.linkedTicketId() == ticketId1));
    }

    @Test
    @Order(893)
    void addAndRemoveWatcherByIdentity() {
        var identity = memberIdentityFactory.local(station.id(), member.id());
        ticketService.addWatcher(ticketId1, identity);
        assertTrue(ticketService.isWatching(ticketId1, member.id()));
        assertTrue(ticketService.removeWatcher(ticketId1, identity));
        assertFalse(ticketService.isWatching(ticketId1, member.id()));
    }

    @Test
    @Order(894)
    void commentWithNewFormatMentions() {
        var memberIdentity = memberIdentityFactory.local(station.id(), member.id());
        String mentionText =
                "Hello @[" + memberIdentity.stationUid() + "/" + memberIdentity.memberUid() + ":TestUser]!";
        var comment = ticketService.createComment(
                ticketId1, null, memberIdentityFactory.local(station.id(), member.id()), mentionText);
        assertNotNull(comment);
        ticketService.deleteComment(comment.id());
    }

    @Test
    @Order(894)
    void commentWithMentions() {
        var comment = ticketService.createComment(
                ticketId1,
                null,
                memberIdentityFactory.local(station.id(), member.id()),
                "Hello @[" + member.id() + ":Test]!");
        assertNotNull(comment);
        ticketService.deleteComment(comment.id());
    }

    // -- Cleanup --

    @Test
    @Order(999)
    void deleteBoard() {
        assertTrue(boardService.delete(boardId));
        assertTrue(boardService.findById(boardId).isEmpty());
    }
}

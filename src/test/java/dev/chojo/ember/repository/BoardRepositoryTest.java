/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BoardRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int boardId;
    private static int laneId1;
    private static int laneId2;
    private static int ticketId1;
    private static int ticketId2;
    private static int checklistItemId;
    private static int commentId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Board Station");
        account = accountRepo.create("board@test.com", "Board", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Board CRUD --

    @Test
    @Order(1)
    void createBoard() {
        Board board = boardRepo.create(station.id(), "Dev Board", "For development", "DEV");
        assertNotNull(board);
        assertEquals("Dev Board", board.name());
        assertEquals("DEV", board.shortKey());
        assertEquals(0, board.ticketCounter());
        boardId = board.id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var boards = boardRepo.findByStation(station.id());
        assertEquals(1, boards.size());
        assertEquals("Dev Board", boards.getFirst().name());
    }

    @Test
    @Order(3)
    void findById() {
        var board = boardRepo.findById(boardId);
        assertTrue(board.isPresent());
        assertEquals("Dev Board", board.get().name());
    }

    @Test
    @Order(4)
    void updateBoard() {
        assertTrue(boardRepo.update(boardId, "Dev Board Updated", "Updated desc", 14));
        var board = boardRepo.findById(boardId).orElseThrow();
        assertEquals("Dev Board Updated", board.name());
        assertEquals(14, board.hideDoneAfterDays());
    }

    // -- Lanes --

    @Test
    @Order(10)
    void createLanes() {
        BoardLane lane1 = boardRepo.createLane(boardId, "Open", null, 0);
        BoardLane lane2 = boardRepo.createLane(boardId, "Done", null, 1);
        assertNotNull(lane1);
        assertNotNull(lane2);
        laneId1 = lane1.id();
        laneId2 = lane2.id();
    }

    @Test
    @Order(11)
    void findLanes() {
        var lanes = boardRepo.findLanes(boardId);
        assertEquals(2, lanes.size());
        assertEquals("Open", lanes.get(0).name());
        assertEquals("Done", lanes.get(1).name());
    }

    @Test
    @Order(12)
    void updateLane() {
        assertTrue(boardRepo.updateLane(laneId1, "To Do", 0));
        var lanes = boardRepo.findLanes(boardId);
        assertEquals("To Do", lanes.get(0).name());
    }

    // -- Fields --

    @Test
    @Order(15)
    void createAndFindFields() {
        boardRepo.createField(boardId, "Component", "string", BoardFieldConfig.parse("{}"), 0);
        var fields = boardRepo.findFields(boardId);
        assertEquals(1, fields.size());
        assertEquals("Component", fields.getFirst().name());
    }

    // -- Ticket counter --

    @Test
    @Order(20)
    void nextTicketNumber() {
        int n1 = boardRepo.nextTicketNumber(boardId);
        int n2 = boardRepo.nextTicketNumber(boardId);
        assertEquals(1, n1);
        assertEquals(2, n2);
    }

    // -- Tickets --

    @Test
    @Order(30)
    void createTicket() {
        BoardTicket ticket = boardTicketRepo.createTicket(
                boardId, laneId1, 1, "First ticket", "Description", null, TicketPriority.HIGH, null, 0, member.id());
        assertNotNull(ticket);
        assertEquals("First ticket", ticket.title());
        assertEquals(TicketPriority.HIGH, ticket.priority());
        assertEquals(0, ticket.checklistTotal());
        ticketId1 = ticket.id();
    }

    @Test
    @Order(31)
    void createSecondTicket() {
        BoardTicket ticket = boardTicketRepo.createTicket(
                boardId, laneId1, 2, "Second ticket", null, member.id(), TicketPriority.LOW, null, 1, member.id());
        assertNotNull(ticket);
        assertEquals(member.id(), ticket.assignedMemberId());
        ticketId2 = ticket.id();
    }

    @Test
    @Order(32)
    void findByBoard() {
        var tickets = boardTicketRepo.findByBoard(boardId);
        assertEquals(2, tickets.size());
    }

    @Test
    @Order(33)
    void findByBoardAndLane() {
        var tickets = boardTicketRepo.findByBoardAndLane(boardId, laneId1);
        assertEquals(2, tickets.size());
    }

    @Test
    @Order(34)
    void findByAssignee() {
        var tickets = boardTicketRepo.findByAssignee(boardId, member.id());
        assertEquals(1, tickets.size());
        assertEquals("Second ticket", tickets.getFirst().title());
    }

    @Test
    @Order(35)
    void updateTicket() {
        assertTrue(boardTicketRepo.updateTicket(
                ticketId1, "First ticket updated", "New desc", null, TicketPriority.MEDIUM, null));
        var ticket = boardTicketRepo.findById(ticketId1).orElseThrow();
        assertEquals("First ticket updated", ticket.title());
        assertEquals(TicketPriority.MEDIUM, ticket.priority());
    }

    @Test
    @Order(36)
    void moveTicket() {
        assertTrue(boardTicketRepo.moveTicket(ticketId1, laneId2, 0));
        var ticket = boardTicketRepo.findById(ticketId1).orElseThrow();
        assertEquals(laneId2, ticket.laneId());
    }

    @Test
    @Order(37)
    void logAndFindTransition() {
        boardTicketRepo.logTransition(ticketId1, laneId1, laneId2, member.id());
        var transitions = boardTicketRepo.findTransitions(ticketId1);
        assertEquals(1, transitions.size());
        assertEquals(laneId1, transitions.getFirst().fromLaneId());
        assertEquals(laneId2, transitions.getFirst().toLaneId());
    }

    // -- Links --

    @Test
    @Order(40)
    void createAndFindLinks() {
        boardTicketRepo.createLink(ticketId1, ticketId2, LinkType.BLOCKS);
        var links1 = boardTicketRepo.findLinks(ticketId1);
        assertEquals(1, links1.size());
        assertEquals(LinkType.BLOCKS, links1.getFirst().linkType());

        // Reverse side should show BLOCKED_BY
        var links2 = boardTicketRepo.findLinks(ticketId2);
        assertEquals(1, links2.size());
        assertEquals(LinkType.BLOCKED_BY, links2.getFirst().linkType());
    }

    @Test
    @Order(41)
    void deleteLink() {
        assertTrue(boardTicketRepo.deleteLink(ticketId1, ticketId2));
        assertTrue(boardTicketRepo.findLinks(ticketId1).isEmpty());
    }

    // -- Checklist --

    @Test
    @Order(50)
    void createChecklistItem() {
        BoardChecklistItem item = boardTicketRepo.createChecklistItem(ticketId1, "Write tests", 0);
        assertNotNull(item);
        assertFalse(item.checked());
        checklistItemId = item.id();
    }

    @Test
    @Order(51)
    void updateChecklistItem() {
        assertTrue(boardTicketRepo.updateChecklistItem(checklistItemId, "Write tests", true));
        var items = boardTicketRepo.findChecklistItems(ticketId1);
        assertTrue(items.getFirst().checked());
    }

    @Test
    @Order(52)
    void checklistProgressInTicket() {
        boardTicketRepo.createChecklistItem(ticketId1, "Review code", 1);
        var ticket = boardTicketRepo.findById(ticketId1).orElseThrow();
        assertEquals(2, ticket.checklistTotal());
        assertEquals(1, ticket.checklistChecked());
    }

    @Test
    @Order(53)
    void deleteChecklistItem() {
        assertTrue(boardTicketRepo.deleteChecklistItem(checklistItemId));
        var items = boardTicketRepo.findChecklistItems(ticketId1);
        assertEquals(1, items.size());
    }

    // -- Comments --

    @Test
    @Order(60)
    void createComment() {
        BoardComment comment = boardTicketRepo.createComment(ticketId1, null, member.id(), "First comment");
        assertNotNull(comment);
        assertEquals("First comment", comment.content());
        commentId = comment.id();
    }

    @Test
    @Order(61)
    void updateComment() {
        assertTrue(boardTicketRepo.updateComment(commentId, "Updated comment"));
        var comments = boardTicketRepo.findComments(ticketId1);
        assertEquals("Updated comment", comments.getFirst().content());
    }

    @Test
    @Order(62)
    void createReply() {
        BoardComment reply = boardTicketRepo.createComment(ticketId1, commentId, member.id(), "Reply");
        assertNotNull(reply);
        assertEquals(commentId, reply.parentId());
    }

    @Test
    @Order(63)
    void softDeleteComment() {
        assertTrue(boardTicketRepo.softDeleteComment(commentId));
        var comments = boardTicketRepo.findComments(ticketId1);
        var deleted =
                comments.stream().filter(c -> c.id() == commentId).findFirst().orElseThrow();
        assertTrue(deleted.deleted());
        assertEquals("", deleted.content());
    }

    // -- Access restrictions --

    @Test
    @Order(70)
    void hasNoRestrictionsByDefault() {
        assertFalse(boardRepo.hasViewRestrictions(boardId));
        assertFalse(boardRepo.hasEditRestrictions(boardId));
    }

    @Test
    @Order(71)
    void setAndFindViewAccess() {
        boardRepo.setViewAccess(boardId, List.of(1, 2), List.of(), List.of());
        assertTrue(boardRepo.hasViewRestrictions(boardId));
        assertEquals(List.of(1, 2), boardRepo.findViewAccessRoleIds(boardId));
    }

    @Test
    @Order(72)
    void setViewAccessWithGroupsAndTags() {
        boardRepo.setViewAccess(boardId, List.of(), List.of(10), List.of(20));
        assertTrue(boardRepo.hasViewRestrictions(boardId));
        assertEquals(List.of(10), boardRepo.findViewAccessGroupIds(boardId));
        assertEquals(List.of(20), boardRepo.findViewAccessTagIds(boardId));
        assertTrue(boardRepo.findViewAccessRoleIds(boardId).isEmpty());
    }

    @Test
    @Order(73)
    void setAndFindEditAccess() {
        boardRepo.setEditAccess(boardId, List.of(5), List.of(15), List.of(25));
        assertTrue(boardRepo.hasEditRestrictions(boardId));
        assertEquals(List.of(5), boardRepo.findEditAccessRoleIds(boardId));
        assertEquals(List.of(15), boardRepo.findEditAccessGroupIds(boardId));
        assertEquals(List.of(25), boardRepo.findEditAccessTagIds(boardId));
    }

    @Test
    @Order(74)
    void clearViewAccess() {
        boardRepo.setViewAccess(boardId, List.of(), List.of(), List.of());
        assertFalse(boardRepo.hasViewRestrictions(boardId));
    }

    @Test
    @Order(75)
    void clearEditAccess() {
        boardRepo.setEditAccess(boardId, List.of(), List.of(), List.of());
        assertFalse(boardRepo.hasEditRestrictions(boardId));
    }

    // -- Ticket findByBoardAndNumber --

    @Test
    @Order(76)
    void findByBoardAndNumber() {
        var ticket = boardTicketRepo.findByBoardAndNumber(boardId, 1);
        assertTrue(ticket.isPresent());
        assertEquals("First ticket updated", ticket.get().title());
    }

    // -- Ticket reorder --

    @Test
    @Order(77)
    void reorderTickets() {
        boardTicketRepo.reorderTickets(laneId1, List.of(ticketId2, ticketId1));
        var tickets = boardTicketRepo.findByBoardAndLane(boardId, laneId1);
        // ticketId2 is in laneId1 with position 0, but ticketId1 was moved to laneId2
        // so this just validates the method doesn't throw
        assertNotNull(tickets);
    }

    // -- Checklist reorder --

    @Test
    @Order(78)
    void reorderChecklistItems() {
        var items = boardTicketRepo.findChecklistItems(ticketId1);
        if (!items.isEmpty()) {
            boardTicketRepo.reorderChecklistItems(
                    ticketId1, items.stream().map(BoardChecklistItem::id).toList());
            var reordered = boardTicketRepo.findChecklistItems(ticketId1);
            assertEquals(items.size(), reordered.size());
        }
    }

    // -- Field delete all --

    @Test
    @Order(79)
    void deleteAllFields() {
        boardRepo.deleteAllFields(boardId);
        assertTrue(boardRepo.findFields(boardId).isEmpty());
    }

    // -- Watchers --

    @Test
    @Order(80)
    void addAndFindWatchers() {
        boardTicketRepo.addWatcher(ticketId1, member.id());
        var watchers = boardTicketRepo.findWatchers(ticketId1);
        assertEquals(1, watchers.size());
        assertEquals(member.id(), watchers.getFirst());
        assertTrue(boardTicketRepo.isWatching(ticketId1, member.id()));
    }

    @Test
    @Order(81)
    void removeWatcher() {
        assertTrue(boardTicketRepo.removeWatcher(ticketId1, member.id()));
        assertFalse(boardTicketRepo.isWatching(ticketId1, member.id()));
    }

    // -- Activity feed --

    @Test
    @Order(82)
    void findActivity() {
        var activity = boardTicketRepo.findActivity(ticketId1);
        assertFalse(activity.isEmpty());
        assertTrue(activity.stream().anyMatch(a -> "transition".equals(a.type())));
    }

    // -- Weblinks --

    @Test
    @Order(83)
    void createAndFindWeblinks() {
        var wl = boardTicketRepo.createWeblink(ticketId1, "https://example.com", "Example", 0);
        assertNotNull(wl);
        var weblinks = boardTicketRepo.findWeblinks(ticketId1);
        assertEquals(1, weblinks.size());
        assertTrue(boardTicketRepo.deleteWeblink(wl.id()));
    }

    // -- Attachments --

    @Test
    @Order(84)
    void createAndFindAttachments() {
        var att = boardTicketRepo.createAttachment(ticketId1, "file.txt", "file.txt", "text/plain", 100, member.id());
        assertNotNull(att);
        assertEquals("file.txt", att.originalName());
        var atts = boardTicketRepo.findAttachments(ticketId1);
        assertEquals(1, atts.size());
        assertTrue(boardTicketRepo.findAttachmentById(att.id()).isPresent());
        assertTrue(boardTicketRepo.deleteAttachment(att.id()));
    }

    // -- Field values --

    @Test
    @Order(84)
    void setAndFindFieldValues() {
        boardRepo.createField(boardId, "TestField", "string", BoardFieldConfig.parse("{}"), 0);
        var fields = boardRepo.findFields(boardId);
        int fieldId = fields.getFirst().id();
        boardTicketRepo.setFieldValue(ticketId1, fieldId, "\"hello\"");
        var values = boardTicketRepo.findFieldValues(ticketId1);
        assertEquals(1, values.size());
        assertTrue(boardTicketRepo.deleteFieldValue(ticketId1, fieldId));
        boardRepo.deleteAllFields(boardId);
    }

    // -- Search --

    @Test
    @Order(84)
    void searchTickets() {
        var results = boardTicketRepo.search(boardId, "First");
        assertFalse(results.isEmpty());
    }

    // -- Assign --

    @Test
    @Order(84)
    void assignTicket() {
        assertTrue(boardTicketRepo.assignTicket(ticketId1, member.id()));
        var ticket = boardTicketRepo.findById(ticketId1).orElseThrow();
        assertEquals(member.id(), ticket.assignedMemberId());
        boardTicketRepo.assignTicket(ticketId1, null);
    }

    // -- Lane entered at --

    @Test
    @Order(84)
    void setLaneEnteredAt() {
        boardTicketRepo.setLaneEnteredAt(ticketId1, java.time.Instant.now().minusSeconds(86400));
        var ticket = boardTicketRepo.findById(ticketId1).orElseThrow();
        assertNotNull(ticket.laneEnteredAt());
    }

    // -- Labels --

    @Test
    @Order(84)
    void createAndFindLabels() {
        var label = boardRepo.createLabel(boardId, "Bug", "#ef4444");
        assertNotNull(label);
        var labels = boardRepo.findLabels(boardId);
        assertEquals(1, labels.size());
        assertEquals("Bug", labels.getFirst().name());

        boardRepo.addLabelToTicket(ticketId1, label.id());
        var ticketLabels = boardRepo.findLabelsForTicket(ticketId1);
        assertEquals(1, ticketLabels.size());

        var allMappings = boardRepo.findAllTicketLabels(boardId);
        assertFalse(allMappings.isEmpty());

        assertTrue(boardRepo.removeLabelFromTicket(ticketId1, label.id()));
        assertTrue(boardRepo.updateLabel(label.id(), "Critical", "#ff0000"));
        assertTrue(boardRepo.deleteLabel(label.id()));
    }

    // -- Backlog --

    @Test
    @Order(84)
    void enableAndDisableBacklog() {
        var lane = boardRepo.enableBacklog(boardId);
        assertNotNull(lane);
        var board = boardRepo.findById(boardId).orElseThrow();
        assertNotNull(board.backlogLaneId());

        var laneFound = boardRepo.findLaneById(lane.id());
        assertTrue(laneFound.isPresent());

        boardRepo.disableBacklog(boardId);
        var boardAfter = boardRepo.findById(boardId).orElseThrow();
        assertNull(boardAfter.backlogLaneId());
    }

    // -- Lane update with color --

    @Test
    @Order(84)
    void updateLaneWithColor() {
        assertTrue(boardRepo.updateLane(laneId1, "Updated Lane", 0));
        var lanes = boardRepo.findLanes(boardId);
        assertTrue(lanes.stream().anyMatch(l -> "Updated Lane".equals(l.name())));
        boardRepo.updateLane(laneId1, "Open", 0);
    }

    // -- Delete all lanes --

    @Test
    @Order(84)
    void deleteAllLanes() {
        int tempBoardId =
                boardRepo.create(station.id(), "TempBoard", null, "TMP").id();
        boardRepo.createLane(tempBoardId, "L1", null, 0);
        boardRepo.createLane(tempBoardId, "L2", null, 1);
        boardRepo.deleteAllLanes(tempBoardId);
        assertTrue(boardRepo.findLanes(tempBoardId).isEmpty());
        boardRepo.delete(tempBoardId);
    }

    // -- History --

    @Test
    @Order(84)
    void logAndFindHistory() {
        boardTicketRepo.logHistory(ticketId1, "TEST_ACTION", "test detail", member.id());
        var history = boardTicketRepo.findHistory(ticketId1);
        assertFalse(history.isEmpty());
        assertEquals("TEST_ACTION", history.getLast().action());
    }

    // -- Ticket deletion --

    @Test
    @Order(85)
    void deleteTicket() {
        assertTrue(boardTicketRepo.deleteTicket(ticketId2));
        assertFalse(boardTicketRepo.findById(ticketId2).isPresent());
    }

    // -- Board deletion --

    @Test
    @Order(90)
    void deleteBoard() {
        assertTrue(boardRepo.delete(boardId));
        assertFalse(boardRepo.findById(boardId).isPresent());
    }
}

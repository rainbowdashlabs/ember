/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketKbLink;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.BoardWeblink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Singleton
public class BoardTicketService {
    private static final Pattern MENTION_PATTERN_LEGACY = Pattern.compile("@\\[(\\d+):([^\\]]+)]");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");

    private final BoardTicketRepository ticketRepository;
    private final BoardRepository boardRepository;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public BoardTicketService(
            BoardTicketRepository ticketRepository,
            BoardRepository boardRepository,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            MemberIdentityFactory memberIdentityFactory) {
        this.ticketRepository = ticketRepository;
        this.boardRepository = boardRepository;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private void notifyWatchers(int ticketId, int boardId, String changeDescription, Integer actorMemberId) {
        var watchers = ticketRepository.findWatchers(ticketId);
        if (watchers.isEmpty()) return;
        var board = boardRepository.findById(boardId).orElse(null);
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        if (board == null || ticket == null) return;
        var ticketKey = board.shortKey() + "-" + ticket.ticketNumber();
        eventBus.publish(new BoardTicketChanged(
                board.stationId(),
                boardId,
                ticketId,
                board.name(),
                ticketKey,
                changeDescription,
                actorMemberId,
                watchers));
    }

    // -- Ticket CRUD --

    public List<BoardTicket> findByBoard(int boardId) {
        return ticketRepository.findByBoard(boardId);
    }

    public List<BoardTicket> findByBoardAndLane(int boardId, int laneId) {
        return ticketRepository.findByBoardAndLane(boardId, laneId);
    }

    public Optional<BoardTicket> findById(int id) {
        return ticketRepository.findById(id);
    }

    public Optional<BoardTicket> findByBoardAndNumber(int boardId, int ticketNumber) {
        return ticketRepository.findByBoardAndNumber(boardId, ticketNumber);
    }

    public List<BoardTicket> search(int boardId, String query) {
        return ticketRepository.search(boardId, query);
    }

    public List<BoardTicket> findByAssignee(int boardId, int memberId) {
        UUID memberUid = stationMemberService.resolveUid(memberId);
        if (memberUid == null) return List.of();
        return ticketRepository.findByAssignee(boardId, memberUid);
    }

    public BoardTicket createTicket(
            int boardId,
            int laneId,
            String title,
            String description,
            MemberIdentity assignee,
            TicketPriority priority,
            LocalDate dueDate,
            MemberIdentity creator) {
        int ticketNumber = boardRepository.nextTicketNumber(boardId);
        int position = ticketRepository.findByBoardAndLane(boardId, laneId).size();
        return ticketRepository.createTicket(
                boardId, laneId, ticketNumber, title, description, assignee, priority, dueDate, position, creator);
    }

    public boolean updateTicket(
            int id,
            String title,
            String description,
            MemberIdentity assignee,
            TicketPriority priority,
            LocalDate dueDate,
            MemberIdentity actor) {
        var oldTicket = ticketRepository.findById(id).orElse(null);
        boolean updated = ticketRepository.updateTicket(id, title, description, assignee, priority, dueDate);
        if (updated && oldTicket != null) {
            if (oldTicket.priority() != priority)
                ticketRepository.logHistory(
                        id, "PRIORITY_CHANGED", oldTicket.priority().name() + " → " + priority.name(), actor);
            if (!Objects.equals(oldTicket.title(), title))
                ticketRepository.logHistory(id, "TITLE_CHANGED", oldTicket.title() + " → " + title, actor);
            if (!Objects.equals(oldTicket.description(), description))
                ticketRepository.logHistory(id, "DESCRIPTION_CHANGED", null, actor);
            if (!Objects.equals(oldTicket.dueDate(), dueDate))
                ticketRepository.logHistory(
                        id, "DUE_DATE_CHANGED", (dueDate != null ? dueDate.toString() : "entfernt"), actor);
            notifyWatchers(id, oldTicket.boardId(), "Ticket aktualisiert", null);
        }
        return updated;
    }

    public boolean assignTicket(int ticketId, MemberIdentity assignee, int actorMemberId) {
        boolean updated = ticketRepository.assignTicket(ticketId, assignee);
        if (updated) {
            ticketRepository.findById(ticketId).ifPresent(ticket -> notifyWatchers(ticketId, ticket.boardId(), "Zuweisung geändert", actorMemberId));
        }
        return updated;
    }

    public boolean deleteTicket(int id) {
        return ticketRepository.deleteTicket(id);
    }

    public boolean moveTicket(int ticketId, int fromLaneId, int toLaneId, int position, MemberIdentity actor) {
        boolean moved = ticketRepository.moveTicket(ticketId, toLaneId, position);
        if (moved) {
            ticketRepository.logTransition(ticketId, fromLaneId, toLaneId, actor);
            var ticket = ticketRepository.findById(ticketId).orElse(null);
            var toLane = ticket != null
                    ? boardRepository.findLanes(ticket.boardId()).stream()
                            .filter(l -> l.id() == toLaneId)
                            .findFirst()
                            .orElse(null)
                    : null;
            if (ticket != null) {
                notifyWatchers(
                        ticketId, ticket.boardId(), "Verschoben nach " + (toLane != null ? toLane.name() : "?"), null);
            }
            // Auto-assign from lane_assignee fields
            if (ticket != null) {
                var board = boardRepository.findById(ticket.boardId()).orElse(null);
                var fields = boardRepository.findFields(ticket.boardId());
                var fieldValues = ticketRepository.findFieldValues(ticketId);
                var fvMap = new HashMap<Integer, BoardFieldValue>();
                for (var fv : fieldValues) fvMap.put(fv.fieldId(), fv.value());
                for (var field : fields) {
                    if (field.config() instanceof BoardFieldConfig.LaneAssignee lac && lac.laneId() == toLaneId) {
                        if (fvMap.get(field.id()) instanceof BoardFieldValue.LaneAssignee(int memberId)
                                && board != null) {
                            ticketRepository.assignTicket(
                                    ticketId, memberIdentityFactory.local(board.stationId(), memberId));
                        }
                    }
                }
            }
        }
        return moved;
    }

    public void reorderTickets(int laneId, List<Integer> orderedIds) {
        ticketRepository.reorderTickets(laneId, orderedIds);
    }

    // -- Links --

    public List<BoardTicketLink> findLinks(int ticketId) {
        return ticketRepository.findLinks(ticketId);
    }

    public void linkTickets(int ticketId, int linkedTicketId, LinkType linkType) {
        ticketRepository.createLink(ticketId, linkedTicketId, linkType);
    }

    public boolean unlinkTickets(int ticketId, int linkedTicketId) {
        return ticketRepository.deleteLink(ticketId, linkedTicketId);
    }

    // -- Transitions --

    public List<BoardTicketTransition> findTransitions(int ticketId) {
        return ticketRepository.findTransitions(ticketId);
    }

    // -- Checklist --

    public List<BoardChecklistItem> findChecklistItems(int ticketId) {
        return ticketRepository.findChecklistItems(ticketId);
    }

    public BoardChecklistItem addChecklistItem(int ticketId, String title, int actorMemberId) {
        int position = ticketRepository.findChecklistItems(ticketId).size();
        var item = ticketRepository.createChecklistItem(ticketId, title, position);
        ticketRepository.findById(ticketId).ifPresent(ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
        return item;
    }

    public boolean updateChecklistItem(int id, int ticketId, String title, boolean checked, int actorMemberId) {
        boolean updated = ticketRepository.updateChecklistItem(id, title, checked);
        if (updated) {
            ticketRepository.findById(ticketId).ifPresent(ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
        }
        return updated;
    }

    public boolean deleteChecklistItem(int id, int ticketId, int actorMemberId) {
        boolean deleted = ticketRepository.deleteChecklistItem(id);
        if (deleted) {
            ticketRepository.findById(ticketId).ifPresent(ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
        }
        return deleted;
    }

    public void reorderChecklistItems(int ticketId, List<Integer> orderedIds) {
        ticketRepository.reorderChecklistItems(ticketId, orderedIds);
    }

    // -- Comments --

    public List<BoardComment> findComments(int ticketId) {
        return ticketRepository.findComments(ticketId);
    }

    public BoardComment createComment(int ticketId, Integer parentId, MemberIdentity author, String content) {
        var comment = ticketRepository.createComment(ticketId, parentId, author, content);
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket != null) {
            notifyWatchers(ticketId, ticket.boardId(), "Neuer Kommentar", null);
            var board = boardRepository.findById(ticket.boardId()).orElse(null);
            var ticketKey = board != null ? board.shortKey() + "-" + ticket.ticketNumber() : "?";
            int stationId = board != null ? board.stationId() : 0;
            // Resolve local author member ID for mention exclusion
            Integer authorMemberId = null;
            if (author != null) {
                authorMemberId = stationMemberService
                        .resolveId(stationId, author.memberUid())
                        .orElse(null);
            }
            for (int mentionedId : parseMentions(stationId, content)) {
                if (authorMemberId == null || mentionedId != authorMemberId) {
                    eventBus.publish(new MentionedInComment(
                            stationId,
                            mentionedId,
                            authorMemberId,
                            ticketKey,
                            CommentEntityType.BOARD_TICKET,
                            ticketId));
                }
            }
        }
        return comment;
    }

    private List<Integer> parseMentions(int stationId, String content) {
        var mentions = new ArrayList<Integer>();
        // New format: @[stationUid/memberUid:Name]
        var matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                var memberUid = UUID.fromString(matcher.group(2));
                stationMemberService.resolveId(stationId, memberUid).ifPresent(mentions::add);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Legacy format: @[123:Name]
        var legacyMatcher = MENTION_PATTERN_LEGACY.matcher(content);
        while (legacyMatcher.find()) {
            mentions.add(Integer.parseInt(legacyMatcher.group(1)));
        }
        return mentions;
    }

    public boolean updateComment(int id, String content) {
        return ticketRepository.updateComment(id, content);
    }

    public boolean deleteComment(int id) {
        return ticketRepository.deleteComment(id);
    }

    // -- Weblinks --

    public List<BoardWeblink> findWeblinks(int ticketId) {
        return ticketRepository.findWeblinks(ticketId);
    }

    public BoardWeblink addWeblink(int ticketId, String url, String title) {
        int position = ticketRepository.findWeblinks(ticketId).size();
        return ticketRepository.createWeblink(ticketId, url, title, position);
    }

    public boolean deleteWeblink(int id) {
        return ticketRepository.deleteWeblink(id);
    }

    // -- Attachments --

    private static final Path ATTACHMENT_DIR = Path.of("data", "attachments", "board");

    public List<BoardTicketAttachment> findAttachments(int ticketId) {
        return ticketRepository.findAttachments(ticketId);
    }

    public Optional<BoardTicketAttachment> findAttachmentById(int id) {
        return ticketRepository.findAttachmentById(id);
    }

    public BoardTicketAttachment uploadAttachment(
            int ticketId, String originalName, String contentType, byte[] data, int uploadedBy) {
        String filename = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        var dir = ATTACHMENT_DIR.resolve(String.valueOf(ticketId));
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment", e);
        }
        return ticketRepository.createAttachment(
                ticketId, filename, originalName, contentType, data.length, uploadedBy);
    }

    public boolean deleteAttachment(int id) {
        var att = ticketRepository.findAttachmentById(id).orElse(null);
        if (att == null) return false;
        var file = ATTACHMENT_DIR.resolve(String.valueOf(att.ticketId())).resolve(att.filename());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // log but don't fail
        }
        return ticketRepository.deleteAttachment(id);
    }

    public Path getAttachmentPath(BoardTicketAttachment att) {
        return ATTACHMENT_DIR.resolve(String.valueOf(att.ticketId())).resolve(att.filename());
    }

    // -- Watchers --

    public List<Integer> findWatchers(int ticketId) {
        return ticketRepository.findWatchers(ticketId);
    }

    public void watchTicket(int ticketId, int memberId) {
        ticketRepository.addWatcher(ticketId, memberId);
    }

    public void addWatcher(int ticketId, MemberIdentity identity) {
        ticketRepository.addWatcher(ticketId, identity);
    }

    public boolean removeWatcher(int ticketId, MemberIdentity identity) {
        return ticketRepository.removeWatcher(ticketId, identity);
    }

    public boolean unwatchTicket(int ticketId, int memberId) {
        return ticketRepository.removeWatcher(ticketId, memberId);
    }

    public boolean isWatching(int ticketId, int memberId) {
        return ticketRepository.isWatching(ticketId, memberId);
    }

    // -- Field values --

    public List<BoardTicketFieldValue> findFieldValues(int ticketId) {
        return ticketRepository.findFieldValues(ticketId);
    }

    public void setFieldValue(int ticketId, int fieldId, BoardFieldValue value) {
        ticketRepository.setFieldValue(ticketId, fieldId, value.toJson());
    }

    public boolean deleteFieldValue(int ticketId, int fieldId) {
        return ticketRepository.deleteFieldValue(ticketId, fieldId);
    }

    // -- KB Links --

    public List<BoardTicketKbLink> findKbLinks(int ticketId) {
        return ticketRepository.findKbLinks(ticketId);
    }

    public BoardTicketKbLink addKbLink(int ticketId, int kbFileId) {
        return ticketRepository.addKbLink(ticketId, kbFileId);
    }

    public boolean removeKbLink(int id) {
        return ticketRepository.removeKbLink(id);
    }

    // -- History --

    public void logHistory(int ticketId, String action, String detail, MemberIdentity actor) {
        ticketRepository.logHistory(ticketId, action, detail, actor);
    }

    public List<BoardTicketHistory> findHistory(int ticketId) {
        return ticketRepository.findHistory(ticketId);
    }

    // -- Activity feed --

    public List<BoardTicketRepository.ActivityEntry> findActivity(int ticketId) {
        return ticketRepository.findActivity(ticketId);
    }
}

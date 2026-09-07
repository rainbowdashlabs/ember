/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BoardTicketChanged;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketKbLink;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.BoardWeblink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.service.MentionLimits;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(BoardTicketService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");

    private final BoardTicketRepository ticketRepository;
    private final BoardRepository boardRepository;
    private final BoardService boardService;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;
    private final BoardAttachmentService attachmentService;

    @Inject
    public BoardTicketService(
            BoardTicketRepository ticketRepository,
            BoardRepository boardRepository,
            BoardService boardService,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver,
            BoardAttachmentService attachmentService) {
        this.ticketRepository = ticketRepository;
        this.boardRepository = boardRepository;
        this.boardService = boardService;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.memberNameResolver = memberNameResolver;
        this.attachmentService = attachmentService;
    }

    public List<BoardTicket> findByBoard(int boardId) {
        return ticketRepository.findByBoard(boardId);
    }

    // -- Ticket CRUD --

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

    /**
     * Refuses to hand a ticket to somebody who may not write on its board.
     *
     * <p>The board already says who may work on it, and a ticket put on a name that cannot open it
     * is an instruction nobody can follow. The question is asked of the person being handed the
     * work rather than of the person handing it over, and it is asked exactly as the board asks it
     * of an editor, so somebody who administers every board may be handed a ticket on one they were
     * never separately let into.
     *
     * <p>It asks it through the same list the picker is built from, so the two cannot drift apart
     * and offer a name the server then turns down.
     *
     * <p>What it does not do is look back. A ticket already on a name keeps it when that person
     * later loses the right: the rule is about handing work over, not about holding it, and taking
     * a name off a ticket on its own would throw away the only record of who was on it.
     *
     * @param boardId  the board the ticket belongs to
     * @param assignee whom the ticket is being handed to, or {@code null} to take the name off
     */
    private void requireAssignable(int boardId, MemberIdentity assignee) {
        if (assignee == null) return;
        var board = boardRepository.findById(boardId).orElse(null);
        if (board == null) return;
        int memberId = stationMemberService
                .resolveId(board.stationId(), assignee.memberUid())
                .orElseThrow(() -> new BadRequestResponse("The assignee is not a member of the board's station"));
        if (!boardService.findMembersWhoMayEdit(boardId, board.stationId()).contains(memberId)) {
            throw new BadRequestResponse("The assignee has no write access to this board");
        }
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
        requireAssignable(boardId, assignee);
        int ticketNumber = boardRepository.nextTicketNumber(boardId);
        int position = ticketRepository.findByBoardAndLane(boardId, laneId).size();
        var ticket = ticketRepository.createTicket(
                boardId, laneId, ticketNumber, title, description, assignee, priority, dueDate, position, creator);
        log.info("Created ticket {} on board {} in lane {}", ticket.id(), boardId, laneId);
        return ticket;
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
        if (oldTicket != null) requireAssignable(oldTicket.boardId(), assignee);
        boolean updated = ticketRepository.updateTicket(id, title, description, assignee, priority, dueDate);
        if (updated && oldTicket != null) {
            if (oldTicket.priority() != priority)
                ticketRepository.logHistory(
                        id,
                        BoardTicketHistoryAction.PRIORITY_CHANGED,
                        oldTicket.priority().name() + " → " + priority.name(),
                        actor);
            if (!Objects.equals(oldTicket.title(), title))
                ticketRepository.logHistory(
                        id, BoardTicketHistoryAction.TITLE_CHANGED, oldTicket.title() + " → " + title, actor);
            if (!Objects.equals(oldTicket.description(), description))
                ticketRepository.logHistory(id, BoardTicketHistoryAction.DESCRIPTION_CHANGED, null, actor);
            if (!Objects.equals(oldTicket.dueDate(), dueDate))
                ticketRepository.logHistory(
                        id,
                        BoardTicketHistoryAction.DUE_DATE_CHANGED,
                        (dueDate != null ? dueDate.toString() : "entfernt"),
                        actor);
            notifyWatchers(id, oldTicket.boardId(), "Ticket aktualisiert", null);
            log.info("Updated ticket {}", id);
        } else if (!updated) {
            log.warn("Update for ticket {} affected zero rows", id);
        }
        return updated;
    }

    public boolean assignTicket(int ticketId, MemberIdentity assignee, int actorMemberId) {
        var oldTicket = ticketRepository.findById(ticketId).orElse(null);
        if (oldTicket != null) requireAssignable(oldTicket.boardId(), assignee);
        MemberIdentity oldAssignee = oldTicket != null ? oldTicket.assignee() : null;
        boolean updated = ticketRepository.assignTicket(ticketId, assignee);
        if (updated && oldTicket != null) {
            var board = boardRepository.findById(oldTicket.boardId()).orElse(null);
            String ticketKey =
                    board != null ? board.shortKey() + "-" + oldTicket.ticketNumber() : String.valueOf(ticketId);
            MemberIdentity actorIdentity =
                    memberIdentityFactory.local(board != null ? board.stationId() : 0, actorMemberId);

            // Log history
            String oldName = memberNameResolver.resolve(oldAssignee);
            String newName = memberNameResolver.resolve(assignee);
            String detail = (oldName != null ? oldName : "-") + " → " + (newName != null ? newName : "-");
            ticketRepository.logHistory(ticketId, BoardTicketHistoryAction.ASSIGNEE_CHANGED, detail, actorIdentity);

            // Notify watchers
            notifyWatchers(ticketId, oldTicket.boardId(), "Zuweisung geändert", actorMemberId);

            // Notify unassigned member
            if (oldAssignee != null) {
                var oldMemberId =
                        stationMemberService.resolveId(board != null ? board.stationId() : 0, oldAssignee.memberUid());
                oldMemberId.ifPresent(id -> eventBus.publish(new BoardTicketChanged(
                        board != null ? board.stationId() : 0,
                        oldTicket.boardId(),
                        ticketId,
                        board != null ? board.shortKey() : "",
                        oldTicket.ticketNumber(),
                        board != null ? board.name() : "",
                        ticketKey,
                        "Du wurdest von " + ticketKey + " abgemeldet",
                        actorMemberId,
                        List.of(id))));
            }

            // Notify newly assigned member
            if (assignee != null) {
                var newMemberId =
                        stationMemberService.resolveId(board != null ? board.stationId() : 0, assignee.memberUid());
                newMemberId.ifPresent(id -> {
                    if (id != actorMemberId) {
                        eventBus.publish(new BoardTicketChanged(
                                board != null ? board.stationId() : 0,
                                oldTicket.boardId(),
                                ticketId,
                                board != null ? board.shortKey() : "",
                                oldTicket.ticketNumber(),
                                board != null ? board.name() : "",
                                ticketKey,
                                "Du wurdest " + ticketKey + " zugewiesen",
                                actorMemberId,
                                List.of(id)));
                    }
                });
            }
            log.info("Assigned ticket {} (actor member {})", ticketId, actorMemberId);
        } else if (!updated) {
            log.warn("Assign for ticket {} affected zero rows", ticketId);
        }
        return updated;
    }

    public boolean deleteTicket(int id) {
        boolean deleted = ticketRepository.deleteTicket(id);
        if (deleted) {
            log.info("Deleted ticket {}", id);
        } else {
            log.warn("Delete for ticket {} affected zero rows", id);
        }
        return deleted;
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
            log.info("Moved ticket {} from lane {} to lane {}", ticketId, fromLaneId, toLaneId);
        } else if (!moved) {
            log.warn("Move for ticket {} from lane {} to lane {} affected zero rows", ticketId, fromLaneId, toLaneId);
        }
        return moved;
    }

    public void reorderTickets(int laneId, List<Integer> orderedIds) {
        ticketRepository.reorderTickets(laneId, orderedIds);
        log.debug("Lane {} reordered to {} ticket(s)", laneId, orderedIds.size());
    }

    public List<BoardTicketLink> findLinks(int ticketId) {
        return ticketRepository.findLinks(ticketId);
    }

    // -- Links --

    public void linkTickets(int ticketId, int linkedTicketId, LinkType linkType, MemberIdentity actor) {
        if (ticketId == linkedTicketId) {
            log.warn("Refusing to link ticket {} to itself", ticketId);
            return;
        }
        ticketRepository.createLink(ticketId, linkedTicketId, linkType);
        log.info("Linked ticket {} to ticket {} ({})", ticketId, linkedTicketId, linkType);
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        var linkedTicket = ticketRepository.findById(linkedTicketId).orElse(null);
        if (ticket != null && linkedTicket != null) {
            logLinkHistory(ticket, linkedTicket, BoardTicketHistoryAction.LINK_ADDED, actor);
        }
    }

    private void logLinkHistory(
            BoardTicket ticket, BoardTicket linkedTicket, BoardTicketHistoryAction action, MemberIdentity actor) {
        var board = boardRepository.findById(ticket.boardId()).orElse(null);
        var linkedBoard = boardRepository.findById(linkedTicket.boardId()).orElse(null);
        String key = (board != null ? board.shortKey() : "?") + "-" + linkedTicket.ticketNumber();
        String reverseKey = (linkedBoard != null ? linkedBoard.shortKey() : "?") + "-" + ticket.ticketNumber();
        ticketRepository.logHistory(ticket.id(), action, key, actor);
        ticketRepository.logHistory(linkedTicket.id(), action, reverseKey, actor);
    }

    public boolean unlinkTickets(int ticketId, int linkedTicketId, MemberIdentity actor) {
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        var linkedTicket = ticketRepository.findById(linkedTicketId).orElse(null);
        boolean deleted = ticketRepository.deleteLink(ticketId, linkedTicketId);
        if (deleted && ticket != null && linkedTicket != null) {
            logLinkHistory(ticket, linkedTicket, BoardTicketHistoryAction.LINK_REMOVED, actor);
            log.info("Unlinked ticket {} from ticket {}", ticketId, linkedTicketId);
        } else if (!deleted) {
            log.warn("Unlink of ticket {} from ticket {} affected zero rows", ticketId, linkedTicketId);
        }
        return deleted;
    }

    public List<BoardTicketTransition> findTransitions(int ticketId) {
        return ticketRepository.findTransitions(ticketId);
    }

    // -- Transitions --

    public List<BoardChecklistItem> findChecklistItems(int ticketId) {
        return ticketRepository.findChecklistItems(ticketId);
    }

    // -- Checklist --

    public BoardChecklistItem addChecklistItem(int ticketId, String title, int actorMemberId) {
        int position = ticketRepository.findChecklistItems(ticketId).size();
        var item = ticketRepository.createChecklistItem(ticketId, title, position);
        ticketRepository
                .findById(ticketId)
                .ifPresent(ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
        log.info("Added checklist item {} on ticket {}", item.id(), ticketId);
        return item;
    }

    public boolean updateChecklistItem(int id, int ticketId, String title, boolean checked, int actorMemberId) {
        boolean updated = ticketRepository.updateChecklistItem(id, title, checked);
        if (updated) {
            ticketRepository
                    .findById(ticketId)
                    .ifPresent(
                            ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
            log.info("Updated checklist item {} on ticket {} (checked={})", id, ticketId, checked);
        } else {
            log.warn("Update for checklist item {} on ticket {} affected zero rows", id, ticketId);
        }
        return updated;
    }

    public boolean deleteChecklistItem(int id, int ticketId, int actorMemberId) {
        boolean deleted = ticketRepository.deleteChecklistItem(id);
        if (deleted) {
            ticketRepository
                    .findById(ticketId)
                    .ifPresent(
                            ticket -> notifyWatchers(ticketId, ticket.boardId(), "Checkliste geändert", actorMemberId));
            log.info("Deleted checklist item {} on ticket {}", id, ticketId);
        } else {
            log.warn("Delete for checklist item {} on ticket {} affected zero rows", id, ticketId);
        }
        return deleted;
    }

    public void reorderChecklistItems(int ticketId, List<Integer> orderedIds) {
        ticketRepository.reorderChecklistItems(ticketId, orderedIds);
        log.debug("Checklist of ticket {} reordered to {} item(s)", ticketId, orderedIds.size());
    }

    public List<BoardComment> findComments(int ticketId) {
        return ticketRepository.findComments(ticketId);
    }

    // -- Comments --

    public BoardComment createComment(int ticketId, Integer parentId, MemberIdentity author, String content) {
        var comment = ticketRepository.createComment(ticketId, parentId, author, content);
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket != null) {
            notifyWatchers(ticketId, ticket.boardId(), "Neuer Kommentar", null);
            var board = boardRepository.findById(ticket.boardId()).orElse(null);
            var ticketKey = board != null ? board.shortKey() + "-" + ticket.ticketNumber() : "?";
            int stationId = board != null ? board.stationId() : 0;
            var address = board != null ? new BoardTicketAddress(board.shortKey(), ticket.ticketNumber()) : null;
            // Resolve local author member ID for mention exclusion
            Integer authorMemberId = null;
            if (author != null) {
                authorMemberId = stationMemberService
                        .resolveId(stationId, author.memberUid())
                        .orElse(null);
            }
            String mentionPreview = content.length() > 100 ? content.substring(0, 100) + "…" : content;
            for (int mentionedId : parseMentions(stationId, content)) {
                if (authorMemberId == null || mentionedId != authorMemberId) {
                    eventBus.publish(new MentionedInComment(
                            stationId,
                            mentionedId,
                            authorMemberId,
                            ticketKey,
                            CommentEntityType.BOARD_TICKET,
                            ticketId,
                            ticketKey,
                            address,
                            comment.id(),
                            mentionPreview));
                }
            }
        }
        log.info("Created comment {} on ticket {}", comment.id(), ticketId);
        return comment;
    }

    public boolean updateComment(int id, String content) {
        boolean updated = ticketRepository.updateComment(id, content);
        if (updated) {
            log.info("Updated comment {}", id);
        } else {
            log.warn("Update for comment {} affected zero rows", id);
        }
        return updated;
    }

    /**
     * Deletes a comment on a ticket and announces the removal, so that whatever was written about
     * it can be withdrawn.
     *
     * @param ticketId the ticket the comment hangs under, which names the owning station
     * @param id       the comment to remove
     * @return {@code true} when a comment was removed
     */
    public boolean deleteComment(int ticketId, int id) {
        boolean deleted = ticketRepository.deleteComment(id);
        if (deleted) {
            eventBus.publish(new CommentDeleted(stationOf(ticketId), CommentEntityType.BOARD_TICKET, id));
            log.info("Deleted comment {}", id);
        } else {
            log.warn("Delete for comment {} affected zero rows", id);
        }
        return deleted;
    }

    private int stationOf(int ticketId) {
        return ticketRepository
                .findById(ticketId)
                .flatMap(ticket -> boardRepository.findById(ticket.boardId()))
                .map(Board::stationId)
                .orElse(0);
    }

    public List<BoardWeblink> findWeblinks(int ticketId) {
        return ticketRepository.findWeblinks(ticketId);
    }

    public BoardWeblink addWeblink(int ticketId, String url, String title) {
        int position = ticketRepository.findWeblinks(ticketId).size();
        var weblink = ticketRepository.createWeblink(ticketId, url, title, position);
        log.info("Added weblink {} on ticket {}", weblink.id(), ticketId);
        return weblink;
    }

    // -- Weblinks --

    public boolean deleteWeblink(int id) {
        boolean deleted = ticketRepository.deleteWeblink(id);
        if (deleted) {
            log.info("Deleted weblink {}", id);
        } else {
            log.warn("Delete for weblink {} affected zero rows", id);
        }
        return deleted;
    }

    public List<BoardTicketAttachment> findAttachments(int ticketId) {
        return ticketRepository.findAttachments(ticketId);
    }

    public Optional<BoardTicketAttachment> findAttachmentById(int id) {
        return ticketRepository.findAttachmentById(id);
    }

    // -- Attachments --

    public BoardTicketAttachment uploadAttachment(
            int stationId,
            int ticketId,
            String originalName,
            String contentType,
            byte[] data,
            MemberIdentity uploader) {
        String filename = attachmentService.newFilename(originalName);
        attachmentService.store(stationId, ticketId, filename, data, contentType);
        var attachment =
                ticketRepository.createAttachment(ticketId, filename, originalName, contentType, data.length, uploader);
        log.info("Uploaded attachment {} on ticket {} ({} bytes)", attachment.id(), ticketId, data.length);
        return attachment;
    }

    public boolean deleteAttachment(int stationId, int id) {
        var att = ticketRepository.findAttachmentById(id).orElse(null);
        if (att == null) {
            log.warn("Delete for attachment {} skipped: not found", id);
            return false;
        }
        attachmentService.delete(stationId, att.ticketId(), att.filename());
        boolean deleted = ticketRepository.deleteAttachment(id);
        if (deleted) {
            log.info("Deleted attachment {} on ticket {}", id, att.ticketId());
        } else {
            log.warn("Delete for attachment {} affected zero rows", id);
        }
        return deleted;
    }

    public Path getAttachmentPath(int stationId, BoardTicketAttachment att) {
        return attachmentService.resolvePath(stationId, att.ticketId(), att.filename());
    }

    /**
     * The local member IDs watching a ticket. Watchers from other stations are dropped, since they
     * have no local ID to report.
     */
    public List<Integer> findWatchers(int ticketId) {
        return ticketRepository.findWatcherIdentities(ticketId).stream()
                .map(stationMemberService::resolveMemberId)
                .flatMap(Optional::stream)
                .toList();
    }

    public void watchTicket(int ticketId, int memberId) {
        addWatcher(ticketId, stationMemberService.resolveIdentity(memberId));
    }

    // -- Watchers --

    public void addWatcher(int ticketId, MemberIdentity identity) {
        ticketRepository.addWatcher(ticketId, identity);
        log.debug("Ticket {} is now watched by {}", ticketId, identity);
    }

    public boolean removeWatcher(int ticketId, MemberIdentity identity) {
        boolean removed = ticketRepository.removeWatcher(ticketId, identity);
        if (removed) log.debug("Ticket {} is no longer watched by {}", ticketId, identity);
        return removed;
    }

    public boolean unwatchTicket(int ticketId, int memberId) {
        return removeWatcher(ticketId, stationMemberService.resolveIdentity(memberId));
    }

    public boolean isWatching(int ticketId, int memberId) {
        return ticketRepository.isWatching(ticketId, stationMemberService.resolveIdentity(memberId));
    }

    public List<BoardTicketFieldValue> findFieldValues(int ticketId) {
        return ticketRepository.findFieldValues(ticketId);
    }

    public void setFieldValue(int ticketId, int fieldId, BoardFieldValue value) {
        ticketRepository.setFieldValue(ticketId, fieldId, value);
        log.info("Field {} of ticket {} was filled in", fieldId, ticketId);
    }

    // -- Field values --

    public boolean deleteFieldValue(int ticketId, int fieldId) {
        boolean deleted = ticketRepository.deleteFieldValue(ticketId, fieldId);
        if (deleted) log.info("Field {} of ticket {} was cleared", fieldId, ticketId);
        else log.warn("Clear of field {} on ticket {} affected zero rows", fieldId, ticketId);
        return deleted;
    }

    public List<BoardTicketKbLink> findKbLinks(int ticketId) {
        return ticketRepository.findKbLinks(ticketId);
    }

    public BoardTicketKbLink addKbLink(int ticketId, int kbFileId) {
        BoardTicketKbLink link = ticketRepository.addKbLink(ticketId, kbFileId);
        log.info("Ticket {} now points at knowledge file {}", ticketId, kbFileId);
        return link;
    }

    // -- KB Links --

    public void removeKbLink(int id) {
        ticketRepository.removeKbLink(id);
        log.info("Removed knowledge link {} from its ticket", id);
    }

    public void logHistory(int ticketId, BoardTicketHistoryAction action, String detail, MemberIdentity actor) {
        ticketRepository.logHistory(ticketId, action, detail, actor);
    }

    public List<BoardTicketHistory> findHistory(int ticketId) {
        return ticketRepository.findHistory(ticketId);
    }

    // -- History --

    public List<BoardTicketRepository.ActivityEntry> findActivity(int ticketId) {
        return ticketRepository.findActivity(ticketId);
    }

    private void notifyWatchers(int ticketId, int boardId, String changeDescription, Integer actorMemberId) {
        var watchers = findWatchers(ticketId);
        if (watchers.isEmpty()) return;
        var board = boardRepository.findById(boardId).orElse(null);
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        if (board == null || ticket == null) return;
        var ticketKey = board.shortKey() + "-" + ticket.ticketNumber();
        eventBus.publish(new BoardTicketChanged(
                board.stationId(),
                boardId,
                ticketId,
                board.shortKey(),
                ticket.ticketNumber(),
                board.name(),
                ticketKey,
                changeDescription,
                actorMemberId,
                watchers));
    }

    // -- Activity feed --

    /**
     * The members a comment mentions, resolved through the station the ticket belongs to. Only the
     * form carrying a station and a member uid is read: a bare numeric id names a member anywhere
     * on the instance, and notifying by it reaches into another station.
     */
    private List<Integer> parseMentions(int stationId, String content) {
        var mentions = new ArrayList<Integer>();
        var matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find() && mentions.size() < MentionLimits.MAX_MEMBER_MENTIONS) {
            try {
                var memberUid = UUID.fromString(matcher.group(2));
                stationMemberService.resolveId(stationId, memberUid).ifPresent(mentions::add);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return mentions;
    }
}

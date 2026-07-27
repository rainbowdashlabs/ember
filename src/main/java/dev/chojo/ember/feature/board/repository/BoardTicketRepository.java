/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardActivityType;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardFieldValue;
import dev.chojo.ember.feature.board.entity.BoardTicket;
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
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class BoardTicketRepository {

    private static final String TICKET_COLUMNS =
            "id, board_id, lane_id, ticket_number, title, description, assignee_station_uid, assignee_member_uid, priority, due_date, position, creator_station_uid, creator_member_uid, created_at, updated_at, lane_entered_at";
    private static final String TICKET_SELECT = """
            SELECT %s,
                COALESCE((SELECT count(*) FROM board_ticket_checklist_item WHERE ticket_id = t.id), 0) AS checklist_total,
                COALESCE((SELECT count(*) FROM board_ticket_checklist_item WHERE ticket_id = t.id AND checked = true), 0) AS checklist_checked,
                COALESCE((SELECT count(*) FROM board_ticket_attachment WHERE ticket_id = t.id), 0) AS attachment_count
            FROM board_ticket t""".formatted(SqlSupport.alias("t", TICKET_COLUMNS));
    private static final String TRANSITION_COLUMNS =
            "id, ticket_id, from_lane_id, to_lane_id, actor_station_uid, actor_member_uid, moved_at";
    private static final String CHECKLIST_ITEM_COLUMNS = "id, ticket_id, title, checked, position";
    private static final String COMMENT_COLUMNS =
            "id, ticket_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at";
    private static final String WEBLINK_COLUMNS = "id, ticket_id, url, title, position";
    private static final String ATTACHMENT_COLUMNS =
            "id, ticket_id, filename, original_name, content_type, size_bytes, uploader_station_uid, uploader_member_uid, created_at";
    private static final String HISTORY_COLUMNS =
            "id, ticket_id, action, detail, actor_station_uid, actor_member_uid, created_at";
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;

    @Inject
    public BoardTicketRepository(StationMemberRepository stationMemberRepository, StationRepository stationRepository) {
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
    }

    // -- Ticket CRUD --

    private static String preparePrefixQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> w.replaceAll("[^\\w\\p{L}]", "") + ":*")
                .collect(Collectors.joining(" & "));
    }

    public List<BoardTicket> findByBoard(int boardId) {
        return query("""
                %s
                WHERE t.board_id = :board_id
                ORDER BY t.position;""", TICKET_SELECT)
                .single(call().bind("board_id", boardId))
                .map(BoardTicket.map())
                .all();
    }

    public List<BoardTicket> findByBoardAndLane(int boardId, int laneId) {
        return query("""
                %s
                WHERE t.board_id = :board_id
                  AND t.lane_id = :lane_id
                ORDER BY t.position;""", TICKET_SELECT)
                .single(call().bind("board_id", boardId).bind("lane_id", laneId))
                .map(BoardTicket.map())
                .all();
    }

    public Optional<BoardTicket> findById(int id) {
        return query("""
                %s
                WHERE t.id = :id;""", TICKET_SELECT)
                .single(call().bind("id", id))
                .map(BoardTicket.map())
                .first();
    }

    public Optional<BoardTicket> findByBoardAndNumber(int boardId, int ticketNumber) {
        return query("""
                %s
                WHERE t.board_id = :board_id
                  AND t.ticket_number = :ticket_number;""", TICKET_SELECT)
                .single(call().bind("board_id", boardId).bind("ticket_number", ticketNumber))
                .map(BoardTicket.map())
                .first();
    }

    public List<BoardTicket> findByAssignee(int boardId, UUID memberUid) {
        return query("""
                %s
                WHERE t.board_id = :board_id
                  AND t.assignee_member_uid = :member_uid::uuid
                ORDER BY t.position;""", TICKET_SELECT)
                .single(call().bind("board_id", boardId)
                        .bind("member_uid", memberUid, StandardValueConverter.UUID_STRING))
                .map(BoardTicket.map())
                .all();
    }

    public BoardTicket createTicket(
            int boardId,
            int laneId,
            int ticketNumber,
            String title,
            String description,
            MemberIdentity assignee,
            TicketPriority priority,
            LocalDate dueDate,
            int position,
            MemberIdentity creator) {
        return SqlSupport.insertReturning(
                """
                WITH ins AS (
                    INSERT INTO board_ticket(board_id, lane_id, ticket_number, title, description,
                        assignee_station_uid, assignee_member_uid, priority, due_date, position,
                        creator_station_uid, creator_member_uid)
                    VALUES (:board_id, :lane_id, :ticket_number, :title, :description,
                        :assignee_station_uid::UUID, :assignee_member_uid::UUID, :priority, :due_date, :position,
                        :creator_station_uid::UUID, :creator_member_uid::UUID)
                    RETURNING %s
                )
                SELECT %s,
                    0 AS checklist_total,
                    0 AS checklist_checked,
                    0 AS attachment_count
                FROM ins;""",
                call().bind("board_id", boardId)
                        .bind("lane_id", laneId)
                        .bind("ticket_number", ticketNumber)
                        .bind("title", title)
                        .bind("description", description)
                        .bind(
                                "assignee_station_uid",
                                assignee != null ? assignee.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "assignee_member_uid",
                                assignee != null ? assignee.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind("priority", priority)
                        .bind("due_date", dueDate)
                        .bind("position", position)
                        .bind(
                                "creator_station_uid",
                                creator != null ? creator.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "creator_member_uid",
                                creator != null ? creator.memberUid() : null,
                                StandardValueConverter.UUID_STRING),
                BoardTicket.map(),
                TICKET_COLUMNS,
                SqlSupport.alias("ins", TICKET_COLUMNS));
    }

    public boolean updateTicket(
            int id,
            String title,
            String description,
            MemberIdentity assignee,
            TicketPriority priority,
            LocalDate dueDate) {
        return query("""
                UPDATE board_ticket SET title = :title, description = :description,
                    assignee_station_uid = :assignee_station_uid::UUID, assignee_member_uid = :assignee_member_uid::UUID,
                    priority = :priority, due_date = :due_date, updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind(
                                "assignee_station_uid",
                                assignee != null ? assignee.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "assignee_member_uid",
                                assignee != null ? assignee.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind("priority", priority)
                        .bind("due_date", dueDate))
                .update()
                .changed();
    }

    public boolean assignTicket(int id, MemberIdentity assignee) {
        return query("""
                UPDATE board_ticket SET assignee_station_uid = :assignee_station_uid::UUID,
                    assignee_member_uid = :assignee_member_uid::UUID, updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind(
                                "assignee_station_uid",
                                assignee != null ? assignee.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "assignee_member_uid",
                                assignee != null ? assignee.memberUid() : null,
                                StandardValueConverter.UUID_STRING))
                .update()
                .changed();
    }

    public boolean deleteTicket(int id) {
        return SqlSupport.deleteById("board_ticket", id);
    }

    public boolean moveTicket(int ticketId, int toLaneId, int position) {
        return query("""
                UPDATE board_ticket SET lane_id = :lane_id, position = :position,
                    lane_entered_at = now(), updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", ticketId).bind("lane_id", toLaneId).bind("position", position))
                .update()
                .changed();
    }

    public void setLaneEnteredAt(int ticketId, Instant laneEnteredAt) {
        query("UPDATE board_ticket SET lane_entered_at = :entered WHERE id = :id;")
                .single(call().bind("id", ticketId)
                        .bind("entered", laneEnteredAt, StandardValueConverter.INSTANT_TIMESTAMP))
                .update();
    }

    // -- Ticket links --

    public void reorderTickets(int laneId, List<Integer> orderedIds) {
        SqlSupport.reorder("board_ticket", "position", "lane_id", laneId, orderedIds);
    }

    public List<BoardTicketLink> findLinks(int ticketId) {
        var stored = query(
                        "SELECT ticket_id, linked_ticket_id, link_type FROM board_ticket_link WHERE ticket_id = :ticket_id;")
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketLink.map())
                .all();
        var reverse = query(
                        "SELECT linked_ticket_id AS ticket_id, ticket_id AS linked_ticket_id, link_type FROM board_ticket_link WHERE linked_ticket_id = :ticket_id;")
                .single(call().bind("ticket_id", ticketId))
                .map(row -> new BoardTicketLink(
                        row.getInt("ticket_id"),
                        row.getInt("linked_ticket_id"),
                        row.getEnum("link_type", LinkType.class).inverse()))
                .all();
        var result = new ArrayList<>(stored);
        result.addAll(reverse);
        return result;
    }

    public void createLink(int ticketId, int linkedTicketId, LinkType linkType) {
        query("""
                INSERT INTO board_ticket_link(ticket_id, linked_ticket_id, link_type)
                VALUES (:ticket_id, :linked_ticket_id, :link_type)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("linked_ticket_id", linkedTicketId)
                        .bind("link_type", linkType))
                .insert();
    }

    // -- Transitions --

    public boolean deleteLink(int ticketId, int linkedTicketId) {
        boolean deleted = query(
                        "DELETE FROM board_ticket_link WHERE ticket_id = :ticket_id AND linked_ticket_id = :linked_ticket_id;")
                .single(call().bind("ticket_id", ticketId).bind("linked_ticket_id", linkedTicketId))
                .delete()
                .changed();
        if (!deleted) {
            deleted = query(
                            "DELETE FROM board_ticket_link WHERE ticket_id = :linked_ticket_id AND linked_ticket_id = :ticket_id;")
                    .single(call().bind("ticket_id", ticketId).bind("linked_ticket_id", linkedTicketId))
                    .delete()
                    .changed();
        }
        return deleted;
    }

    public void logTransition(int ticketId, int fromLaneId, int toLaneId, MemberIdentity actor) {
        query("""
                INSERT INTO board_ticket_transition(ticket_id, from_lane_id, to_lane_id, actor_station_uid, actor_member_uid)
                VALUES (:ticket_id, :from_lane_id, :to_lane_id, :actor_station_uid::UUID, :actor_member_uid::UUID);""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("from_lane_id", fromLaneId)
                        .bind("to_lane_id", toLaneId)
                        .bind(
                                "actor_station_uid",
                                actor != null ? actor.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "actor_member_uid",
                                actor != null ? actor.memberUid() : null,
                                StandardValueConverter.UUID_STRING))
                .insert();
    }

    // -- Checklist --

    public List<BoardTicketTransition> findTransitions(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_transition WHERE ticket_id = :ticket_id ORDER BY moved_at;",
                        TRANSITION_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketTransition.map())
                .all();
    }

    public List<BoardChecklistItem> findChecklistItems(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_checklist_item WHERE ticket_id = :ticket_id ORDER BY position;",
                        CHECKLIST_ITEM_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardChecklistItem.map())
                .all();
    }

    public BoardChecklistItem createChecklistItem(int ticketId, String title, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_ticket_checklist_item(ticket_id, title, position)
                VALUES (:ticket_id, :title, :position)
                RETURNING %s;""",
                call().bind("ticket_id", ticketId).bind("title", title).bind("position", position),
                BoardChecklistItem.map(),
                CHECKLIST_ITEM_COLUMNS);
    }

    public boolean updateChecklistItem(int id, String title, boolean checked) {
        return query("UPDATE board_ticket_checklist_item SET title = :title, checked = :checked WHERE id = :id;")
                .single(call().bind("id", id).bind("title", title).bind("checked", checked))
                .update()
                .changed();
    }

    public boolean deleteChecklistItem(int id) {
        return SqlSupport.deleteById("board_ticket_checklist_item", id);
    }

    // -- Comments --

    public void reorderChecklistItems(int ticketId, List<Integer> orderedIds) {
        SqlSupport.reorder("board_ticket_checklist_item", "position", "ticket_id", ticketId, orderedIds);
    }

    public List<BoardComment> findComments(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_comment WHERE ticket_id = :ticket_id ORDER BY created_at;",
                        COMMENT_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardComment.map())
                .all();
    }

    public BoardComment createComment(int ticketId, Integer parentId, MemberIdentity author, String content) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_ticket_comment(ticket_id, parent_id, author_station_uid, author_member_uid, content)
                VALUES (:ticket_id, :parent_id, :author_station_uid::UUID, :author_member_uid::UUID, :content)
                RETURNING %s;""",
                call().bind("ticket_id", ticketId)
                        .bind("parent_id", parentId)
                        .bind(
                                "author_station_uid",
                                author != null ? author.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "author_member_uid",
                                author != null ? author.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind("content", content),
                BoardComment.map(),
                COMMENT_COLUMNS);
    }

    public boolean updateComment(int id, String content) {
        return query("UPDATE board_ticket_comment SET content = :content, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("content", content))
                .update()
                .changed();
    }

    /**
     * Soft-deletes a comment if it has children, or hard-deletes it if it has none.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted or marked as deleted
     */
    public boolean deleteComment(int id) {
        if (hasCommentChildren(id)) {
            return query("UPDATE board_ticket_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(call().bind("id", id))
                    .update()
                    .changed();
        }
        return SqlSupport.deleteById("board_ticket_comment", id);
    }

    // -- Watchers --

    /**
     * Checks whether a comment has any child replies.
     *
     * @param id the comment ID
     * @return {@code true} if the comment has children
     */
    public boolean hasCommentChildren(int id) {
        return query("SELECT exists(SELECT 1 FROM board_ticket_comment WHERE parent_id = :id);")
                .single(call().bind("id", id))
                .map(row -> row.getBoolean(1))
                .first()
                .orElse(false);
    }

    public List<Integer> findWatchers(int ticketId) {
        return query(
                        "SELECT watcher_station_uid, watcher_member_uid FROM board_ticket_watcher WHERE ticket_id = :ticket_id;")
                .single(call().bind("ticket_id", ticketId))
                .map(row -> {
                    UUID memberUid = row.get("watcher_member_uid", StandardValueConverter.UUID_STRING);
                    UUID stationUid = row.get("watcher_station_uid", StandardValueConverter.UUID_STRING);
                    return stationRepository
                            .resolveId(stationUid)
                            .flatMap(sid -> stationMemberRepository.resolveId(sid, memberUid));
                })
                .all()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public void addWatcher(int ticketId, int memberId) {
        MemberIdentity identity = stationMemberRepository.resolveIdentity(memberId);
        if (identity == null) return;
        query("""
                INSERT INTO board_ticket_watcher(ticket_id, watcher_station_uid, watcher_member_uid)
                VALUES (:ticket_id, :watcher_station_uid::UUID, :watcher_member_uid::UUID)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("watcher_station_uid", identity.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("watcher_member_uid", identity.memberUid(), StandardValueConverter.UUID_STRING))
                .insert();
    }

    public void addWatcher(int ticketId, MemberIdentity identity) {
        if (identity == null || identity.stationUid() == null || identity.memberUid() == null) return;
        query("""
                INSERT INTO board_ticket_watcher(ticket_id, watcher_station_uid, watcher_member_uid)
                VALUES (:ticket_id, :watcher_station_uid::UUID, :watcher_member_uid::UUID)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("watcher_station_uid", identity.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("watcher_member_uid", identity.memberUid(), StandardValueConverter.UUID_STRING))
                .insert();
    }

    public boolean removeWatcher(int ticketId, MemberIdentity identity) {
        if (identity == null || identity.stationUid() == null || identity.memberUid() == null) return false;
        return query("""
                DELETE FROM board_ticket_watcher
                WHERE ticket_id = :ticket_id AND watcher_station_uid = :watcher_station_uid::UUID AND watcher_member_uid = :watcher_member_uid::UUID;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("watcher_station_uid", identity.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("watcher_member_uid", identity.memberUid(), StandardValueConverter.UUID_STRING))
                .delete()
                .changed();
    }

    public boolean removeWatcher(int ticketId, int memberId) {
        MemberIdentity identity = stationMemberRepository.resolveIdentity(memberId);
        if (identity == null) return false;
        return query("""
                DELETE FROM board_ticket_watcher
                WHERE ticket_id = :ticket_id AND watcher_station_uid = :watcher_station_uid::UUID AND watcher_member_uid = :watcher_member_uid::UUID;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("watcher_station_uid", identity.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("watcher_member_uid", identity.memberUid(), StandardValueConverter.UUID_STRING))
                .delete()
                .changed();
    }

    // -- Weblinks --

    public boolean isWatching(int ticketId, int memberId) {
        MemberIdentity identity = stationMemberRepository.resolveIdentity(memberId);
        if (identity == null) return false;
        return SqlSupport.exists(
                """
                SELECT 1 FROM board_ticket_watcher
                WHERE ticket_id = :ticket_id AND watcher_station_uid = :watcher_station_uid::UUID AND watcher_member_uid = :watcher_member_uid::UUID;""",
                call().bind("ticket_id", ticketId)
                        .bind("watcher_station_uid", identity.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("watcher_member_uid", identity.memberUid(), StandardValueConverter.UUID_STRING));
    }

    public List<BoardWeblink> findWeblinks(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_weblink WHERE ticket_id = :ticket_id ORDER BY position;",
                        WEBLINK_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardWeblink.map())
                .all();
    }

    public BoardWeblink createWeblink(int ticketId, String url, String title, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_ticket_weblink(ticket_id, url, title, position)
                VALUES (:ticket_id, :url, :title, :position)
                RETURNING %s;""",
                call().bind("ticket_id", ticketId)
                        .bind("url", url)
                        .bind("title", title)
                        .bind("position", position),
                BoardWeblink.map(),
                WEBLINK_COLUMNS);
    }

    // -- Attachments --

    public boolean deleteWeblink(int id) {
        return SqlSupport.deleteById("board_ticket_weblink", id);
    }

    public List<BoardTicketAttachment> findAttachments(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_attachment WHERE ticket_id = :ticket_id ORDER BY created_at;",
                        ATTACHMENT_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketAttachment.map())
                .all();
    }

    public BoardTicketAttachment createAttachment(
            int ticketId,
            String filename,
            String originalName,
            String contentType,
            long sizeBytes,
            MemberIdentity uploader) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_ticket_attachment(
                    ticket_id, filename, original_name, content_type, size_bytes,
                    uploader_station_uid, uploader_member_uid)
                VALUES (:ticket_id, :filename, :original_name, :content_type, :size_bytes,
                    :uploader_station_uid::UUID, :uploader_member_uid::UUID)
                RETURNING %s;""",
                call().bind("ticket_id", ticketId)
                        .bind("filename", filename)
                        .bind("original_name", originalName)
                        .bind("content_type", contentType)
                        .bind("size_bytes", sizeBytes)
                        .bind("uploader_station_uid", uploader.stationUid(), StandardValueConverter.UUID_STRING)
                        .bind("uploader_member_uid", uploader.memberUid(), StandardValueConverter.UUID_STRING),
                BoardTicketAttachment.map(),
                ATTACHMENT_COLUMNS);
    }

    public Optional<BoardTicketAttachment> findAttachmentById(int id) {
        return SqlSupport.findById("board_ticket_attachment", ATTACHMENT_COLUMNS, id, BoardTicketAttachment.map());
    }

    // -- Search --

    public boolean deleteAttachment(int id) {
        return SqlSupport.deleteById("board_ticket_attachment", id);
    }

    public List<BoardTicket> search(int boardId, String searchQuery) {
        return query("""
                %s
                WHERE t.board_id = :board_id
                  AND t.search_vector @@ to_tsquery('german', :tsquery)
                ORDER BY ts_rank(t.search_vector, to_tsquery('german', :tsquery)) DESC, t.position
                LIMIT 10;""", TICKET_SELECT)
                .single(call().bind("board_id", boardId).bind("tsquery", preparePrefixQuery(searchQuery)))
                .map(BoardTicket.map())
                .all();
    }

    // -- Field values --

    public List<BoardTicketFieldValue> findFieldValues(int ticketId) {
        return query("""
                SELECT fv.ticket_id, fv.field_id, f.field_type, fv.value
                FROM board_ticket_field_value fv
                JOIN board_field f ON f.id = fv.field_id
                WHERE fv.ticket_id = :ticket_id;""")
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketFieldValue.map())
                .all();
    }

    public void setFieldValue(int ticketId, int fieldId, BoardFieldValue value) {
        query("""
                INSERT INTO board_ticket_field_value(ticket_id, field_id, value)
                VALUES (:ticket_id, :field_id, :value::JSONB)
                ON CONFLICT (ticket_id, field_id) DO UPDATE SET value = :value::JSONB;""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("field_id", fieldId)
                        .bind("value", value == null ? null : value.toJson()))
                .insert();
    }

    public boolean deleteFieldValue(int ticketId, int fieldId) {
        return query("DELETE FROM board_ticket_field_value WHERE ticket_id = :ticket_id AND field_id = :field_id;")
                .single(call().bind("ticket_id", ticketId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    // -- KB Links --

    public List<BoardTicketKbLink> findKbLinks(int ticketId) {
        return query("""
                WITH RECURSIVE folder_path AS (
                    SELECT id, name, parent_id, name::TEXT AS path FROM kb_folder WHERE parent_id IS NULL
                    UNION ALL
                    SELECT c.id, c.name, c.parent_id, fp.path || ' / ' || c.name FROM kb_folder c JOIN folder_path fp ON fp.id = c.parent_id
                )
                SELECT l.id, l.ticket_id, l.kb_file_id, f.name AS title, coalesce(fp.path, '') AS folder_path
                FROM board_ticket_kb_link l JOIN kb_file f ON f.id = l.kb_file_id LEFT JOIN folder_path fp ON fp.id = f.folder_id
                WHERE l.ticket_id = :ticket_id ORDER BY f.name;""")
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketKbLink.map())
                .all();
    }

    public BoardTicketKbLink addKbLink(int ticketId, int kbFileId) {
        return query("""
                INSERT INTO board_ticket_kb_link(ticket_id, kb_file_id) VALUES (:ticket_id, :kb_file_id)
                ON CONFLICT DO NOTHING RETURNING id, ticket_id, kb_file_id, (SELECT name FROM kb_file WHERE id = :kb_file_id) AS title, '' AS folder_path;""")
                .single(call().bind("ticket_id", ticketId).bind("kb_file_id", kbFileId))
                .map(BoardTicketKbLink.map())
                .first()
                .orElse(null);
    }

    public boolean removeKbLink(int id) {
        return SqlSupport.deleteById("board_ticket_kb_link", id);
    }

    // -- History --

    public void logHistory(int ticketId, BoardTicketHistoryAction action, String detail, MemberIdentity actor) {
        query("""
                INSERT INTO board_ticket_history(ticket_id, action, detail, actor_station_uid, actor_member_uid)
                VALUES (:ticket_id, :action, :detail, :actor_station_uid::UUID, :actor_member_uid::UUID)""")
                .single(call().bind("ticket_id", ticketId)
                        .bind("action", action)
                        .bind("detail", detail)
                        .bind(
                                "actor_station_uid",
                                actor != null ? actor.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "actor_member_uid",
                                actor != null ? actor.memberUid() : null,
                                StandardValueConverter.UUID_STRING))
                .insert();
    }

    public List<BoardTicketHistory> findHistory(int ticketId) {
        return query(
                        "SELECT %s FROM board_ticket_history WHERE ticket_id = :ticket_id ORDER BY created_at;",
                        HISTORY_COLUMNS)
                .single(call().bind("ticket_id", ticketId))
                .map(BoardTicketHistory.map())
                .all();
    }

    // -- Activity feed --

    public List<ActivityEntry> findActivity(int ticketId) {
        return query("""
                SELECT 'COMMENT' AS type, id, created_at AS ts FROM board_ticket_comment WHERE ticket_id = :ticket_id AND NOT deleted
                UNION ALL
                SELECT 'TRANSITION' AS type, id, moved_at AS ts FROM board_ticket_transition WHERE ticket_id = :ticket_id
                UNION ALL
                SELECT 'HISTORY' AS type, id, created_at AS ts FROM board_ticket_history WHERE ticket_id = :ticket_id
                ORDER BY ts;""")
                .single(call().bind("ticket_id", ticketId))
                .map(row -> new ActivityEntry(
                        row.getEnum("type", BoardActivityType.class),
                        row.getInt("id"),
                        row.get("ts", StandardValueConverter.INSTANT_TIMESTAMP)))
                .all();
    }

    public record ActivityEntry(BoardActivityType type, int id, Instant timestamp) {}
}

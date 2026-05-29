/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
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
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class BoardTicketRepository {

    private static final String TICKET_SELECT = """
            SELECT t.*,
                COALESCE((SELECT count(*) FROM board_ticket_checklist_item WHERE ticket_id = t.id), 0) AS checklist_total,
                COALESCE((SELECT count(*) FROM board_ticket_checklist_item WHERE ticket_id = t.id AND checked = true), 0) AS checklist_checked,
                COALESCE((SELECT count(*) FROM board_ticket_attachment WHERE ticket_id = t.id), 0) AS attachment_count
            FROM board_ticket t""";

    // -- Ticket CRUD --

    public List<BoardTicket> findByBoard(int boardId) {
        return Query.query(TICKET_SELECT + " WHERE t.board_id = :board_id ORDER BY t.position;")
                .single(Call.of().bind("board_id", boardId))
                .map(BoardTicket.map())
                .all();
    }

    public List<BoardTicket> findByBoardAndLane(int boardId, int laneId) {
        return Query.query(
                        TICKET_SELECT + " WHERE t.board_id = :board_id AND t.lane_id = :lane_id ORDER BY t.position;")
                .single(Call.of().bind("board_id", boardId).bind("lane_id", laneId))
                .map(BoardTicket.map())
                .all();
    }

    public Optional<BoardTicket> findById(int id) {
        return Query.query(TICKET_SELECT + " WHERE t.id = :id;")
                .single(Call.of().bind("id", id))
                .map(BoardTicket.map())
                .first();
    }

    public Optional<BoardTicket> findByBoardAndNumber(int boardId, int ticketNumber) {
        return Query.query(TICKET_SELECT + " WHERE t.board_id = :board_id AND t.ticket_number = :ticket_number;")
                .single(Call.of().bind("board_id", boardId).bind("ticket_number", ticketNumber))
                .map(BoardTicket.map())
                .first();
    }

    public List<BoardTicket> findByAssignee(int boardId, int memberId) {
        return Query.query(TICKET_SELECT
                        + " WHERE t.board_id = :board_id AND t.assigned_member_id = :member_id ORDER BY t.position;")
                .single(Call.of().bind("board_id", boardId).bind("member_id", memberId))
                .map(BoardTicket.map())
                .all();
    }

    public BoardTicket createTicket(
            int boardId,
            int laneId,
            int ticketNumber,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate,
            int position,
            int createdBy) {
        return Query.query("""
                        WITH ins AS (
                            INSERT INTO board_ticket(board_id, lane_id, ticket_number, title, description,
                                assigned_member_id, priority, due_date, position, created_by)
                            VALUES (:board_id, :lane_id, :ticket_number, :title, :description,
                                :assigned_member_id, :priority, :due_date, :position, :created_by)
                            RETURNING *
                        )
                        SELECT ins.*,
                            0 AS checklist_total,
                            0 AS checklist_checked,
                            0 AS attachment_count
                        FROM ins;""")
                .single(Call.of()
                        .bind("board_id", boardId)
                        .bind("lane_id", laneId)
                        .bind("ticket_number", ticketNumber)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("assigned_member_id", assignedMemberId)
                        .bind("priority", priority.name())
                        .bind("due_date", dueDate)
                        .bind("position", position)
                        .bind("created_by", createdBy))
                .map(BoardTicket.map())
                .first()
                .orElseThrow();
    }

    public boolean updateTicket(
            int id,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate) {
        return Query.query("""
                        UPDATE board_ticket SET title = :title, description = :description,
                            assigned_member_id = :assigned_member_id, priority = :priority,
                            due_date = :due_date, updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("assigned_member_id", assignedMemberId)
                        .bind("priority", priority.name())
                        .bind("due_date", dueDate))
                .update()
                .changed();
    }

    public boolean assignTicket(int id, Integer assignedMemberId) {
        return Query.query(
                        "UPDATE board_ticket SET assigned_member_id = :assigned_member_id, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("assigned_member_id", assignedMemberId))
                .update()
                .changed();
    }

    public boolean deleteTicket(int id) {
        return Query.query("DELETE FROM board_ticket WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean moveTicket(int ticketId, int toLaneId, int position) {
        return Query.query("""
                        UPDATE board_ticket SET lane_id = :lane_id, position = :position,
                            lane_entered_at = now(), updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of().bind("id", ticketId).bind("lane_id", toLaneId).bind("position", position))
                .update()
                .changed();
    }

    public void setLaneEnteredAt(int ticketId, Instant laneEnteredAt) {
        Query.query("UPDATE board_ticket SET lane_entered_at = :entered WHERE id = :id;")
                .single(Call.of()
                        .bind("id", ticketId)
                        .bind("entered", laneEnteredAt, StandardValueConverter.INSTANT_TIMESTAMP))
                .update();
    }

    public void reorderTickets(int laneId, List<Integer> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Query.query("UPDATE board_ticket SET position = :position WHERE id = :id AND lane_id = :lane_id;")
                    .single(Call.of()
                            .bind("id", orderedIds.get(i))
                            .bind("position", i)
                            .bind("lane_id", laneId))
                    .update();
        }
    }

    // -- Ticket links --

    public List<BoardTicketLink> findLinks(int ticketId) {
        var stored = Query.query(
                        "SELECT ticket_id, linked_ticket_id, link_type FROM board_ticket_link WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketLink.map())
                .all();
        var reverse = Query.query(
                        "SELECT linked_ticket_id AS ticket_id, ticket_id AS linked_ticket_id, link_type FROM board_ticket_link WHERE linked_ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(row -> new BoardTicketLink(
                        row.getInt("ticket_id"),
                        row.getInt("linked_ticket_id"),
                        LinkType.valueOf(row.getString("link_type")).inverse()))
                .all();
        var result = new ArrayList<>(stored);
        result.addAll(reverse);
        return result;
    }

    public void createLink(int ticketId, int linkedTicketId, LinkType linkType) {
        Query.query("""
                     INSERT INTO board_ticket_link(ticket_id, linked_ticket_id, link_type)
                     VALUES (:ticket_id, :linked_ticket_id, :link_type)
                     ON CONFLICT DO NOTHING;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("linked_ticket_id", linkedTicketId)
                        .bind("link_type", linkType.name()))
                .insert();
    }

    public boolean deleteLink(int ticketId, int linkedTicketId) {
        boolean deleted = Query.query(
                        "DELETE FROM board_ticket_link WHERE ticket_id = :ticket_id AND linked_ticket_id = :linked_ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId).bind("linked_ticket_id", linkedTicketId))
                .delete()
                .changed();
        if (!deleted) {
            deleted = Query.query(
                            "DELETE FROM board_ticket_link WHERE ticket_id = :linked_ticket_id AND linked_ticket_id = :ticket_id;")
                    .single(Call.of().bind("ticket_id", ticketId).bind("linked_ticket_id", linkedTicketId))
                    .delete()
                    .changed();
        }
        return deleted;
    }

    // -- Transitions --

    public void logTransition(int ticketId, int fromLaneId, int toLaneId, int movedBy) {
        logTransition(ticketId, fromLaneId, toLaneId, movedBy, null, null);
    }

    public void logTransition(
            int ticketId,
            Integer fromLaneId,
            Integer toLaneId,
            Integer movedBy,
            Integer federatedPartnerId,
            UUID federatedMemberId) {
        Query.query("""
                     INSERT INTO board_ticket_transition(ticket_id, from_lane_id, to_lane_id, moved_by, federated_partner_id, federated_member_id)
                     VALUES (:ticket_id, :from_lane_id, :to_lane_id, :moved_by, :federated_partner_id, :federated_member_id::uuid);""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("from_lane_id", fromLaneId)
                        .bind("to_lane_id", toLaneId)
                        .bind("moved_by", movedBy)
                        .bind("federated_partner_id", federatedPartnerId)
                        .bind("federated_member_id", federatedMemberId, StandardValueConverter.UUID_STRING))
                .insert();
    }

    public List<BoardTicketTransition> findTransitions(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_transition WHERE ticket_id = :ticket_id ORDER BY moved_at;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketTransition.map())
                .all();
    }

    // -- Checklist --

    public List<BoardChecklistItem> findChecklistItems(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_checklist_item WHERE ticket_id = :ticket_id ORDER BY position;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardChecklistItem.map())
                .all();
    }

    public BoardChecklistItem createChecklistItem(int ticketId, String title, int position) {
        return Query.query("""
                        INSERT INTO board_ticket_checklist_item(ticket_id, title, position)
                        VALUES (:ticket_id, :title, :position)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("title", title)
                        .bind("position", position))
                .map(BoardChecklistItem.map())
                .first()
                .orElseThrow();
    }

    public boolean updateChecklistItem(int id, String title, boolean checked) {
        return Query.query("UPDATE board_ticket_checklist_item SET title = :title, checked = :checked WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("title", title).bind("checked", checked))
                .update()
                .changed();
    }

    public boolean deleteChecklistItem(int id) {
        return Query.query("DELETE FROM board_ticket_checklist_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void reorderChecklistItems(int ticketId, List<Integer> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Query.query(
                            "UPDATE board_ticket_checklist_item SET position = :position WHERE id = :id AND ticket_id = :ticket_id;")
                    .single(Call.of()
                            .bind("id", orderedIds.get(i))
                            .bind("position", i)
                            .bind("ticket_id", ticketId))
                    .update();
        }
    }

    // -- Comments --

    public List<BoardComment> findComments(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_comment WHERE ticket_id = :ticket_id ORDER BY created_at;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardComment.map())
                .all();
    }

    public BoardComment createComment(int ticketId, Integer parentId, Integer authorId, String content) {
        return Query.query("""
                        INSERT INTO board_ticket_comment(ticket_id, parent_id, author_id, content)
                        VALUES (:ticket_id, :parent_id, :author_id, :content)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("parent_id", parentId)
                        .bind("author_id", authorId)
                        .bind("content", content))
                .map(BoardComment.map())
                .first()
                .orElseThrow();
    }

    public boolean updateComment(int id, String content) {
        return Query.query("UPDATE board_ticket_comment SET content = :content, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("content", content))
                .update()
                .changed();
    }

    public boolean softDeleteComment(int id) {
        return Query.query("UPDATE board_ticket_comment SET deleted = true, content = '' WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    // -- Watchers --

    public List<Integer> findWatchers(int ticketId) {
        return Query.query("SELECT member_id FROM board_ticket_watcher WHERE ticket_id = :ticket_id;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    public void addWatcher(int ticketId, int memberId) {
        Query.query("""
                     INSERT INTO board_ticket_watcher(ticket_id, member_id)
                     VALUES (:ticket_id, :member_id)
                     ON CONFLICT DO NOTHING;""")
                .single(Call.of().bind("ticket_id", ticketId).bind("member_id", memberId))
                .insert();
    }

    public boolean removeWatcher(int ticketId, int memberId) {
        return Query.query("DELETE FROM board_ticket_watcher WHERE ticket_id = :ticket_id AND member_id = :member_id;")
                .single(Call.of().bind("ticket_id", ticketId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    public boolean isWatching(int ticketId, int memberId) {
        return Query.query(
                        "SELECT 1 FROM board_ticket_watcher WHERE ticket_id = :ticket_id AND member_id = :member_id;")
                .single(Call.of().bind("ticket_id", ticketId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    // -- Weblinks --

    public List<BoardWeblink> findWeblinks(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_weblink WHERE ticket_id = :ticket_id ORDER BY position;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardWeblink.map())
                .all();
    }

    public BoardWeblink createWeblink(int ticketId, String url, String title, int position) {
        return Query.query("""
                        INSERT INTO board_ticket_weblink(ticket_id, url, title, position)
                        VALUES (:ticket_id, :url, :title, :position)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("url", url)
                        .bind("title", title)
                        .bind("position", position))
                .map(BoardWeblink.map())
                .first()
                .orElseThrow();
    }

    public boolean deleteWeblink(int id) {
        return Query.query("DELETE FROM board_ticket_weblink WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Attachments --

    public List<BoardTicketAttachment> findAttachments(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_attachment WHERE ticket_id = :ticket_id ORDER BY created_at;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketAttachment.map())
                .all();
    }

    public BoardTicketAttachment createAttachment(
            int ticketId, String filename, String originalName, String contentType, long sizeBytes, int uploadedBy) {
        return Query.query("""
                        INSERT INTO board_ticket_attachment(ticket_id, filename, original_name, content_type, size_bytes, uploaded_by)
                        VALUES (:ticket_id, :filename, :original_name, :content_type, :size_bytes, :uploaded_by)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("filename", filename)
                        .bind("original_name", originalName)
                        .bind("content_type", contentType)
                        .bind("size_bytes", sizeBytes)
                        .bind("uploaded_by", uploadedBy))
                .map(BoardTicketAttachment.map())
                .first()
                .orElseThrow();
    }

    public Optional<BoardTicketAttachment> findAttachmentById(int id) {
        return Query.query("SELECT * FROM board_ticket_attachment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(BoardTicketAttachment.map())
                .first();
    }

    public boolean deleteAttachment(int id) {
        return Query.query("DELETE FROM board_ticket_attachment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Search --

    public List<BoardTicket> search(int boardId, String query) {
        String tsq = "to_tsquery('german', :tsquery)";
        return Query.query(TICKET_SELECT + " WHERE t.board_id = :board_id AND t.search_vector @@ " + tsq
                        + " ORDER BY ts_rank(t.search_vector, " + tsq + ") DESC, t.position LIMIT 10;")
                .single(Call.of().bind("board_id", boardId).bind("tsquery", preparePrefixQuery(query)))
                .map(BoardTicket.map())
                .all();
    }

    private static String preparePrefixQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> w.replaceAll("[^\\w\\p{L}]", "") + ":*")
                .collect(Collectors.joining(" & "));
    }

    // -- Field values --

    public List<BoardTicketFieldValue> findFieldValues(int ticketId) {
        return Query.query("""
                        SELECT fv.ticket_id, fv.field_id, f.field_type, fv.value
                        FROM board_ticket_field_value fv
                        JOIN board_field f ON f.id = fv.field_id
                        WHERE fv.ticket_id = :ticket_id;""")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketFieldValue.map())
                .all();
    }

    public void setFieldValue(int ticketId, int fieldId, String valueJson) {
        Query.query("""
                        INSERT INTO board_ticket_field_value(ticket_id, field_id, value)
                        VALUES (:ticket_id, :field_id, :value::JSONB)
                        ON CONFLICT (ticket_id, field_id) DO UPDATE SET value = :value::JSONB;""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("field_id", fieldId)
                        .bind("value", valueJson))
                .insert();
    }

    public boolean deleteFieldValue(int ticketId, int fieldId) {
        return Query.query(
                        "DELETE FROM board_ticket_field_value WHERE ticket_id = :ticket_id AND field_id = :field_id;")
                .single(Call.of().bind("ticket_id", ticketId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    // -- KB Links --

    public List<BoardTicketKbLink> findKbLinks(int ticketId) {
        return Query.query("""
                        WITH RECURSIVE folder_path AS (
                            SELECT id, name, parent_id, name::text AS path FROM kb_folder WHERE parent_id IS NULL
                            UNION ALL
                            SELECT c.id, c.name, c.parent_id, fp.path || ' / ' || c.name FROM kb_folder c JOIN folder_path fp ON fp.id = c.parent_id
                        )
                        SELECT l.id, l.ticket_id, l.kb_file_id, f.name AS title, COALESCE(fp.path, '') AS folder_path
                        FROM board_ticket_kb_link l JOIN kb_file f ON f.id = l.kb_file_id LEFT JOIN folder_path fp ON fp.id = f.folder_id
                        WHERE l.ticket_id = :ticket_id ORDER BY f.name;""")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketKbLink.map())
                .all();
    }

    public BoardTicketKbLink addKbLink(int ticketId, int kbFileId) {
        return Query.query("""
                        INSERT INTO board_ticket_kb_link(ticket_id, kb_file_id) VALUES (:ticket_id, :kb_file_id)
                        ON CONFLICT DO NOTHING RETURNING id, ticket_id, kb_file_id, (SELECT name FROM kb_file WHERE id = :kb_file_id) AS title, '' AS folder_path;""")
                .single(Call.of().bind("ticket_id", ticketId).bind("kb_file_id", kbFileId))
                .map(BoardTicketKbLink.map())
                .first()
                .orElse(null);
    }

    public boolean removeKbLink(int id) {
        return Query.query("DELETE FROM board_ticket_kb_link WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- History --

    public void logHistory(int ticketId, String action, String detail, int actorMemberId) {
        logHistory(ticketId, action, detail, actorMemberId, null, null);
    }

    public void logHistory(
            int ticketId,
            String action,
            String detail,
            Integer actorMemberId,
            Integer federatedPartnerId,
            UUID federatedMemberId) {
        Query.query("""
                        INSERT INTO board_ticket_history(ticket_id, action, detail, actor_member_id, federated_partner_id, federated_member_id)
                        VALUES (:ticket_id, :action, :detail, :actor, :federated_partner_id, :federated_member_id::uuid)""")
                .single(Call.of()
                        .bind("ticket_id", ticketId)
                        .bind("action", action)
                        .bind("detail", detail)
                        .bind("actor", actorMemberId)
                        .bind("federated_partner_id", federatedPartnerId)
                        .bind("federated_member_id", federatedMemberId, StandardValueConverter.UUID_STRING))
                .insert();
    }

    public List<BoardTicketHistory> findHistory(int ticketId) {
        return Query.query("SELECT * FROM board_ticket_history WHERE ticket_id = :ticket_id ORDER BY created_at;")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(BoardTicketHistory.map())
                .all();
    }

    // -- Activity feed --

    public record ActivityEntry(String type, int id, Instant timestamp) {}

    public List<ActivityEntry> findActivity(int ticketId) {
        return Query.query("""
                        SELECT 'comment' AS type, id, created_at AS ts FROM board_ticket_comment WHERE ticket_id = :ticket_id AND NOT deleted
                        UNION ALL
                        SELECT 'transition' AS type, id, moved_at AS ts FROM board_ticket_transition WHERE ticket_id = :ticket_id
                        UNION ALL
                        SELECT 'history' AS type, id, created_at AS ts FROM board_ticket_history WHERE ticket_id = :ticket_id
                        ORDER BY ts;""")
                .single(Call.of().bind("ticket_id", ticketId))
                .map(row -> new ActivityEntry(
                        row.getString("type"),
                        row.getInt("id"),
                        row.get("ts", StandardValueConverter.INSTANT_TIMESTAMP)))
                .all();
    }
}

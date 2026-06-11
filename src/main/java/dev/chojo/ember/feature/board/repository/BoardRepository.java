/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.repository;

import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class BoardRepository {

    // -- Board CRUD --

    public List<Board> findByStation(int stationId) {
        return query("SELECT * FROM board WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(call().bind("station_id", stationId))
                .map(Board.map())
                .all();
    }

    public Optional<Board> findById(int id) {
        return query("SELECT * FROM board WHERE id = :id;")
                .single(call().bind("id", id))
                .map(Board.map())
                .first();
    }

    public Optional<Board> findByShortKey(int stationId, String shortKey) {
        return query("SELECT * FROM board WHERE station_id = :station_id AND short_key = :short_key;")
                .single(call().bind("station_id", stationId).bind("short_key", shortKey))
                .map(Board.map())
                .first();
    }

    public Board create(int stationId, String name, String description, String shortKey) {
        return query("""
                        INSERT INTO board(station_id, name, description, short_key)
                        VALUES (:station_id, :name, :description, :short_key)
                        RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("short_key", shortKey))
                .map(Board.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String description, int hideDoneAfterDays) {
        return query("""
                        UPDATE board SET name = :name, description = :description,
                            hide_done_after_days = :hide_done_after_days
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("hide_done_after_days", hideDoneAfterDays))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM board WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public int nextTicketNumber(int boardId) {
        return query("UPDATE board SET ticket_counter = ticket_counter + 1 WHERE id = :id RETURNING ticket_counter;")
                .single(call().bind("id", boardId))
                .map(row -> row.getInt("ticket_counter"))
                .first()
                .orElseThrow();
    }

    // -- Backlog --

    public BoardLane enableBacklog(int boardId) {
        var existing = findById(boardId).orElseThrow();
        if (existing.backlogLaneId() != null) {
            return findLaneById(existing.backlogLaneId()).orElseThrow();
        }
        var lane = createLane(boardId, "Backlog", "#6b7280", -1);
        query("UPDATE board SET backlog_lane_id = :lane_id WHERE id = :id;")
                .single(call().bind("lane_id", lane.id()).bind("id", boardId))
                .update();
        return lane;
    }

    public void disableBacklog(int boardId) {
        var existing = findById(boardId).orElseThrow();
        if (existing.backlogLaneId() == null) return;
        query("UPDATE board SET backlog_lane_id = NULL WHERE id = :id;")
                .single(call().bind("id", boardId))
                .update();
        deleteLane(existing.backlogLaneId());
    }

    public Optional<BoardLane> findLaneById(int laneId) {
        return query("SELECT * FROM board_lane WHERE id = :id;")
                .single(call().bind("id", laneId))
                .map(BoardLane.map())
                .first();
    }

    // -- Lane CRUD --

    public List<BoardLane> findLanes(int boardId) {
        return query("SELECT * FROM board_lane WHERE board_id = :board_id ORDER BY position;")
                .single(call().bind("board_id", boardId))
                .map(BoardLane.map())
                .all();
    }

    public BoardLane createLane(int boardId, String name, String color, int position) {
        return query("""
                        INSERT INTO board_lane(board_id, name, color, position)
                        VALUES (:board_id, :name, :color, :position)
                        RETURNING *;""")
                .single(call().bind("board_id", boardId)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("position", position))
                .map(BoardLane.map())
                .first()
                .orElseThrow();
    }

    public boolean updateLane(int id, String name, String color, int position) {
        return query("UPDATE board_lane SET name = :name, color = :color, position = :position WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("position", position))
                .update()
                .changed();
    }

    public void moveTicketsFromLane(int fromLaneId, int toLaneId) {
        query("UPDATE board_ticket SET lane_id = :to_id WHERE lane_id = :from_id;")
                .single(call().bind("from_id", fromLaneId).bind("to_id", toLaneId))
                .update();
    }

    public boolean deleteLane(int id) {
        return query("DELETE FROM board_lane WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public void deleteAllLanes(int boardId) {
        query("DELETE FROM board_lane WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
    }

    // -- Label CRUD --

    public List<BoardLabel> findLabels(int boardId) {
        return query("SELECT * FROM board_label WHERE board_id = :board_id ORDER BY name;")
                .single(call().bind("board_id", boardId))
                .map(BoardLabel.map())
                .all();
    }

    public BoardLabel createLabel(int boardId, String name, String color) {
        return query("""
                        INSERT INTO board_label(board_id, name, color) VALUES (:board_id, :name, :color) RETURNING *;""")
                .single(call().bind("board_id", boardId).bind("name", name).bind("color", color))
                .map(BoardLabel.map())
                .first()
                .orElseThrow();
    }

    public boolean updateLabel(int id, String name, String color) {
        return query("UPDATE board_label SET name = :name, color = :color WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name).bind("color", color))
                .update()
                .changed();
    }

    public boolean deleteLabel(int id) {
        return query("DELETE FROM board_label WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public List<BoardLabel> findLabelsForTicket(int ticketId) {
        return query("""
                        SELECT l.* FROM board_label l JOIN board_ticket_label tl ON tl.label_id = l.id
                        WHERE tl.ticket_id = :ticket_id ORDER BY l.name;""")
                .single(call().bind("ticket_id", ticketId))
                .map(BoardLabel.map())
                .all();
    }

    public void addLabelToTicket(int ticketId, int labelId) {
        query(
                        "INSERT INTO board_ticket_label(ticket_id, label_id) VALUES (:ticket_id, :label_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("ticket_id", ticketId).bind("label_id", labelId))
                .insert();
    }

    public boolean removeLabelFromTicket(int ticketId, int labelId) {
        return query("DELETE FROM board_ticket_label WHERE ticket_id = :ticket_id AND label_id = :label_id;")
                .single(call().bind("ticket_id", ticketId).bind("label_id", labelId))
                .delete()
                .changed();
    }

    public List<TicketLabelMapping> findAllTicketLabels(int boardId) {
        return query("""
                        SELECT tl.ticket_id, tl.label_id FROM board_ticket_label tl
                        JOIN board_ticket t ON t.id = tl.ticket_id
                        WHERE t.board_id = :board_id;""")
                .single(call().bind("board_id", boardId))
                .map(row -> new TicketLabelMapping(row.getInt("ticket_id"), row.getInt("label_id")))
                .all();
    }

    // -- Field CRUD --

    public List<BoardField> findFields(int boardId) {
        return query("SELECT * FROM board_field WHERE board_id = :board_id ORDER BY position;")
                .single(call().bind("board_id", boardId))
                .map(BoardField.map())
                .all();
    }

    public BoardField createField(
            int boardId, String name, BoardFieldType fieldType, BoardFieldConfig config, int position) {
        return query("""
                        INSERT INTO board_field(board_id, name, field_type, config, position)
                        VALUES (:board_id, :name, :field_type, :config::JSONB, :position)
                        RETURNING *;""")
                .single(call().bind("board_id", boardId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position))
                .map(BoardField.map())
                .first()
                .orElseThrow();
    }

    public void deleteAllFields(int boardId) {
        query("DELETE FROM board_field WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
    }

    // -- Access restrictions --

    public void setViewAccess(int boardId, List<String> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        query("DELETE FROM board_view_access WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
        for (String userType : userTypes) {
            query("INSERT INTO board_view_access(board_id, user_type) VALUES (:board_id, :user_type);")
                    .single(call().bind("board_id", boardId).bind("user_type", userType))
                    .insert();
        }
        for (int groupId : groupIds) {
            query("INSERT INTO board_view_access(board_id, group_id) VALUES (:board_id, :group_id);")
                    .single(call().bind("board_id", boardId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            query("INSERT INTO board_view_access(board_id, tag_id) VALUES (:board_id, :tag_id);")
                    .single(call().bind("board_id", boardId).bind("tag_id", tagId))
                    .insert();
        }
    }

    public void setEditAccess(int boardId, List<String> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        query("DELETE FROM board_edit_access WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
        for (String userType : userTypes) {
            query("INSERT INTO board_edit_access(board_id, user_type) VALUES (:board_id, :user_type);")
                    .single(call().bind("board_id", boardId).bind("user_type", userType))
                    .insert();
        }
        for (int groupId : groupIds) {
            query("INSERT INTO board_edit_access(board_id, group_id) VALUES (:board_id, :group_id);")
                    .single(call().bind("board_id", boardId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            query("INSERT INTO board_edit_access(board_id, tag_id) VALUES (:board_id, :tag_id);")
                    .single(call().bind("board_id", boardId).bind("tag_id", tagId))
                    .insert();
        }
    }

    public List<String> findViewAccessUserTypes(int boardId) {
        return query("SELECT user_type FROM board_view_access WHERE board_id = :board_id AND user_type IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getString("user_type"))
                .all();
    }

    public List<Integer> findViewAccessGroupIds(int boardId) {
        return query("SELECT group_id FROM board_view_access WHERE board_id = :board_id AND group_id IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    public List<Integer> findViewAccessTagIds(int boardId) {
        return query("SELECT tag_id FROM board_view_access WHERE board_id = :board_id AND tag_id IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getInt("tag_id"))
                .all();
    }

    public List<String> findEditAccessUserTypes(int boardId) {
        return query("SELECT user_type FROM board_edit_access WHERE board_id = :board_id AND user_type IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getString("user_type"))
                .all();
    }

    public List<Integer> findEditAccessGroupIds(int boardId) {
        return query("SELECT group_id FROM board_edit_access WHERE board_id = :board_id AND group_id IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    public List<Integer> findEditAccessTagIds(int boardId) {
        return query("SELECT tag_id FROM board_edit_access WHERE board_id = :board_id AND tag_id IS NOT NULL;")
                .single(call().bind("board_id", boardId))
                .map(row -> row.getInt("tag_id"))
                .all();
    }

    public boolean hasViewRestrictions(int boardId) {
        return query("SELECT 1 FROM board_view_access WHERE board_id = :board_id LIMIT 1;")
                .single(call().bind("board_id", boardId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public boolean hasEditRestrictions(int boardId) {
        return query("SELECT 1 FROM board_edit_access WHERE board_id = :board_id LIMIT 1;")
                .single(call().bind("board_id", boardId))
                .map(row -> true)
                .first()
                .isPresent();
    }
}

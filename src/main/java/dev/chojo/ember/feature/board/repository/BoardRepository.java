/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class BoardRepository {

    private static final String BOARD_COLUMNS =
            "id, uid, station_id, name, description, short_key, hide_done_after_days, ticket_counter, backlog_lane_id, created_at";
    private static final String LANE_COLUMNS = "id, board_id, name, color, position";
    private static final String LABEL_COLUMNS = "id, board_id, name, color";
    private static final String FIELD_COLUMNS = "id, board_id, name, field_type, config, position";
    private static final String VIEW_ACCESS_TABLE = "board_view_access";
    private static final String EDIT_ACCESS_TABLE = "board_edit_access";
    private static final String BOARD_FK = "board_id";

    // -- Board CRUD --

    public List<Board> findByStation(int stationId) {
        return query("SELECT %s FROM board WHERE station_id = :station_id ORDER BY created_at DESC;", BOARD_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(Board.map())
                .all();
    }

    public Optional<Board> findById(int id) {
        return SqlSupport.findById("board", BOARD_COLUMNS, id, Board.map());
    }

    public Optional<Board> findByShortKey(int stationId, String shortKey) {
        return query("SELECT %s FROM board WHERE station_id = :station_id AND short_key = :short_key;", BOARD_COLUMNS)
                .single(call().bind("station_id", stationId).bind("short_key", shortKey))
                .map(Board.map())
                .first();
    }

    public Board create(int stationId, String name, String description, String shortKey) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board(station_id, name, description, short_key)
                VALUES (:station_id, :name, :description, :short_key)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("short_key", shortKey),
                Board.map(),
                BOARD_COLUMNS);
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
        return SqlSupport.deleteById("board", id);
    }

    public int nextTicketNumber(int boardId) {
        return SqlSupport.insertReturning(
                "UPDATE board SET ticket_counter = ticket_counter + 1 WHERE id = :id RETURNING ticket_counter;",
                call().bind("id", boardId),
                row -> row.getInt("ticket_counter"));
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
        return SqlSupport.findById("board_lane", LANE_COLUMNS, laneId, BoardLane.map());
    }

    // -- Lane CRUD --

    public List<BoardLane> findLanes(int boardId) {
        return query("SELECT %s FROM board_lane WHERE board_id = :board_id ORDER BY position;", LANE_COLUMNS)
                .single(call().bind("board_id", boardId))
                .map(BoardLane.map())
                .all();
    }

    public BoardLane createLane(int boardId, String name, String color, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_lane(board_id, name, color, position)
                VALUES (:board_id, :name, :color, :position)
                RETURNING %s;""",
                call().bind("board_id", boardId)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("position", position),
                BoardLane.map(),
                LANE_COLUMNS);
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

    public void deleteLane(int id) {
        SqlSupport.deleteById("board_lane", id);
    }

    public void deleteAllLanes(int boardId) {
        query("DELETE FROM board_lane WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
    }

    // -- Label CRUD --

    public List<BoardLabel> findLabels(int boardId) {
        return query("SELECT %s FROM board_label WHERE board_id = :board_id ORDER BY name;", LABEL_COLUMNS)
                .single(call().bind("board_id", boardId))
                .map(BoardLabel.map())
                .all();
    }

    public BoardLabel createLabel(int boardId, String name, String color) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO board_label(board_id, name, color) VALUES (:board_id, :name, :color) RETURNING %s;""",
                call().bind("board_id", boardId).bind("name", name).bind("color", color),
                BoardLabel.map(),
                LABEL_COLUMNS);
    }

    public boolean updateLabel(int id, String name, String color) {
        return query("UPDATE board_label SET name = :name, color = :color WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name).bind("color", color))
                .update()
                .changed();
    }

    public boolean deleteLabel(int id) {
        return SqlSupport.deleteById("board_label", id);
    }

    public List<BoardLabel> findLabelsForTicket(int ticketId) {
        return query("""
                SELECT %s FROM board_label l JOIN board_ticket_label tl ON tl.label_id = l.id
                WHERE tl.ticket_id = :ticket_id ORDER BY l.name;""", SqlSupport.alias("l", LABEL_COLUMNS))
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
        return query("SELECT %s FROM board_field WHERE board_id = :board_id ORDER BY position;", FIELD_COLUMNS)
                .single(call().bind("board_id", boardId))
                .map(BoardField.map())
                .all();
    }

    public void createField(int boardId, String name, BoardFieldType fieldType, BoardFieldConfig config, int position) {
        SqlSupport.insertReturning(
                """
                INSERT INTO board_field(board_id, name, field_type, config, position)
                VALUES (:board_id, :name, :field_type, :config::JSONB, :position)
                RETURNING %s;""",
                call().bind("board_id", boardId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position),
                BoardField.map(),
                FIELD_COLUMNS);
    }

    public void deleteAllFields(int boardId) {
        query("DELETE FROM board_field WHERE board_id = :board_id;")
                .single(call().bind("board_id", boardId))
                .delete();
    }

    // -- Access restrictions --

    public void setViewAccess(
            int boardId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        RestrictionSql.replace(VIEW_ACCESS_TABLE, BOARD_FK, boardId, accessSelection(userTypes, groupIds, tagIds));
    }

    public void setEditAccess(
            int boardId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        RestrictionSql.replace(EDIT_ACCESS_TABLE, BOARD_FK, boardId, accessSelection(userTypes, groupIds, tagIds));
    }

    /**
     * Board access tables carry no per-member rows, so the selection never names members.
     */
    private static RestrictionSelection accessSelection(
            List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        return new RestrictionSelection(userTypes, groupIds, tagIds, List.of(), null);
    }

    public List<StationUserType> findViewAccessUserTypes(int boardId) {
        return findAccessUserTypes(VIEW_ACCESS_TABLE, boardId);
    }

    public List<Integer> findViewAccessGroupIds(int boardId) {
        return findAccessIds(VIEW_ACCESS_TABLE, "group_id", boardId);
    }

    public List<Integer> findViewAccessTagIds(int boardId) {
        return findAccessIds(VIEW_ACCESS_TABLE, "tag_id", boardId);
    }

    public List<StationUserType> findEditAccessUserTypes(int boardId) {
        return findAccessUserTypes(EDIT_ACCESS_TABLE, boardId);
    }

    public List<Integer> findEditAccessGroupIds(int boardId) {
        return findAccessIds(EDIT_ACCESS_TABLE, "group_id", boardId);
    }

    public List<Integer> findEditAccessTagIds(int boardId) {
        return findAccessIds(EDIT_ACCESS_TABLE, "tag_id", boardId);
    }

    private static List<StationUserType> findAccessUserTypes(String table, int boardId) {
        return query("SELECT user_type FROM %s WHERE board_id = :board_id AND user_type IS NOT NULL;", table)
                .single(call().bind("board_id", boardId))
                .map(row -> row.getEnum("user_type", StationUserType.class))
                .all();
    }

    private static List<Integer> findAccessIds(String table, String column, int boardId) {
        return query("SELECT %1$s FROM %2$s WHERE board_id = :board_id AND %1$s IS NOT NULL;", column, table)
                .single(call().bind("board_id", boardId))
                .map(row -> row.getInt(column))
                .all();
    }

    public boolean hasViewRestrictions(int boardId) {
        return RestrictionSql.hasAny(VIEW_ACCESS_TABLE, BOARD_FK, boardId);
    }

    public boolean hasEditRestrictions(int boardId) {
        return RestrictionSql.hasAny(EDIT_ACCESS_TABLE, BOARD_FK, boardId);
    }
}

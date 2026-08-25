/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.repository;

import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import jakarta.inject.Singleton;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.alias;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.deleteByIdInStation;
import static dev.chojo.ember.util.sql.SqlSupport.findById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class TestProtocolRepository {
    private static final String TEST_PROTOCOL_COLUMNS =
            "id, station_id, name, description, pass_threshold, created_at, updated_at";
    private static final String TEST_PROTOCOL_SECTION_COLUMNS =
            "id, protocol_id, parent_id, name, description, max_points, pass_threshold, position";
    private static final String TEST_PROTOCOL_ITEM_COLUMNS = "id, section_id, label, description, points, position";
    private static final String TEST_PROTOCOL_RUN_COLUMNS =
            "id, protocol_id, station_id, name, test_date, status, created_by, created_at";
    private static final String TEST_PROTOCOL_RUN_MEMBER_COLUMNS =
            "id, run_id, member_id, locked_by, locked_at, completed, total_score";
    private static final String TEST_PROTOCOL_RUN_CHECK_COLUMNS =
            "run_member_id, item_id, checked, checked_by, checked_at";

    // -- Protocols --

    public List<TestProtocol> findProtocols(int stationId) {
        return query(
                        "SELECT %s FROM test_protocol WHERE station_id = :station_id ORDER BY name;",
                        TEST_PROTOCOL_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(TestProtocol.map())
                .all();
    }

    public List<TestProtocol> searchProtocols(int stationId, String query) {
        var pattern = "%" + query + "%";
        return query("""
                SELECT %s
                FROM
                    test_protocol
                WHERE station_id = :station_id
                  AND ( name ILIKE :q OR description ILIKE :q )
                ORDER BY name;""", TEST_PROTOCOL_COLUMNS)
                .single(call().bind("station_id", stationId).bind("q", pattern))
                .map(TestProtocol.map())
                .all();
    }

    public Optional<TestProtocol> findProtocolById(int id) {
        return findById("test_protocol", TEST_PROTOCOL_COLUMNS, id, TestProtocol.map());
    }

    public TestProtocol createProtocol(int stationId, String name, String description, Integer passThreshold) {
        return insertReturning(
                """
                INSERT INTO test_protocol(station_id, name, description, pass_threshold)
                VALUES (:station_id, :name, :description, :pass_threshold)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("pass_threshold", passThreshold),
                TestProtocol.map(),
                TEST_PROTOCOL_COLUMNS);
    }

    public boolean updateProtocol(int id, String name, String description, Integer passThreshold) {
        return query("""
                UPDATE test_protocol
                SET
                    name           = :name,
                    description    = :description,
                    pass_threshold = :pass_threshold,
                    updated_at     = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("pass_threshold", passThreshold))
                .update()
                .changed();
    }

    /**
     * Deletes a protocol of the given station, cascading to its sections, items and runs. The
     * station is part of the statement, so a caller that forgets to check whose protocol it is
     * removes nothing rather than another station's work.
     */
    public boolean deleteProtocol(int id, int stationId) {
        return deleteByIdInStation("test_protocol", id, stationId);
    }

    // -- Sections --

    public List<TestProtocolSection> findSections(int protocolId) {
        return query("""
                SELECT %s
                FROM
                    test_protocol_section
                WHERE protocol_id = :protocol_id
                ORDER BY position, name;""", TEST_PROTOCOL_SECTION_COLUMNS)
                .single(call().bind("protocol_id", protocolId))
                .map(TestProtocolSection.map())
                .all();
    }

    public TestProtocolSection createSection(
            int protocolId,
            Integer parentId,
            String name,
            String description,
            Integer maxPoints,
            Integer passThreshold,
            int position) {
        return insertReturning(
                """
                INSERT INTO test_protocol_section(protocol_id, parent_id, name, description, max_points, pass_threshold, position)
                VALUES (:protocol_id, :parent_id, :name, :description, :max_points, :pass_threshold, :position)
                RETURNING %s;""",
                call().bind("protocol_id", protocolId)
                        .bind("parent_id", parentId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("max_points", maxPoints)
                        .bind("pass_threshold", passThreshold)
                        .bind("position", position),
                TestProtocolSection.map(),
                TEST_PROTOCOL_SECTION_COLUMNS);
    }

    public boolean updateSection(
            int id, String name, String description, Integer maxPoints, Integer passThreshold, int position) {
        return query("""
                UPDATE test_protocol_section SET name = :name, description = :description,
                max_points = :max_points, pass_threshold = :pass_threshold, position = :position WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("max_points", maxPoints)
                        .bind("pass_threshold", passThreshold)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteSection(int id) {
        return deleteById("test_protocol_section", id);
    }

    /**
     * The station owning the protocol a section belongs to. Sections carry no station of their own,
     * so this is what an ownership check on a section endpoint compares against.
     */
    public Optional<Integer> findSectionStation(int sectionId) {
        return query("""
                SELECT p.station_id
                FROM
                    test_protocol_section s
                    JOIN test_protocol p ON p.id = s.protocol_id
                WHERE s.id = :id;""")
                .single(call().bind("id", sectionId))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    // -- Items --

    public List<TestProtocolItem> findItems(int sectionId) {
        return query(
                        "SELECT %s FROM test_protocol_item WHERE section_id = :section_id ORDER BY position;",
                        TEST_PROTOCOL_ITEM_COLUMNS)
                .single(call().bind("section_id", sectionId))
                .map(TestProtocolItem.map())
                .all();
    }

    public List<TestProtocolItem> findAllItemsByProtocol(int protocolId) {
        return query("""
                SELECT %s FROM test_protocol_item i
                JOIN test_protocol_section s ON s.id = i.section_id
                WHERE s.protocol_id = :protocol_id ORDER BY s.position, i.position;""", alias("i", TEST_PROTOCOL_ITEM_COLUMNS))
                .single(call().bind("protocol_id", protocolId))
                .map(TestProtocolItem.map())
                .all();
    }

    public TestProtocolItem createItem(int sectionId, String label, String description, double points, int position) {
        return insertReturning(
                """
                INSERT INTO test_protocol_item(section_id, label, description, points, position)
                VALUES (:section_id, :label, :description, :points, :position) RETURNING %s;""",
                call().bind("section_id", sectionId)
                        .bind("label", label)
                        .bind("description", description)
                        .bind("points", points)
                        .bind("position", position),
                TestProtocolItem.map(),
                TEST_PROTOCOL_ITEM_COLUMNS);
    }

    public boolean updateItem(int id, String label, String description, double points, int position) {
        return query("""
                UPDATE test_protocol_item
                SET
                    label       = :label,
                    description = :description,
                    points      = :points,
                    position    = :position
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("label", label)
                        .bind("description", description)
                        .bind("points", points)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteItem(int id) {
        return deleteById("test_protocol_item", id);
    }

    /**
     * The station owning the protocol an item belongs to, reached through its section.
     */
    public Optional<Integer> findItemStation(int itemId) {
        return query("""
                SELECT p.station_id
                FROM
                    test_protocol_item i
                    JOIN test_protocol_section s ON s.id = i.section_id
                    JOIN test_protocol p ON p.id = s.protocol_id
                WHERE i.id = :id;""")
                .single(call().bind("id", itemId))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    // -- Runs --

    public List<TestProtocolRun> findRuns(int stationId) {
        return query(
                        "SELECT %s FROM test_protocol_run WHERE station_id = :station_id ORDER BY created_at DESC;",
                        TEST_PROTOCOL_RUN_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(TestProtocolRun.map())
                .all();
    }

    public Optional<TestProtocolRun> findRunById(int id) {
        return findById("test_protocol_run", TEST_PROTOCOL_RUN_COLUMNS, id, TestProtocolRun.map());
    }

    public TestProtocolRun createRun(int protocolId, int stationId, String name, LocalDate testDate, int createdBy) {
        return insertReturning(
                """
                INSERT INTO test_protocol_run(protocol_id, station_id, name, test_date, created_by)
                VALUES (:protocol_id, :station_id, :name, :test_date, :created_by) RETURNING %s;""",
                call().bind("protocol_id", protocolId)
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("test_date", Date.valueOf(testDate))
                        .bind("created_by", createdBy),
                TestProtocolRun.map(),
                TEST_PROTOCOL_RUN_COLUMNS);
    }

    public boolean updateRun(int id, String name, LocalDate testDate) {
        return query("UPDATE test_protocol_run SET name = :name, test_date = :test_date WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name).bind("test_date", Date.valueOf(testDate)))
                .update()
                .changed();
    }

    public boolean closeRun(int id) {
        return query("UPDATE test_protocol_run SET status = 'CLOSED' WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteRun(int id, int stationId) {
        return deleteByIdInStation("test_protocol_run", id, stationId);
    }

    // -- Run Members --

    public List<TestProtocolRunMember> findRunMembers(int runId) {
        return query(
                        "SELECT %s FROM test_protocol_run_member WHERE run_id = :run_id ORDER BY member_id;",
                        TEST_PROTOCOL_RUN_MEMBER_COLUMNS)
                .single(call().bind("run_id", runId))
                .map(TestProtocolRunMember.map())
                .all();
    }

    public Optional<TestProtocolRunMember> findRunMember(int runId, int memberId) {
        return query(
                        "SELECT %s FROM test_protocol_run_member WHERE run_id = :run_id AND member_id = :member_id;",
                        TEST_PROTOCOL_RUN_MEMBER_COLUMNS)
                .single(call().bind("run_id", runId).bind("member_id", memberId))
                .map(TestProtocolRunMember.map())
                .first();
    }

    public TestProtocolRunMember addRunMember(int runId, int memberId) {
        return query("""
                INSERT INTO test_protocol_run_member(run_id, member_id)
                VALUES (:run_id, :member_id)
                ON CONFLICT (run_id, member_id) DO NOTHING
                RETURNING %s;""", TEST_PROTOCOL_RUN_MEMBER_COLUMNS)
                .single(call().bind("run_id", runId).bind("member_id", memberId))
                .map(TestProtocolRunMember.map())
                .first()
                .orElseGet(() -> findRunMember(runId, memberId).orElseThrow());
    }

    public boolean lockMember(int runMemberId, int lockedBy) {
        return query("""

                UPDATE test_protocol_run_member
                        SET
                            locked_by = :locked_by,
                            locked_at = :locked_at
                        WHERE id = :id
                          AND ( locked_by IS NULL OR locked_by = :locked_by );""")
                .single(call().bind("id", runMemberId)
                        .bind("locked_by", lockedBy)
                        .bind("locked_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean unlockMember(int runMemberId) {
        return query("UPDATE test_protocol_run_member SET locked_by = NULL, locked_at = NULL WHERE id = :id;")
                .single(call().bind("id", runMemberId))
                .update()
                .changed();
    }

    public void updateScore(int runMemberId, double score) {
        query("UPDATE test_protocol_run_member SET total_score = :score WHERE id = :id;")
                .single(call().bind("id", runMemberId).bind("score", score))
                .update();
    }

    public boolean completeMember(int runMemberId, double totalScore) {
        return query("""
                UPDATE test_protocol_run_member
                        SET
                            completed   = TRUE,
                            total_score = :total_score,
                            locked_by   = NULL,
                            locked_at   = NULL
                        WHERE id = :id;""")
                .single(call().bind("id", runMemberId).bind("total_score", totalScore))
                .update()
                .changed();
    }

    // -- Section Done --

    public List<Integer> findDoneSections(int runMemberId) {
        return query("SELECT section_id FROM test_protocol_run_section_done WHERE run_member_id = :run_member_id;")
                .single(call().bind("run_member_id", runMemberId))
                .map(row -> row.getInt("section_id"))
                .all();
    }

    public void markSectionDone(int runMemberId, int sectionId, int doneBy) {
        query("""
                INSERT
                INTO
                    test_protocol_run_section_done(run_member_id, section_id, done_by)
                VALUES
                    (:run_member_id, :section_id, :done_by)
                ON CONFLICT (run_member_id, section_id) DO NOTHING;""")
                .single(call().bind("run_member_id", runMemberId)
                        .bind("section_id", sectionId)
                        .bind("done_by", doneBy))
                .insert();
    }

    public void unmarkSectionDone(int runMemberId, int sectionId) {
        query("""
                DELETE
                FROM
                    test_protocol_run_section_done
                WHERE run_member_id = :run_member_id
                  AND section_id = :section_id;""")
                .single(call().bind("run_member_id", runMemberId).bind("section_id", sectionId))
                .delete();
    }

    public int countDoneSections(int runMemberId) {
        return count(
                "SELECT count(*) AS cnt FROM test_protocol_run_section_done WHERE run_member_id = :run_member_id;",
                call().bind("run_member_id", runMemberId));
    }

    public List<TestProtocolRunMember> findCompletedRunMembers(int runId) {
        return query(
                        "SELECT %s FROM test_protocol_run_member WHERE run_id = :run_id AND completed ORDER BY member_id;",
                        TEST_PROTOCOL_RUN_MEMBER_COLUMNS)
                .single(call().bind("run_id", runId))
                .map(TestProtocolRunMember.map())
                .all();
    }

    // -- Checks --

    public List<TestProtocolRunCheck> findChecks(int runMemberId) {
        return query(
                        "SELECT %s FROM test_protocol_run_check WHERE run_member_id = :run_member_id;",
                        TEST_PROTOCOL_RUN_CHECK_COLUMNS)
                .single(call().bind("run_member_id", runMemberId))
                .map(TestProtocolRunCheck.map())
                .all();
    }

    public void upsertCheck(int runMemberId, int itemId, boolean checked, int checkedBy) {
        query("""
                INSERT
                INTO
                    test_protocol_run_check(run_member_id, item_id, checked, checked_by, checked_at)
                VALUES
                    (:run_member_id, :item_id, :checked, :checked_by, :checked_at)
                ON CONFLICT (run_member_id, item_id)
                    DO UPDATE
                    SET
                        checked    = :checked,
                        checked_by = :checked_by,
                        checked_at = :checked_at;""")
                .single(call().bind("run_member_id", runMemberId)
                        .bind("item_id", itemId)
                        .bind("checked", checked)
                        .bind("checked_by", checkedBy)
                        .bind("checked_at", Instant.now(), INSTANT_TIMESTAMP))
                .insert();
    }
}

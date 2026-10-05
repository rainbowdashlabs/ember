/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunExaminer;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** Who examines which section of a test run. */
@Singleton
public class TestProtocolExaminerRepository {

    public List<TestProtocolRunExaminer> findByRun(int runId) {
        return query("""
                SELECT run_id, section_id, member_id
                FROM test_protocol_run_examiner
                WHERE run_id = :run_id
                ORDER BY section_id, member_id;""")
                .single(call().bind("run_id", runId))
                .map(TestProtocolRunExaminer.map())
                .all();
    }

    /**
     * Replaces the examiners of one section of a run.
     *
     * @param memberIds the examiners, an empty collection leaving the section without any
     */
    public void replaceForSection(int runId, int sectionId, Collection<Integer> memberIds) {
        query("DELETE FROM test_protocol_run_examiner WHERE run_id = :run_id AND section_id = :section_id;")
                .single(call().bind("run_id", runId).bind("section_id", sectionId))
                .delete();
        if (memberIds.isEmpty()) return;
        query("""
                INSERT INTO test_protocol_run_examiner(run_id, section_id, member_id)
                SELECT :run_id, :section_id, unnest(:member_ids)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("run_id", runId)
                        .bind("section_id", sectionId)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .insert();
    }

    public void deleteByRun(int runId) {
        query("DELETE FROM test_protocol_run_examiner WHERE run_id = :run_id;")
                .single(call().bind("run_id", runId))
                .delete();
    }

    /**
     * The most recent other run of the same protocol that has examiners, which is what a recurring
     * examination is planned from.
     */
    public Optional<Integer> findPreviousPlannedRun(int protocolId, int exceptRunId) {
        return query("""
                SELECT r.id
                FROM test_protocol_run r
                WHERE r.protocol_id = :protocol_id
                  AND r.id <> :except_run_id
                  AND EXISTS (SELECT 1 FROM test_protocol_run_examiner e WHERE e.run_id = r.id)
                ORDER BY r.test_date DESC, r.id DESC
                LIMIT 1;""")
                .single(call().bind("protocol_id", protocolId).bind("except_run_id", exceptRunId))
                .map(row -> row.getInt("id"))
                .first();
    }
}

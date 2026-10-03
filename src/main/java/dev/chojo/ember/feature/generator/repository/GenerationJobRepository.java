/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.GenerationJob;
import dev.chojo.ember.feature.generator.entity.GenerationJobMember;
import dev.chojo.ember.feature.generator.entity.JobMemberStatus;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Generation runs and their members, which is all a run needs to carry on after a restart: every member
 * still waiting is generated when the run is picked up again, and the ones done stay as they are.
 */
@Singleton
public class GenerationJobRepository {

    private static final String JOB = """
            SELECT j.id, j.station_id, j.template_id, t.name AS template_name, j.started_by, j.accept_missing,
                   j.issuer_id, j.issuer_function, j.issuer_fixed, j.started_at, j.finished_at,
                   count(m.member_id)::int                                AS total,
                   (count(m.member_id) FILTER (WHERE m.status = 'FILED'))::int  AS filed,
                   (count(m.member_id) FILTER (WHERE m.status = 'FAILED'))::int AS failed
            FROM document_generation_job j
                     JOIN document_template t ON t.id = j.template_id
                     LEFT JOIN document_generation_job_member m ON m.job_id = j.id""";

    /**
     * Writes a new run with its members, all waiting.
     *
     * @param stationId     the station the documents are filed at
     * @param templateId    the template
     * @param startedBy     the manager who starts it
     * @param acceptMissing whether a member with incomplete data still gets a document
     * @param issuer        who issues every document of the run
     * @param memberIds     the members in the order they are generated
     * @return the run's id
     */
    public int create(
            int stationId,
            int templateId,
            int startedBy,
            boolean acceptMissing,
            DocumentIssuer issuer,
            List<Integer> memberIds) {
        int jobId = query("""
                        INSERT INTO document_generation_job(station_id, template_id, started_by, accept_missing,
                                                            issuer_id, issuer_function, issuer_fixed)
                        VALUES (:station_id, :template_id, :started_by, :accept_missing,
                                :issuer_id, :issuer_function, :issuer_fixed)
                        RETURNING id;""")
                .single(call().bind("station_id", stationId)
                        .bind("template_id", templateId)
                        .bind("started_by", startedBy)
                        .bind("accept_missing", acceptMissing)
                        .bind("issuer_id", issuer.memberId())
                        .bind("issuer_function", issuer.function())
                        .bind("issuer_fixed", issuer.fixed()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("""
                INSERT INTO document_generation_job_member(job_id, member_id, position)
                SELECT :job_id, member_id, (position - 1)::int
                FROM unnest(:member_ids::INT[]) WITH ORDINALITY AS chosen(member_id, position);""")
                .single(call().bind("job_id", jobId).bind("member_ids", memberIds, PostgreSqlTypes.INTEGER))
                .insert();
        return jobId;
    }

    /**
     * @param jobId the run
     * @return the run with how far it got, or empty where there is none
     */
    public Optional<GenerationJob> find(int jobId) {
        return query("""
                %s
                WHERE j.id = :id
                GROUP BY j.id, t.name;""", JOB)
                .single(call().bind("id", jobId))
                .map(GenerationJob.map())
                .first();
    }

    /**
     * The latest runs of a station, the newest first.
     *
     * @param stationId the station
     * @param limit     how many at most
     * @return the runs
     */
    public List<GenerationJob> recent(int stationId, int limit) {
        return query("""
                %s
                WHERE j.station_id = :station_id
                GROUP BY j.id, t.name
                ORDER BY j.started_at DESC, j.id DESC
                LIMIT :limit;""", JOB)
                .single(call().bind("station_id", stationId).bind("limit", limit))
                .map(GenerationJob.map())
                .all();
    }

    /**
     * @param jobId the run
     * @return every member of the run in the order they are generated, with how it went for them
     */
    public List<GenerationJobMember> members(int jobId) {
        return query("""
                SELECT m.member_id, m.status, g.document_id, m.refusal_code, m.refusal_detail
                FROM document_generation_job_member m
                         LEFT JOIN document_generation g ON g.id = m.generation_id
                WHERE m.job_id = :job_id
                ORDER BY m.position;""")
                .single(call().bind("job_id", jobId))
                .map(GenerationJobMember.map())
                .all();
    }

    /**
     * @param jobId the run
     * @return the members of the run still waiting, in the order they are generated
     */
    public List<Integer> waiting(int jobId) {
        return query("""
                SELECT member_id
                FROM document_generation_job_member
                WHERE job_id = :job_id
                  AND status = 'WAITING'
                ORDER BY position;""")
                .single(call().bind("job_id", jobId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * Records the document filed for a member of a run.
     *
     * @param jobId        the run
     * @param memberId     the member
     * @param generationId the entry of the document in the generation log
     */
    public void markFiled(int jobId, int memberId, int generationId) {
        mark(jobId, memberId, JobMemberStatus.FILED, generationId, null, null);
    }

    /**
     * Records why no document was filed for a member of a run.
     *
     * @param jobId    the run
     * @param memberId the member
     * @param code     the code of the refusal
     * @param detail   what the refusal was about, or null
     */
    public void markFailed(int jobId, int memberId, String code, @Nullable String detail) {
        mark(jobId, memberId, JobMemberStatus.FAILED, null, code, detail);
    }

    private void mark(
            int jobId,
            int memberId,
            JobMemberStatus status,
            @Nullable Integer generationId,
            @Nullable String code,
            @Nullable String detail) {
        query("""
                UPDATE document_generation_job_member
                SET status         = :status,
                    generation_id  = :generation_id,
                    refusal_code   = :code,
                    refusal_detail = :detail,
                    done_at        = now()
                WHERE job_id = :job_id
                  AND member_id = :member_id;""")
                .single(call().bind("job_id", jobId)
                        .bind("member_id", memberId)
                        .bind("status", status)
                        .bind("generation_id", generationId)
                        .bind("code", code)
                        .bind("detail", detail))
                .update();
    }

    /**
     * Closes a run whose members are all done.
     *
     * @param jobId the run
     */
    public void finish(int jobId) {
        query("""
                UPDATE document_generation_job
                SET finished_at = now()
                WHERE id = :id
                  AND finished_at IS NULL
                  AND NOT EXISTS (SELECT 1
                                  FROM document_generation_job_member
                                  WHERE job_id = :id
                                    AND status = 'WAITING');""").single(call().bind("id", jobId)).update();
    }

    /**
     * @return the runs that were started and are not finished, the oldest first
     */
    public List<Integer> unfinished() {
        return query("""
                SELECT id
                FROM document_generation_job
                WHERE finished_at IS NULL
                ORDER BY started_at, id;""").single(call()).map(row -> row.getInt("id")).all();
    }
}

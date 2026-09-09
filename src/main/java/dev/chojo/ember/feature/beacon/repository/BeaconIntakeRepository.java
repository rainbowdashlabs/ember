/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.repository;

import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What a beacon writes down when something reports to it.
 *
 * <p>Every write here is an upsert. A sender may report the same fault twice, and the count it
 * carries is its own running total rather than an increment, so arriving twice has to correct a row
 * instead of doubling it.
 */
@Singleton
public class BeaconIntakeRepository {

    /**
     * The key an instance is known by, if it has ever reported.
     *
     * @param instanceId the identifier computed from its key
     * @return the stored public key, if any
     */
    public Optional<String> knownKey(String instanceId) {
        return query("SELECT public_key FROM beacon_instance WHERE instance_id = :id;")
                .single(call().bind("id", instanceId))
                .map(row -> row.getString("public_key"))
                .first();
    }

    /**
     * Records that an instance has been heard from, and what it says about itself.
     *
     * <p>The contact is written on every report rather than only on the first, so an operator who
     * clears it in their configuration clears it here too.
     */
    public void touchInstance(
            String instanceId, String publicKey, String contactName, String contactMail, String version) {
        query("""
                        INSERT INTO beacon_instance (instance_id, public_key, contact_name, contact_mail, last_version)
                        VALUES (:id, :key, :name, :mail, :version)
                        ON CONFLICT (instance_id)
                            DO UPDATE SET contact_name = EXCLUDED.contact_name,
                                          contact_mail = EXCLUDED.contact_mail,
                                          last_version = EXCLUDED.last_version,
                                          last_seen    = now();""")
                .single(call().bind("id", instanceId)
                        .bind("key", publicKey)
                        .bind("name", contactName)
                        .bind("mail", contactMail)
                        .bind("version", version))
                .insert();
    }

    /**
     * Files a fault under its fingerprint and returns the row it belongs to.
     *
     * @return the fault's id
     */
    public int upsertProblem(BeaconPayloads.ProblemPayload payload) {
        query("""
                        INSERT INTO beacon_problem (fingerprint, level, logger, exception_class, frames, first_seen, last_seen)
                        VALUES (:fingerprint, :level, :logger, :exception, :frames, :first, :last)
                        ON CONFLICT (fingerprint)
                            DO UPDATE SET last_seen = greatest(beacon_problem.last_seen, EXCLUDED.last_seen);""")
                .single(call().bind("fingerprint", payload.fingerprint())
                        .bind("level", payload.level())
                        .bind("logger", payload.logger())
                        .bind("exception", payload.exceptionClass())
                        .bind("frames", payload.frames())
                        .bind("first", payload.firstOccurrence(), INSTANT_TIMESTAMP)
                        .bind("last", payload.lastOccurrence(), INSTANT_TIMESTAMP))
                .insert();
        return query("SELECT id FROM beacon_problem WHERE fingerprint = :fingerprint;")
                .single(call().bind("fingerprint", payload.fingerprint()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    /**
     * Records how one instance has met one fault.
     *
     * <p>The occurrence count is replaced rather than added to, because the sender's payload carries
     * its own total. A report that arrives twice therefore corrects this row.
     *
     * <p>The version is handed in already checked rather than read from the payload. It is rendered
     * on a screen beside the fault, so anything a sender writes there has to have passed the same
     * check the instance's own version passed.
     */
    public void upsertProblemInstance(
            int problemId, String instanceId, String version, BeaconPayloads.ProblemPayload payload) {
        query("""
                        INSERT INTO beacon_problem_instance (problem_id, instance_id, version, occurrences, first_seen, last_seen)
                        VALUES (:problem, :instance, :version, :count, :first, :last)
                        ON CONFLICT (problem_id, instance_id)
                            DO UPDATE SET version     = EXCLUDED.version,
                                          occurrences = EXCLUDED.occurrences,
                                          last_seen   = EXCLUDED.last_seen;""")
                .single(call().bind("problem", problemId)
                        .bind("instance", instanceId)
                        .bind("version", version)
                        .bind("count", payload.occurrences())
                        .bind("first", payload.firstOccurrence(), INSTANT_TIMESTAMP)
                        .bind("last", payload.lastOccurrence(), INSTANT_TIMESTAMP))
                .insert();
    }

    /** Stores somebody's own words, which do not group and are never corrected. */
    public void insertReport(String instanceId, BeaconPayloads.ReportPayload payload) {
        query("""
                        INSERT INTO beacon_report (instance_id, message, page, version, reported_at)
                        VALUES (:instance, :message, :page, :version, :reported);""")
                .single(call().bind("instance", instanceId)
                        .bind("message", payload.message())
                        .bind("page", payload.page())
                        .bind("version", payload.version())
                        .bind("reported", payload.reportedAt(), INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Stores one subject's day.
     *
     * <p>Last write for a day wins. There is no identity behind these rows to check, so an honest
     * report simply corrects whatever was there, which is the only defence available and is enough
     * for a number nobody is billed on.
     */
    public void upsertMetrics(BeaconPayloads.MetricsSubject subject, LocalDate day, String version) {
        query("""
                        INSERT INTO beacon_metrics (metrics_uid, subject, day, members, accounts, stations, inventory, version)
                        VALUES (:uid::uuid, :subject, :day, :members, :accounts, :stations, :inventory, :version)
                        ON CONFLICT (metrics_uid, day)
                            DO UPDATE SET members   = EXCLUDED.members,
                                          accounts  = EXCLUDED.accounts,
                                          stations  = EXCLUDED.stations,
                                          inventory = EXCLUDED.inventory,
                                          version   = EXCLUDED.version;""")
                .single(call().bind("uid", subject.metricsUid())
                        .bind("subject", subject.subject())
                        .bind("day", day)
                        .bind("members", subject.members())
                        .bind("accounts", subject.accounts())
                        .bind("stations", subject.stations())
                        .bind("inventory", subject.inventory())
                        .bind("version", version))
                .insert();
    }

    /**
     * Whether this delivery has been seen before.
     *
     * <p>Signing the body alone leaves a captured report replayable, and replayable straight into the
     * count of how many installations hit a fault. The nonce is what makes one delivery arrive once.
     *
     * @return true when the nonce was new and has now been recorded
     */
    public boolean recordNonce(String instanceId, String nonce, Instant issuedAt) {
        return query("""
                        INSERT INTO beacon_nonce (instance_id, nonce, issued_at)
                        VALUES (:instance, :nonce, :issued)
                        ON CONFLICT (instance_id, nonce) DO NOTHING;""")
                .single(call().bind("instance", instanceId)
                        .bind("nonce", nonce)
                        .bind("issued", issuedAt, INSTANT_TIMESTAMP))
                .insert()
                .changed();
    }

    /** Forgets nonces older than the drift window, which cannot be replayed anyway. */
    public void pruneNonces(Instant before) {
        query("DELETE FROM beacon_nonce WHERE issued_at < :before;")
                .single(call().bind("before", before, INSTANT_TIMESTAMP))
                .delete();
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.repository;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What a beacon shows whoever runs it.
 *
 * <p>The faults come out ordered by how many installations have hit them, because that is the whole
 * reason to gather them in one place: a fault thirty stations meet is a different thing from one that
 * happened once.
 */
@Singleton
public class BeaconReadRepository {

    /**
     * A fault as the overview lists it.
     *
     * @param instances how many installations have reported it
     * @param versions  the versions it has been seen in, newest first
     */
    public record FaultRow(
            int id,
            String fingerprint,
            String level,
            String exceptionClass,
            String logger,
            String frames,
            int instances,
            long occurrences,
            List<String> versions,
            Instant firstSeen,
            Instant lastSeen,
            boolean acknowledged,
            String resolvedIn) {}

    /** A forwarded report, with whoever can be written to about it. */
    public record ReportRow(
            int id,
            String message,
            String page,
            String version,
            String contactName,
            String contactMail,
            Instant reportedAt,
            boolean acknowledged) {}

    /** A day of one subject's bucketed counts. */
    public record MetricsRow(
            String metricsUid,
            String subject,
            String day,
            String members,
            String accounts,
            String stations,
            String inventory) {}

    /** The faults, most widely met first. */
    public List<FaultRow> faults(boolean includeAcknowledged) {
        return query("""
                        SELECT p.id, p.fingerprint, p.level, p.exception_class, p.logger, p.frames,
                               p.first_seen, p.last_seen, p.acknowledged, p.resolved_in,
                               count(pi.instance_id)                                     AS instances,
                               coalesce(sum(pi.occurrences), 0)                          AS occurrences,
                               coalesce(array_agg(DISTINCT pi.version) FILTER (WHERE pi.version IS NOT NULL), '{}') AS versions
                        FROM beacon_problem p
                                 LEFT JOIN beacon_problem_instance pi ON pi.problem_id = p.id
                        WHERE ( :include_acknowledged OR p.acknowledged = FALSE )
                        GROUP BY p.id
                        ORDER BY count(pi.instance_id) DESC, p.last_seen DESC;""")
                .single(call().bind("include_acknowledged", includeAcknowledged))
                .map(row -> new FaultRow(
                        row.getInt("id"),
                        row.getString("fingerprint"),
                        row.getString("level"),
                        row.getString("exception_class"),
                        row.getString("logger"),
                        row.getString("frames"),
                        row.getInt("instances"),
                        row.getLong("occurrences"),
                        List.of((String[]) row.getArray("versions").getArray()),
                        row.get("first_seen", INSTANT_TIMESTAMP),
                        row.get("last_seen", INSTANT_TIMESTAMP),
                        row.getBoolean("acknowledged"),
                        row.getString("resolved_in")))
                .all();
    }

    /** The forwarded reports, newest first. */
    public List<ReportRow> reports(boolean includeAcknowledged) {
        return query("""
                        SELECT r.id, r.message, r.page, r.version, r.reported_at, r.acknowledged,
                               i.contact_name, i.contact_mail
                        FROM beacon_report r
                                 JOIN beacon_instance i ON i.instance_id = r.instance_id
                        WHERE ( :include_acknowledged OR r.acknowledged = FALSE )
                        ORDER BY r.received_at DESC
                        LIMIT 500;""")
                .single(call().bind("include_acknowledged", includeAcknowledged))
                .map(row -> new ReportRow(
                        row.getInt("id"),
                        row.getString("message"),
                        row.getString("page"),
                        row.getString("version"),
                        row.getString("contact_name"),
                        row.getString("contact_mail"),
                        row.get("reported_at", INSTANT_TIMESTAMP),
                        row.getBoolean("acknowledged")))
                .all();
    }

    /** The numbers of the last so many days. */
    public List<MetricsRow> metrics(int days) {
        return query("""
                        SELECT metrics_uid, subject, day, members, accounts, stations, inventory
                        FROM beacon_metrics
                        WHERE day >= current_date - make_interval(days => :days)
                        ORDER BY day DESC;""")
                .single(call().bind("days", days))
                .map(row -> new MetricsRow(
                        row.getString("metrics_uid"),
                        row.getString("subject"),
                        row.getString("day"),
                        row.getString("members"),
                        row.getString("accounts"),
                        row.getString("stations"),
                        row.getString("inventory")))
                .all();
    }

    /** How many instances have ever reported. */
    /**
     * How many faults and how many forwarded reports nobody has looked at yet.
     *
     * <p>Two counts in one round trip, because the sidebar wants both and asking twice for two numbers a
     * page refreshes on a timer is two round trips more than it needs.
     *
     * @return the faults and the reports still waiting, in that order
     */
    public Waiting countWaiting() {
        return query("""
                        SELECT
                            (SELECT count(*) FROM beacon_problem WHERE acknowledged = FALSE) AS faults,
                            (SELECT count(*) FROM beacon_report WHERE acknowledged = FALSE)  AS reports;""")
                .single()
                .map(row -> new Waiting(row.getInt("faults"), row.getInt("reports")))
                .first()
                .orElse(new Waiting(0, 0));
    }

    /** What a beacon has waiting for whoever looks after it. */
    public record Waiting(int faults, int reports) {}

    public int instanceCount() {
        return query("SELECT count(*) AS c FROM beacon_instance;")
                .single()
                .map(row -> row.getInt("c"))
                .first()
                .orElse(0);
    }

    /** Marks a fault as seen, or says which version put it right. */
    public boolean resolveFault(int id, boolean acknowledged, String resolvedIn) {
        return query("UPDATE beacon_problem SET acknowledged = :ack, resolved_in = :version WHERE id = :id;")
                .single(call().bind("ack", acknowledged)
                        .bind("version", resolvedIn)
                        .bind("id", id))
                .update()
                .changed();
    }

    /** Marks a forwarded report as seen. */
    public boolean acknowledgeReport(int id) {
        return query("UPDATE beacon_report SET acknowledged = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }
}

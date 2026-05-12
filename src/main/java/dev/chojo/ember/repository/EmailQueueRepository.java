/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

@Singleton
public class EmailQueueRepository {

    public record QueuedEmail(int id, String recipient, String subject, String body) {}

    public void enqueue(String recipient, String subject, String body) {
        Query.query("INSERT INTO email_queue(recipient, subject, body) VALUES(:recipient, :subject, :body);")
                .single(Call.of()
                        .bind("recipient", recipient)
                        .bind("subject", subject)
                        .bind("body", body))
                .insert();
    }

    public List<QueuedEmail> fetchPending(int limit) {
        return Query.query("""
                        UPDATE email_queue SET status = 'SENDING'
                        WHERE id IN (
                            SELECT id FROM email_queue WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit
                        )
                        RETURNING id, recipient, subject, body;""")
                .single(Call.of().bind("limit", limit))
                .map(row -> new QueuedEmail(
                        row.getInt("id"), row.getString("recipient"), row.getString("subject"), row.getString("body")))
                .all();
    }

    public void markSent(int id) {
        Query.query("UPDATE email_queue SET status = 'SENT' WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update();
    }

    public void markFailed(int id) {
        Query.query("UPDATE email_queue SET status = 'FAILED' WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update();
    }

    public void requeue(int id) {
        Query.query("UPDATE email_queue SET status = 'PENDING' WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update();
    }

    public int pendingCount() {
        return Query.query("SELECT count(*) FROM email_queue WHERE status = 'PENDING';")
                .single()
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    public int getDailyCount(LocalDate day) {
        return Query.query("SELECT count FROM email_daily_count WHERE day = :day;")
                .single(Call.of().bind("day", day))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    public void incrementDailyCount(LocalDate day) {
        Query.query("""
                        INSERT INTO email_daily_count(day, count) VALUES(:day, 1)
                        ON CONFLICT (day) DO UPDATE SET count = email_daily_count.count + 1;""").single(Call.of().bind("day", day)).insert();
    }

    public void cleanupOldEntries(int keepDays) {
        Query.query(
                        "DELETE FROM email_queue WHERE status IN ('SENT', 'FAILED') AND created_at < now() - make_interval(days => :days);")
                .single(Call.of().bind("days", keepDays))
                .delete();
        Query.query("DELETE FROM email_daily_count WHERE day < :cutoff;")
                .single(Call.of().bind("cutoff", LocalDate.now().minusDays(keepDays)))
                .delete();
    }
}

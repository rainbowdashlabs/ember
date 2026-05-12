/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.Notification;
import dev.chojo.ember.entity.NotificationType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class NotificationRepository {

    public Notification create(int memberId, NotificationType type, Integer referenceId, String message) {
        return Query.query("""
                        INSERT INTO notification(member_id, type, reference_id, message)
                        VALUES(:member_id, :type, :reference_id, :message)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("type", type)
                        .bind("reference_id", referenceId)
                        .bind("message", message))
                .map(Notification.map())
                .first()
                .orElseThrow();
    }

    public List<Notification> findUnacknowledged(int memberId) {
        return Query.query(
                        "SELECT * FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL ORDER BY created_at DESC;")
                .single(Call.of().bind("member_id", memberId))
                .map(Notification.map())
                .all();
    }

    public List<Notification> findAll(int memberId) {
        return Query.query("SELECT * FROM notification WHERE member_id = :member_id ORDER BY created_at DESC LIMIT 50;")
                .single(Call.of().bind("member_id", memberId))
                .map(Notification.map())
                .all();
    }

    public int countUnacknowledged(int memberId) {
        return Query.query(
                        "SELECT COUNT(*) AS cnt FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL;")
                .single(Call.of().bind("member_id", memberId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public boolean acknowledge(int id, int memberId) {
        return Query.query(
                        "UPDATE notification SET acknowledged_at = :now WHERE id = :id AND member_id = :member_id AND acknowledged_at IS NULL;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("member_id", memberId)
                        .bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public int acknowledgeAll(int memberId) {
        return Query.query(
                        "UPDATE notification SET acknowledged_at = :now WHERE member_id = :member_id AND acknowledged_at IS NULL;")
                .single(Call.of().bind("member_id", memberId).bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .rows();
    }

    public void deleteOldAcknowledged() {
        Query.query(
                        "DELETE FROM notification WHERE acknowledged_at IS NOT NULL AND acknowledged_at < now() - INTERVAL '30 days';")
                .single()
                .delete();
    }
}

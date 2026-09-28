/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The ledger of reminders about expiry dates, {@code expiry_reminder_sent}.
 *
 * <p>A reminder is recorded by the day it was due, together with the member, the field and the date
 * it was about, so the same reminder never goes out twice and a new date starts over without anything
 * being cleaned up. A station's fields and an association's fields number themselves separately, so
 * every row also names whose field it is.
 */
@Singleton
public class ExpiryReminderRepository {

    /**
     * Every reminder already done with for one field.
     *
     * @param origin  whose field it is
     * @param fieldId the expiry date field
     * @return the reminders, whichever members and dates they were about
     */
    public List<SentExpiryReminder> findSent(FieldOrigin origin, int fieldId) {
        return query("""
                SELECT
                    member_id,
                    expires_on,
                    reminder_date,
                    sent_at
                FROM
                    expiry_reminder_sent
                WHERE field_origin = :field_origin
                  AND field_id = :field_id;""")
                .single(call().bind("field_origin", origin).bind("field_id", fieldId))
                .map(SentExpiryReminder.map())
                .all();
    }

    /**
     * Records reminders as done with. A reminder recorded already stays as it was.
     *
     * @param memberId      whose date they were about
     * @param origin        whose field it is
     * @param fieldId       the expiry date field
     * @param expiresOn     the last valid day they were about
     * @param reminderDates the days they were due on
     * @param sentAt        when they were done with
     */
    public void markSent(
            int memberId,
            FieldOrigin origin,
            int fieldId,
            LocalDate expiresOn,
            Collection<LocalDate> reminderDates,
            Instant sentAt) {
        query("""
                INSERT
                INTO
                    expiry_reminder_sent(member_id, field_origin, field_id, expires_on, reminder_date, sent_at)
                SELECT
                    :member_id,
                    :field_origin,
                    :field_id,
                    :expires_on,
                    due,
                    :sent_at
                FROM
                    unnest(:reminder_dates) AS due
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("member_id", memberId)
                        .bind("field_origin", origin)
                        .bind("field_id", fieldId)
                        .bind("expires_on", expiresOn)
                        .bind("reminder_dates", List.copyOf(reminderDates), PostgreSqlTypes.DATE)
                        .bind("sent_at", sentAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Clears the rows of fields that no longer exist. A row names its field without a foreign key,
     * because the field lives in one of two tables, so nothing else takes them away.
     *
     * @return how many rows were cleared
     */
    public int forgetDeletedFields() {
        return query("""
                DELETE
                FROM
                    expiry_reminder_sent sent
                WHERE (sent.field_origin = 'STATION'
                           AND NOT EXISTS (SELECT 1 FROM profile_field f WHERE f.id = sent.field_id))
                   OR (sent.field_origin = 'CLUSTER'
                           AND NOT EXISTS (SELECT 1 FROM cluster_profile_field f WHERE f.id = sent.field_id));""").single().delete().rows();
    }
}

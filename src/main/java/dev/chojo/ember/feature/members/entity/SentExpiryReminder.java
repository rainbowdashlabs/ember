/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A reminder about an expiry date that is done with. Which field it was about is known to whoever
 * asked for it, so the row carries only what differs between reminders of one field.
 *
 * @param memberId     whose date it was about
 * @param expiresOn    the last valid day it was about
 * @param reminderDate the day it was due on
 * @param sentAt       when it was recorded as done
 */
public record SentExpiryReminder(int memberId, LocalDate expiresOn, LocalDate reminderDate, Instant sentAt) {
    public static RowMapping<SentExpiryReminder> map() {
        return row -> new SentExpiryReminder(
                row.getInt("member_id"),
                row.getObject("expires_on", LocalDate.class),
                row.getObject("reminder_date", LocalDate.class),
                row.get("sent_at", INSTANT_TIMESTAMP));
    }
}

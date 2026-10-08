/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An open signature field whose signers are due a reminder.
 *
 * @param stationId     the station the request belongs to
 * @param pending       the field with its request and document
 * @param remindersSent how many reminders went out for it so far
 */
public record DueReminder(int stationId, PendingSignature pending, int remindersSent) {

    /**
     * Maps a row read like {@link PendingSignature#map()}, with the request's {@code request_station_id} and the
     * field's {@code field_reminders_sent}.
     */
    public static RowMapping<DueReminder> map() {
        RowMapping<PendingSignature> pending = PendingSignature.map();
        return row ->
                new DueReminder(row.getInt("request_station_id"), pending.map(row), row.getInt("field_reminders_sent"));
    }
}

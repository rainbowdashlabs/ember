/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

/**
 * The SQL that asks whether a registration says the member is not coming, for every read that counts
 * or lists who stays away.
 *
 * <p>A refusal always says so. A withdrawal only does on an appointment that has to be signed up for,
 * where it gives a place up. On one that expects everybody it is a refusal taken back, which leaves
 * the member expected again, so counting it as a refusal kept them away from the sheet, the
 * reminders and the totals after they had said they were coming after all.
 */
public final class RefusalSql {

    /**
     * The condition, for a query reading {@code event_registration} as {@code er} and its
     * {@code station_event} as {@code se}, that the registration says the member is not coming.
     */
    public static final String NOT_COMING = """
            (er.status = 'DECLINED' OR (er.status = 'WITHDRAWN' AND se.requires_registration))""";

    private RefusalSql() {}
}

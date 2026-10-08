/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.mail.entity.InstanceMailGrant;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Which stations may send their own mail through the instance's providers, and how much of it.
 */
@Singleton
public class InstanceMailGrantRepository {

    /**
     * The station's grant, or empty when it has none.
     */
    public Optional<InstanceMailGrant> find(int stationId) {
        return query("""
                SELECT
                    id, instance_mail_granted_at, instance_mail_daily_limit
                FROM
                    station
                WHERE
                    id = :id
                    AND instance_mail_granted_at IS NOT NULL;""")
                .single(call().bind("id", stationId))
                .map(row -> new InstanceMailGrant(
                        row.getInt("id"),
                        row.get("instance_mail_granted_at", INSTANT_TIMESTAMP),
                        row.getObject("instance_mail_daily_limit", Integer.class)))
                .first();
    }

    /**
     * Grants the station the instance's providers, or changes its daily limit where it already has
     * them. A grant that already stands keeps the moment it was first given.
     *
     * @param dailyLimit how many mails a day it may send through them, or null for no limit of its own
     */
    public void grant(int stationId, @Nullable Integer dailyLimit) {
        query("""
                UPDATE station
                SET
                    instance_mail_granted_at  = coalesce(instance_mail_granted_at, now()),
                    instance_mail_daily_limit = :daily_limit
                WHERE
                    id = :id;""")
                .single(call().bind("id", stationId).bind("daily_limit", dailyLimit))
                .update();
    }

    /**
     * Takes the instance's providers away from the station again, its limit with them.
     */
    public void withdraw(int stationId) {
        query("""
                UPDATE station
                SET
                    instance_mail_granted_at  = NULL,
                    instance_mail_daily_limit = NULL
                WHERE
                    id = :id;""").single(call().bind("id", stationId)).update();
    }
}

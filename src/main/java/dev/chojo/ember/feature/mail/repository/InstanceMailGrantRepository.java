/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.mail.entity.InstanceMailGrant;
import dev.chojo.ember.feature.mail.entity.InstanceMailStation;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * Which stations may send their own mail through the instance's providers, and how much of it.
 */
@Singleton
public class InstanceMailGrantRepository {

    private static final String STATION_QUERY = """
            SELECT
                s.id, s.uid, s.name, s.instance_mail_granted_at, s.instance_mail_daily_limit,
                (SELECT count(*)
                 FROM email_queue q
                 WHERE q.station_id = s.id
                   AND q.instance_position IS NOT NULL
                   AND q.sent_at >= :day_start
                   AND q.sent_at < :day_end) AS sent_today
            FROM
                station s
            WHERE
                %s
            ORDER BY
                lower(s.name), s.id;""";

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
     * Every station that sends mail of its own, granted or not, with what the instance's providers
     * sent for it today, by name.
     *
     * @param today the day whose mail is counted
     */
    public List<InstanceMailStation> stations(LocalDate today) {
        return query(STATION_QUERY, "s.station_kind = :kind")
                .single(call().bind("kind", StationKind.REGULAR)
                        .bind("day_start", today)
                        .bind("day_end", today.plusDays(1)))
                .map(InstanceMailGrantRepository::stationOf)
                .all();
    }

    /**
     * One station as {@link #stations(LocalDate)} lists it, or empty when there is no such station.
     *
     * @param today the day whose mail is counted
     */
    public Optional<InstanceMailStation> station(int stationId, LocalDate today) {
        return query(STATION_QUERY, "s.id = :id")
                .single(call().bind("id", stationId).bind("day_start", today).bind("day_end", today.plusDays(1)))
                .map(InstanceMailGrantRepository::stationOf)
                .first();
    }

    /**
     * How many stations may send through the instance's providers.
     */
    public int countGranted() {
        return count("SELECT count(*) FROM station WHERE instance_mail_granted_at IS NOT NULL;", call());
    }

    private static InstanceMailStation stationOf(Row row) throws SQLException {
        Instant grantedAt = row.get("instance_mail_granted_at", INSTANT_TIMESTAMP);
        return new InstanceMailStation(
                row.get("uid", UUID_STRING),
                row.getString("name"),
                grantedAt != null,
                grantedAt,
                row.getObject("instance_mail_daily_limit", Integer.class),
                row.getInt("sent_today"));
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

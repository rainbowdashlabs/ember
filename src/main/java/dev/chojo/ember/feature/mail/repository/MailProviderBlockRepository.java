/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Which provider a receiving domain refuses outright.
 *
 * <p>Only ever written from a report of BLOCKED, which is the receiving side saying it refused the
 * relay rather than the message. Everything else stays out: a hard bounce usually means the address
 * does not exist, and shutting a provider out of a whole domain over one bad address would cost far
 * more than the attempt it saves.
 */
@Singleton
public class MailProviderBlockRepository {

    /**
     * How long a block stands before it is tried again. A block list entry is not forever, and a
     * provider that has got itself removed from one should not be shut out by our memory of it.
     */
    private static final int BLOCK_DAYS = 7;

    /**
     * One provider a domain refuses.
     */
    public record ProviderBlock(
            MailProviderType provider,
            String recipientDomain,
            String reason,
            Instant firstBlockedAt,
            Instant lastBlockedAt,
            Instant expiresAt) {}

    private static final RowMapping<ProviderBlock> BLOCK = row -> new ProviderBlock(
            row.getEnum("provider", MailProviderType.class),
            row.getString("recipient_domain"),
            row.getString("reason"),
            row.get("first_blocked_at", INSTANT_TIMESTAMP),
            row.get("last_blocked_at", INSTANT_TIMESTAMP),
            row.get("expires_at", INSTANT_TIMESTAMP));

    /**
     * Records that this domain refused this provider, or pushes an existing block out.
     *
     * @param stationId the station whose list this concerns, or null for the instance list
     */
    public void block(Integer stationId, MailProviderType provider, String recipientDomain, String reason) {
        String domain = domainOf(recipientDomain);
        if (domain == null) return;
        query("""
                        INSERT
                        INTO
                            mail_provider_block(station_id, provider, recipient_domain, reason, expires_at)
                        VALUES
                            (:station_id, :provider, :domain, :reason, now() + make_interval(days => :days))
                        ON CONFLICT (coalesce(station_id, 0), provider, recipient_domain)
                        DO UPDATE SET
                            last_blocked_at = now(),
                            reason = excluded.reason,
                            expires_at = excluded.expires_at;""")
                .single(call().bind("station_id", stationId)
                        .bind("provider", provider.name())
                        .bind("domain", domain)
                        .bind("reason", reason)
                        .bind("days", BLOCK_DAYS))
                .insert();
    }

    /**
     * The providers this domain currently refuses, for one owner.
     */
    public Set<MailProviderType> blockedFor(Integer stationId, String recipient) {
        String domain = domainOf(recipient);
        if (domain == null) return Set.of();
        return query("""
                        SELECT
                            provider
                        FROM
                            mail_provider_block
                        WHERE
                            station_id IS NOT DISTINCT FROM CAST(:station_id AS INTEGER)
                            AND recipient_domain = :domain
                            AND expires_at > now();""")
                .single(call().bind("station_id", stationId).bind("domain", domain))
                .map(row -> row.getEnum("provider", MailProviderType.class))
                .all()
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * Every block still standing for one owner, newest refusal first.
     */
    public List<ProviderBlock> list(Integer stationId) {
        return query("""
                        SELECT
                            provider, recipient_domain, reason, first_blocked_at, last_blocked_at, expires_at
                        FROM
                            mail_provider_block
                        WHERE
                            station_id IS NOT DISTINCT FROM CAST(:station_id AS INTEGER)
                            AND expires_at > now()
                        ORDER BY
                            last_blocked_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(BLOCK)
                .all();
    }

    /**
     * Lifts a block by hand, for when an operator knows it has been sorted out.
     */
    public void lift(Integer stationId, MailProviderType provider, String recipientDomain) {
        query("""
                        DELETE FROM
                            mail_provider_block
                        WHERE
                            station_id IS NOT DISTINCT FROM CAST(:station_id AS INTEGER)
                            AND provider = :provider
                            AND recipient_domain = :domain;""")
                .single(call().bind("station_id", stationId)
                        .bind("provider", provider.name())
                        .bind("domain", domainOf(recipientDomain)))
                .delete();
    }

    /**
     * Removes blocks that have lapsed.
     */
    public int prune() {
        return query("DELETE FROM mail_provider_block WHERE expires_at <= now();")
                .single(call())
                .update()
                .rows();
    }

    /**
     * The part of an address a block is kept against. Null when there is nothing usable.
     */
    public static String domainOf(String value) {
        if (value == null) return null;
        int at = value.lastIndexOf('@');
        String domain = (at < 0 ? value : value.substring(at + 1)).trim().toLowerCase(Locale.ROOT);
        return domain.isBlank() ? null : domain;
    }
}

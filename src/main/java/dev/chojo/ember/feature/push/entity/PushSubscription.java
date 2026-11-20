/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A browser an account allowed push messages on.
 *
 * @param id            the subscription's id
 * @param accountId     the account it receives pushes for
 * @param sessionId     the sign-in it was registered in, which it ends with
 * @param endpoint      the push service's address for this browser
 * @param p256dh        the browser's public key, an uncompressed P-256 point
 * @param auth          the browser's 16-byte secret
 * @param label         a short name of browser and device
 * @param createdAt     when it was registered
 * @param lastSuccessAt when a push last reached the push service
 * @param failureCount  refusals in a row
 */
public record PushSubscription(
        int id,
        int accountId,
        int sessionId,
        String endpoint,
        byte[] p256dh,
        byte[] auth,
        String label,
        Instant createdAt,
        @Nullable Instant lastSuccessAt,
        int failureCount) {

    /** The columns {@link #map()} reads, aliased to a table. */
    public static String columns(String alias) {
        return """
                %1$s.id, %1$s.account_id, %1$s.session_id, %1$s.endpoint, %1$s.p256dh, %1$s.auth, %1$s.label,
                %1$s.created_at, %1$s.last_success_at, %1$s.failure_count""".formatted(alias);
    }

    /** Maps a row read with {@link #columns(String)}. */
    public static RowMapping<PushSubscription> map() {
        return row -> new PushSubscription(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getInt("session_id"),
                row.getString("endpoint"),
                row.getBytes("p256dh"),
                row.getBytes("auth"),
                row.getString("label"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("last_success_at", INSTANT_TIMESTAMP),
                row.getInt("failure_count"));
    }
}

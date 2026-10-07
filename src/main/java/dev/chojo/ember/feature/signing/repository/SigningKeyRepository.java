/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The installation's signing authority and the stations' signing keys.
 *
 * <p>Both inserts give way to a row that is already there instead of failing, so two callers creating
 * the same key at once both end up reading the one that was stored first.
 */
@Singleton
public class SigningKeyRepository {
    private static final String KEY_COLUMNS = "serial_number, certificate, wrapped_private_key, valid_until";

    /** @return the installation's signing authority, once it has been created */
    public Optional<StoredSigningKey> findAuthority() {
        return query("SELECT %s FROM signing_ca WHERE id = 1;", KEY_COLUMNS)
                .single(call())
                .map(StoredSigningKey.map())
                .first();
    }

    /**
     * Stores the installation's signing authority unless one is stored already.
     *
     * @param authority the authority's certificate and wrapped key
     * @return true when this one was stored, false when another was there first
     */
    public boolean storeAuthority(StoredSigningKey authority) {
        return query("""
                        INSERT INTO signing_ca (serial_number, certificate, wrapped_private_key, valid_until)
                        VALUES (:serial_number, :certificate, :wrapped_private_key, :valid_until)
                        ON CONFLICT (id) DO NOTHING;""").single(bindKey(authority)).insert().changed();
    }

    /**
     * The key a station seals with now.
     *
     * @param stationId the station
     * @return its active key, or empty before it sealed anything
     */
    public Optional<StoredSigningKey> findActive(int stationId) {
        return query("""
                        SELECT
                            %s
                        FROM
                            station_signing_key
                        WHERE station_id = :station_id
                          AND retired_at IS NULL;""", KEY_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StoredSigningKey.map())
                .first();
    }

    /**
     * Stores a station's key as its active one, unless it has an active key already.
     *
     * @param stationId the station
     * @param key       the key's certificate and wrapped private key
     * @return true when this key was stored, false when another active key was there first
     */
    public boolean storeActive(int stationId, StoredSigningKey key) {
        return query("""
                        INSERT INTO station_signing_key (station_id, serial_number, certificate, wrapped_private_key, valid_until)
                        VALUES (:station_id, :serial_number, :certificate, :wrapped_private_key, :valid_until)
                        ON CONFLICT (station_id) WHERE retired_at IS NULL DO NOTHING;""")
                .single(bindKey(key).bind("station_id", stationId))
                .insert()
                .changed();
    }

    private static Call bindKey(StoredSigningKey key) {
        return call().bind("serial_number", key.serialNumber())
                .bind("certificate", key.certificate())
                .bind("wrapped_private_key", key.wrappedPrivateKey())
                .bind("valid_until", key.validUntil(), INSTANT_TIMESTAMP);
    }
}

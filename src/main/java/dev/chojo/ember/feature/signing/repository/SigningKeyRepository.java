/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.signing.entity.StoredAuthority;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.signing.entity.StoredStationKey;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The installation's signing authorities and the stations' signing keys.
 *
 * <p>Both inserts give way to an active row that is already there instead of failing, so two callers
 * creating the same key at once both end up reading the one that was stored first. Retiring is
 * conditional on the row still being active, so of two callers replacing the same key only one
 * succeeds; the other waits for its row lock, finds the key retired and stores nothing.
 */
@Singleton
public class SigningKeyRepository {
    private static final String KEY_COLUMNS = "serial_number, certificate, wrapped_private_key, valid_until";
    private static final String AUTHORITY_COLUMNS = "id, " + KEY_COLUMNS;
    private static final String STATION_KEY_COLUMNS = "id, signing_ca_id, " + KEY_COLUMNS;

    /** @return the authority that issues new station certificates, once one has been created */
    public Optional<StoredAuthority> findActiveAuthority() {
        return query("SELECT %s FROM signing_ca WHERE retired_at IS NULL;", AUTHORITY_COLUMNS)
                .single(call())
                .map(StoredAuthority.map())
                .first();
    }

    /**
     * @param id the authority's id
     * @return that authority, active or retired
     */
    public Optional<StoredAuthority> findAuthority(int id) {
        return query("SELECT %s FROM signing_ca WHERE id = :id;", AUTHORITY_COLUMNS)
                .single(call().bind("id", id))
                .map(StoredAuthority.map())
                .first();
    }

    /**
     * Stores an authority as the active one, unless another is active already.
     *
     * @param authority the authority's certificate and wrapped key
     * @return true when this one was stored, false when another active one was there first
     */
    public boolean storeAuthority(StoredSigningKey authority) {
        return query("""
                        INSERT INTO signing_ca (serial_number, certificate, wrapped_private_key, valid_until)
                        VALUES (:serial_number, :certificate, :wrapped_private_key, :valid_until)
                        ON CONFLICT ((retired_at IS NULL)) WHERE retired_at IS NULL DO NOTHING;""").single(bindKey(authority)).insert().changed();
    }

    /**
     * Retires an authority, unless it was retired already.
     *
     * @param id the authority's id
     * @return true when this call retired it
     */
    public boolean retireAuthority(int id) {
        return query("""
                        UPDATE signing_ca
                        SET retired_at = now()
                        WHERE id = :id
                          AND retired_at IS NULL;""").single(call().bind("id", id)).update().changed();
    }

    /**
     * The key a station seals with now.
     *
     * @param stationId the station
     * @return its active key, or empty before it sealed anything
     */
    public Optional<StoredStationKey> findActive(int stationId) {
        return query("""
                        SELECT
                            %s
                        FROM
                            station_signing_key
                        WHERE station_id = :station_id
                          AND retired_at IS NULL;""", STATION_KEY_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StoredStationKey.map())
                .first();
    }

    /**
     * Stores a station's key as its active one, unless it has an active key already.
     *
     * @param stationId   the station
     * @param authorityId the authority that issued the key's certificate
     * @param key         the key's certificate and wrapped private key
     * @return true when this key was stored, false when another active key was there first
     */
    public boolean storeActive(int stationId, int authorityId, StoredSigningKey key) {
        return query("""
                        INSERT INTO station_signing_key
                            (station_id, signing_ca_id, serial_number, certificate, wrapped_private_key, valid_until)
                        VALUES
                            (:station_id, :signing_ca_id, :serial_number, :certificate, :wrapped_private_key, :valid_until)
                        ON CONFLICT (station_id) WHERE retired_at IS NULL DO NOTHING;""")
                .single(bindKey(key).bind("station_id", stationId).bind("signing_ca_id", authorityId))
                .insert()
                .changed();
    }

    /**
     * Retires a station's key, unless it was retired already.
     *
     * @param id the key's id
     * @return true when this call retired it
     */
    public boolean retire(int id) {
        return query("""
                        UPDATE station_signing_key
                        SET retired_at = now()
                        WHERE id = :id
                          AND retired_at IS NULL;""").single(call().bind("id", id)).update().changed();
    }

    /**
     * Every authority, active and retired, locked until the surrounding transaction ends so its
     * wrapped key cannot change underneath. Only meaningful inside a transaction.
     *
     * @return the authorities, oldest first
     */
    public List<StoredAuthority> lockAuthorities() {
        return query("SELECT %s FROM signing_ca ORDER BY id FOR NO KEY UPDATE;", AUTHORITY_COLUMNS)
                .single(call())
                .map(StoredAuthority.map())
                .all();
    }

    /**
     * Every station key, active and retired, locked like {@link #lockAuthorities()}.
     *
     * @return the station keys, oldest first
     */
    public List<StoredStationKey> lockStationKeys() {
        return query("SELECT %s FROM station_signing_key ORDER BY id FOR NO KEY UPDATE;", STATION_KEY_COLUMNS)
                .single(call())
                .map(StoredStationKey.map())
                .all();
    }

    /**
     * Replaces an authority's wrapped private key.
     *
     * @param id      the authority's id
     * @param wrapped the key wrapped anew
     */
    public void replaceAuthorityWrap(int id, byte[] wrapped) {
        query("UPDATE signing_ca SET wrapped_private_key = :wrapped WHERE id = :id;")
                .single(call().bind("id", id).bind("wrapped", wrapped))
                .update();
    }

    /**
     * Replaces a station key's wrapped private key.
     *
     * @param id      the key's id
     * @param wrapped the key wrapped anew
     */
    public void replaceStationKeyWrap(int id, byte[] wrapped) {
        query("UPDATE station_signing_key SET wrapped_private_key = :wrapped WHERE id = :id;")
                .single(call().bind("id", id).bind("wrapped", wrapped))
                .update();
    }

    private static Call bindKey(StoredSigningKey key) {
        return call().bind("serial_number", key.serialNumber())
                .bind("certificate", key.certificate())
                .bind("wrapped_private_key", key.wrappedPrivateKey())
                .bind("valid_until", key.validUntil(), INSTANT_TIMESTAMP);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.RevokedKey;
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
 * creating the same key at once both end up reading the one that was stored first. Retiring and
 * revoking are conditional on the row still being active or not yet revoked, so each happens once;
 * a second caller waits for the row lock of the first and then finds nothing left to change.
 *
 * <p>Each authority keeps its newest revocation list and the number it was issued under. Issuing the
 * next one locks the authority's row, and so does dropping the stored list after a revocation, so a
 * revocation is never lost between a list being signed and being stored.
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

    /**
     * @param serialNumber the authority certificate's serial number, lower-case hexadecimal
     * @return that authority, active or retired
     */
    public Optional<StoredAuthority> findAuthorityBySerial(String serialNumber) {
        return query("SELECT %s FROM signing_ca WHERE serial_number = :serial_number;", AUTHORITY_COLUMNS)
                .single(call().bind("serial_number", serialNumber))
                .map(StoredAuthority.map())
                .first();
    }

    /**
     * Revokes one of a station's keys, active or retired, and retires it if it was still active. Waits
     * for a caller revoking or retiring the same key at the same time, and leaves a key that was
     * revoked already as it is.
     *
     * @param stationId    the station the key belongs to
     * @param serialNumber the key's certificate serial number, lower-case hexadecimal
     * @param reason       why it is revoked
     * @return the authority that issued the key when this call revoked it, empty when the station
     *         holds no such key or it was revoked already
     */
    public Optional<Integer> revoke(int stationId, String serialNumber, RevocationReason reason) {
        return query("""
                        UPDATE station_signing_key
                        SET revoked_at        = now(),
                            revocation_reason = :reason,
                            retired_at        = coalesce(retired_at, now())
                        WHERE station_id = :station_id
                          AND serial_number = :serial_number
                          AND revoked_at IS NULL
                        RETURNING signing_ca_id;""")
                .single(call().bind("station_id", stationId)
                        .bind("serial_number", serialNumber)
                        .bind("reason", reason))
                .map(row -> row.getInt("signing_ca_id"))
                .first();
    }

    /**
     * @param stationId    the station
     * @param serialNumber a certificate serial number, lower-case hexadecimal
     * @return whether the station holds a key with that serial number, revoked or not
     */
    public boolean holds(int stationId, String serialNumber) {
        return query("""
                        SELECT 1 AS held
                        FROM station_signing_key
                        WHERE station_id = :station_id
                          AND serial_number = :serial_number;""")
                .single(call().bind("station_id", stationId).bind("serial_number", serialNumber))
                .map(row -> row.getInt("held"))
                .first()
                .isPresent();
    }

    /**
     * @param authorityId the authority
     * @return every station key it issued that was revoked, oldest revocation first
     */
    public List<RevokedKey> revokedBy(int authorityId) {
        return query("""
                        SELECT
                            serial_number,
                            revoked_at,
                            revocation_reason
                        FROM
                            station_signing_key
                        WHERE signing_ca_id = :signing_ca_id
                          AND revoked_at IS NOT NULL
                        ORDER BY revoked_at, id;""")
                .single(call().bind("signing_ca_id", authorityId))
                .map(RevokedKey.map())
                .all();
    }

    /**
     * @param authorityId the authority
     * @return the newest revocation list it issued, empty before the first and after a revocation
     *         made it outdated
     */
    public Optional<byte[]> findRevocationList(int authorityId) {
        return query("SELECT crl FROM signing_ca WHERE id = :id AND crl IS NOT NULL;")
                .single(call().bind("id", authorityId))
                .map(row -> row.getBytes("crl"))
                .first();
    }

    /**
     * Locks an authority's revocation list until the surrounding transaction ends, so only one caller
     * issues the next one. A revocation of one of its keys waits for the lock too. Only meaningful
     * inside a transaction.
     *
     * @param authorityId the authority
     * @return the number of the newest list it issued, 0 before the first
     */
    public long lockRevocationListNumber(int authorityId) {
        return query("SELECT crl_number FROM signing_ca WHERE id = :id FOR NO KEY UPDATE;")
                .single(call().bind("id", authorityId))
                .map(row -> row.getLong("crl_number"))
                .first()
                .orElseThrow(() -> new IllegalArgumentException("No signing authority with id " + authorityId));
    }

    /**
     * Stores an authority's newest revocation list with its number.
     *
     * @param authorityId the authority
     * @param number      the list's number
     * @param list        the list, DER encoded
     */
    public void storeRevocationList(int authorityId, long number, byte[] list) {
        query("UPDATE signing_ca SET crl_number = :number, crl = :crl WHERE id = :id;")
                .single(call().bind("id", authorityId).bind("number", number).bind("crl", list))
                .update();
    }

    /**
     * Drops an authority's stored revocation list, so the next reader gets a new one.
     *
     * @param authorityId the authority
     */
    public void forgetRevocationList(int authorityId) {
        query("UPDATE signing_ca SET crl = NULL WHERE id = :id;")
                .single(call().bind("id", authorityId))
                .update();
    }

    private static Call bindKey(StoredSigningKey key) {
        return call().bind("serial_number", key.serialNumber())
                .bind("certificate", key.certificate())
                .bind("wrapped_private_key", key.wrappedPrivateKey())
                .bind("valid_until", key.validUntil(), INSTANT_TIMESTAMP);
    }
}

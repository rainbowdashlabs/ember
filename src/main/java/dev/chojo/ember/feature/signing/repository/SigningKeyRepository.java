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
import dev.chojo.ember.feature.signing.entity.StoredAuthorityCertificate;
import dev.chojo.ember.feature.signing.entity.StoredRevocationList;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.signing.entity.StoredStationCertificate;
import dev.chojo.ember.feature.signing.entity.StoredStationKey;
import jakarta.inject.Singleton;

import java.time.Instant;
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
 * next one locks the authority's row, and so does revoking one of its keys, so a revocation is never
 * lost between a list being signed and being stored.
 *
 * <p><b>Lock order.</b> A transaction that locks rows of both tables takes the authority rows first, in
 * the order of their ids when it takes several, and the station key rows after them. Re-wrapping every
 * key, revoking a key and issuing a revocation list all follow it, so none of them waits for another in
 * a circle. Storing a station key takes only a key-share lock on its authority's row, which none of
 * those locks conflicts with.
 *
 * <p>Station keys outlive their station: deleting a station leaves its keys without a station, so a
 * revoked key stays on its authority's lists and a key of a deleted station can still be revoked by its
 * serial number.
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
     * @param stationId    the station
     * @param serialNumber a certificate serial number, lower-case hexadecimal
     * @return the authority that issued the station's key with that serial number, revoked or not,
     *         or empty when the station holds no such key
     */
    public Optional<Integer> authorityOfStationKey(int stationId, String serialNumber) {
        return query("""
                        SELECT signing_ca_id
                        FROM station_signing_key
                        WHERE station_id = :station_id
                          AND serial_number = :serial_number;""")
                .single(call().bind("station_id", stationId).bind("serial_number", serialNumber))
                .map(row -> row.getInt("signing_ca_id"))
                .first();
    }

    /**
     * @param serialNumber a certificate serial number, lower-case hexadecimal
     * @return the authority that issued the key with that serial number of a station that was deleted,
     *         revoked or not, or empty when no deleted station held such a key
     */
    public Optional<Integer> authorityOfDeletedStationKey(String serialNumber) {
        return query("""
                        SELECT signing_ca_id
                        FROM station_signing_key
                        WHERE station_id IS NULL
                          AND serial_number = :serial_number;""")
                .single(call().bind("serial_number", serialNumber))
                .map(row -> row.getInt("signing_ca_id"))
                .first();
    }

    /**
     * Revokes a station key, active or retired, and retires it if it was still active. Leaves a key
     * that was revoked already as it is. Lock the issuing authority first
     * ({@link #lockRevocationListNumber(int)}), as the lock order requires.
     *
     * @param authorityId  the authority that issued the key
     * @param serialNumber the key's certificate serial number, lower-case hexadecimal
     * @param reason       why it is revoked
     * @param revokedAt    the revocation date its authority's lists give for it
     * @return true when this call revoked the key, false when that authority issued no such key or it
     *         was revoked already
     */
    public boolean revoke(int authorityId, String serialNumber, RevocationReason reason, Instant revokedAt) {
        return query("""
                        UPDATE station_signing_key
                        SET revoked_at        = :revoked_at,
                            revocation_reason = :reason,
                            retired_at        = coalesce(retired_at, :revoked_at)
                        WHERE signing_ca_id = :signing_ca_id
                          AND serial_number = :serial_number
                          AND revoked_at IS NULL;""")
                .single(call().bind("signing_ca_id", authorityId)
                        .bind("serial_number", serialNumber)
                        .bind("reason", reason)
                        .bind("revoked_at", revokedAt, INSTANT_TIMESTAMP))
                .update()
                .changed();
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
    public Optional<StoredRevocationList> findRevocationList(int authorityId) {
        return query("SELECT crl, crl_issued_at FROM signing_ca WHERE id = :id AND crl IS NOT NULL;")
                .single(call().bind("id", authorityId))
                .map(StoredRevocationList.map())
                .first();
    }

    /**
     * Locks an authority's row, and with it its revocation list, until the surrounding transaction
     * ends, so only one caller issues the next list. A revocation of one of its keys takes the lock
     * too. Only meaningful inside a transaction.
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
     * @param list        the list and when it was issued
     */
    public void storeRevocationList(int authorityId, long number, StoredRevocationList list) {
        query("""
                        UPDATE signing_ca
                        SET crl_number    = :number,
                            crl           = :crl,
                            crl_issued_at = :crl_issued_at
                        WHERE id = :id;""")
                .single(call().bind("id", authorityId)
                        .bind("number", number)
                        .bind("crl", list.list())
                        .bind("crl_issued_at", list.issuedAt(), INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Drops an authority's stored revocation list, so the next reader gets a new one.
     *
     * @param authorityId the authority
     */
    public void forgetRevocationList(int authorityId) {
        query("UPDATE signing_ca SET crl = NULL, crl_issued_at = NULL WHERE id = :id;")
                .single(call().bind("id", authorityId))
                .update();
    }

    /** @return every authority's certificate, active and retired, newest first, without private keys */
    public List<StoredAuthorityCertificate> authorityCertificates() {
        return query("""
                        SELECT
                            serial_number,
                            certificate,
                            retired_at IS NULL AS active
                        FROM
                            signing_ca
                        ORDER BY id DESC;""").single(call()).map(StoredAuthorityCertificate.map()).all();
    }

    /**
     * @param serialNumber the authority certificate's serial number, lower-case hexadecimal
     * @return that authority's certificate, DER encoded, active or retired
     */
    public Optional<byte[]> authorityCertificate(String serialNumber) {
        return query("SELECT certificate FROM signing_ca WHERE serial_number = :serial_number;")
                .single(call().bind("serial_number", serialNumber))
                .map(row -> row.getBytes("certificate"))
                .first();
    }

    /**
     * @param stationId the station
     * @return every certificate the station's keys had, active, retired and revoked, newest first,
     *         without private keys
     */
    public List<StoredStationCertificate> stationCertificates(int stationId) {
        return query("""
                        SELECT
                            k.serial_number,
                            k.certificate,
                            ca.serial_number AS authority_serial_number,
                            k.revoked_at
                        FROM
                            station_signing_key k
                            JOIN signing_ca ca ON ca.id = k.signing_ca_id
                        WHERE k.station_id = :station_id
                        ORDER BY k.id DESC;""")
                .single(call().bind("station_id", stationId))
                .map(StoredStationCertificate.map())
                .all();
    }

    /**
     * @param stationId    the station
     * @param serialNumber a certificate serial number, lower-case hexadecimal
     * @return the station's certificate with that serial number, DER encoded, or empty when the
     *         station never had it
     */
    public Optional<byte[]> stationCertificate(int stationId, String serialNumber) {
        return query("""
                        SELECT certificate
                        FROM station_signing_key
                        WHERE station_id = :station_id
                          AND serial_number = :serial_number;""")
                .single(call().bind("station_id", stationId).bind("serial_number", serialNumber))
                .map(row -> row.getBytes("certificate"))
                .first();
    }

    private static Call bindKey(StoredSigningKey key) {
        return call().bind("serial_number", key.serialNumber())
                .bind("certificate", key.certificate())
                .bind("wrapped_private_key", key.wrappedPrivateKey())
                .bind("valid_until", key.validUntil(), INSTANT_TIMESTAMP);
    }
}

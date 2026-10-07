/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.StoredAuthority;
import dev.chojo.ember.feature.signing.entity.StoredRevocationList;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigInteger;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Revokes station keys and hands out each authority's revocation list.
 *
 * <p><b>Revoking.</b> A key is revoked by its station and its certificate's serial number, the number a
 * reader sees in a sealed document. That covers the active key, which is retired along with it so the
 * station's next seal gets a new one ({@link StationSigningKeys#forStation(int)}), and a key that was
 * retired already, since a leak may come to light only after a rotation. A key of a station that was
 * deleted is revoked by its serial number alone ({@link #revokeKeyOfDeletedStation}). A key is revoked
 * once; its date and reason never change afterwards.
 *
 * <p><b>Lists.</b> Every authority, active or retired, has its own list naming every key it issued that
 * was revoked ({@link RevocationLists}). A list is signed when a reader asks for it and stored with its
 * authority and its issue time, so one list serves every reader for {@link #REISSUE_AFTER}; after that
 * the next reader gets a new one, so a published list always has most of its
 * {@link RevocationLists#VALIDITY} left. A revocation drops the stored list in the transaction that
 * revokes the key, which makes the next list name the key, in this process and in any other on the same
 * database. Each list gets the next number of its authority, counted under a lock on the authority's
 * row, so the numbers only ever increase. Revoking takes the same lock before it touches the key, in
 * the order the repository prescribes.
 *
 * <p><b>Clock.</b> The revocation date and a list's {@code thisUpdate} both come from this service's
 * clock, never from the database's, so a list never names a revocation dated after the list itself.
 *
 * <p>Revoking an authority itself is not possible: its certificate is the anchor readers pin, so there
 * is nobody above it to sign a list naming it. Replacing a compromised authority means a new anchor for
 * every reader.
 */
@Singleton
public class StationKeyRevocations {
    /**
     * How long a stored list is handed out before the next reader gets a new one. A day keeps signing to
     * at most one list per authority and day while every list a reader fetches holds for at least six
     * more days.
     */
    static final Duration REISSUE_AFTER = Duration.ofDays(1);

    private final SigningKeyRepository keys;
    private final RevocationLists lists;
    private final SigningKeyWrap wrap;
    private final Clock clock;

    /**
     * @param keys  where the keys and the lists are stored
     * @param lists signs the lists
     * @param wrap  opens the authorities' private keys
     */
    @Inject
    public StationKeyRevocations(SigningKeyRepository keys, RevocationLists lists, SigningKeyWrap wrap) {
        this(keys, lists, wrap, Clock.systemUTC());
    }

    /**
     * @param keys  where the keys and the lists are stored
     * @param lists signs the lists
     * @param wrap  opens the authorities' private keys
     * @param clock where revocation dates and the lists' issue times come from
     */
    StationKeyRevocations(SigningKeyRepository keys, RevocationLists lists, SigningKeyWrap wrap, Clock clock) {
        this.keys = keys;
        this.lists = lists;
        this.wrap = wrap;
        this.clock = clock;
    }

    /**
     * Revokes one of a station's keys and retires it if it was the active one. Callers revoking the same
     * key at the same time revoke it once between them.
     *
     * @param stationId    the station the key belongs to
     * @param serialNumber the key's certificate serial number in hexadecimal
     * @param reason       why it is revoked
     * @return true when this call revoked the key, false when it was revoked already
     * @throws IllegalArgumentException when the station holds no key with that serial number
     */
    public boolean revoke(int stationId, String serialNumber, RevocationReason reason) {
        var serial = serialOrRefuse(serialNumber);
        var authority = keys.authorityOfStationKey(stationId, serial)
                .orElseThrow(() -> new IllegalArgumentException("Station " + stationId + " holds no key " + serial));
        return revokeIssuedBy(authority, serial, reason);
    }

    /**
     * Revokes a key of a station that was deleted, which leaves its keys behind for exactly this.
     *
     * @param serialNumber the key's certificate serial number in hexadecimal
     * @param reason       why it is revoked
     * @return true when this call revoked the key, false when it was revoked already
     * @throws IllegalArgumentException when no deleted station held a key with that serial number
     */
    public boolean revokeKeyOfDeletedStation(String serialNumber, RevocationReason reason) {
        var serial = serialOrRefuse(serialNumber);
        var authority = keys.authorityOfDeletedStationKey(serial)
                .orElseThrow(() -> new IllegalArgumentException("No deleted station held a key " + serial));
        return revokeIssuedBy(authority, serial, reason);
    }

    private boolean revokeIssuedBy(int authority, String serial, RevocationReason reason) {
        return Transactions.call(() -> {
            keys.lockRevocationListNumber(authority);
            boolean revoked = keys.revoke(authority, serial, reason, now());
            if (revoked) keys.forgetRevocationList(authority);
            return revoked;
        });
    }

    /**
     * The current revocation list of an authority, active or retired.
     *
     * @param authoritySerial the authority certificate's serial number in hexadecimal
     * @return the list, DER encoded, or empty when no authority has that serial number
     * @throws SigningKeyWrapException when the authority's key does not open under the at-rest secret
     */
    public Optional<byte[]> revocationList(String authoritySerial) {
        return normalised(authoritySerial).flatMap(keys::findAuthorityBySerial).map(this::currentList);
    }

    private byte[] currentList(StoredAuthority authority) {
        return keys.findRevocationList(authority.id())
                .filter(this::fresh)
                .orElseGet(() -> Transactions.call(() -> reissue(authority)))
                .list();
    }

    private StoredRevocationList reissue(StoredAuthority authority) {
        var number = keys.lockRevocationListNumber(authority.id());
        var stored = keys.findRevocationList(authority.id()).filter(this::fresh);
        if (stored.isPresent()) return stored.get();
        var next = number + 1;
        var thisUpdate = now();
        var list = new StoredRevocationList(
                encoded(lists.issue(wrap.open(authority.key()), next, keys.revokedBy(authority.id()), thisUpdate)),
                thisUpdate);
        keys.storeRevocationList(authority.id(), next, list);
        return list;
    }

    private boolean fresh(StoredRevocationList list) {
        return list.issuedAt().plus(REISSUE_AFTER).isAfter(clock.instant());
    }

    /** @return the time on this service's clock, to the second, which is all a revocation list holds */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static byte[] encoded(X509CRL list) {
        try {
            return list.getEncoded();
        } catch (CRLException e) {
            throw new IllegalStateException("A revocation list could not be encoded", e);
        }
    }

    private static String serialOrRefuse(String serialNumber) {
        return normalised(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Not a serial number: " + serialNumber));
    }

    private static Optional<String> normalised(String serialNumber) {
        try {
            var serial = new BigInteger(serialNumber.strip(), 16);
            return serial.signum() > 0 ? Optional.of(serial.toString(16)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

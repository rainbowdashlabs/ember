/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.KeyInUse;
import dev.chojo.ember.feature.signing.entity.LockedSigningKey;
import dev.chojo.ember.feature.signing.entity.SigningKeyKind;
import dev.chojo.ember.feature.signing.entity.SigningKeyRecoveryEntry;
import dev.chojo.ember.feature.signing.entity.SigningKeyStatus;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the signing keys that no longer open under the at-rest secret, and gives them up when an instance
 * administrator says so.
 *
 * <p><b>Why keys stop opening.</b> Every authority and station key is wrapped under a key derived from the
 * at-rest secret ({@link SigningKeyWrap}). When the key file is lost, Ember creates a new one on its next
 * start, and none of the stored keys opens under it: no station can seal, and no authority can sign a
 * revocation list.
 *
 * <p><b>Never on its own.</b> Nothing else in Ember replaces a key that does not open. A secret that is
 * only misconfigured looks exactly like a lost one, and replacing the authority then would give every
 * reader a new anchor to pin for nothing. So sealing keeps failing, and the administration shows the keys,
 * until an administrator gives them up here, after checking that the key file cannot be restored.
 *
 * <p><b>Giving up.</b> {@link #recover(int, List)} runs only for exactly the keys the administrator was
 * shown: the serial numbers they confirm must be the whole list of keys that do not open at that moment,
 * else nothing happens. In one transaction, holding the keys in use in the lock order, each key that does
 * not open is marked given up and retired, every active station key of a given-up authority is retired as
 * well (no list could ever revoke it), and the recovery is recorded with the administrator. Nothing is
 * deleted. The next seal of each station then creates a new authority and a new station key under the
 * current secret, the way the very first seal did.
 *
 * <p><b>What stays.</b> Every given-up key keeps its row and its certificate, so documents sealed earlier
 * keep their chain: the authority's certificate stays published and pinned beside the new one, it stays a
 * trust anchor of the seal check, and its last revocation list is served as it was
 * ({@link StationKeyRevocations}). Revoked keys stay on that list. Given-up keys are left out of re-wrapping
 * ({@link SigningKeyRewrap}), since they do not open with any secret at hand.
 */
@Singleton
public class SigningKeyRecovery {
    /** How many earlier recoveries the status lists. */
    static final int HISTORY = 20;

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRecovery.class);

    private final SigningKeyRepository keys;
    private final SigningKeyWrap wrap;

    /**
     * @param keys where the keys are stored
     * @param wrap opens them under the current at-rest secret
     */
    @Inject
    public SigningKeyRecovery(SigningKeyRepository keys, SigningKeyWrap wrap) {
        this.keys = keys;
        this.wrap = wrap;
    }

    /** @return which keys in use no longer open, how many do, and the earlier recoveries */
    public SigningKeyStatus status() {
        var inUse = keys.keysInUse();
        var locked = locked(inUse);
        return new SigningKeyStatus(
                locked.stream().map(SigningKeyRecovery::shown).toList(),
                inUse.size() - locked.size(),
                keys.recoveries(HISTORY));
    }

    /** @return how many keys in use no longer open, 0 while every key opens */
    public int lockedCount() {
        return locked(keys.keysInUse()).size();
    }

    /**
     * Gives up the keys that no longer open, provided they are exactly the ones confirmed.
     *
     * @param accountId     the administrator giving them up
     * @param serialNumbers the serial numbers of the keys they were shown as no longer opening
     * @return the recorded recovery
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@code SIGNING_KEYS_ALL_OPEN} when every key opens,
     *                                                     {@code SIGNING_KEYS_CHANGED} when the keys that
     *                                                     do not open are not exactly the confirmed ones
     */
    public SigningKeyRecoveryEntry recover(int accountId, List<String> serialNumbers) {
        var confirmed = normalized(serialNumbers);
        var recovery = Transactions.call(() -> {
            var locked = locked(keys.lockKeysInUse());
            if (locked.isEmpty()) throw DocumentRefusal.SIGNING_KEYS_ALL_OPEN.raise();
            if (!serialsOf(locked).equals(confirmed)) throw DocumentRefusal.SIGNING_KEYS_CHANGED.raise();
            locked.forEach(this::abandon);
            keys.retireKeysOfAbandonedAuthorities();
            return keys.recordRecovery(
                    accountId,
                    serialsOf(locked, SigningKeyKind.AUTHORITY),
                    serialsOf(locked, SigningKeyKind.STATION_KEY));
        });
        log.warn(
                "Signing keys that no longer opened were given up by account {}: authorities {}, station keys {};"
                        + " the next seal of each station issues new ones",
                accountId,
                recovery.authoritySerials(),
                recovery.stationKeySerials());
        return recovery;
    }

    private void abandon(KeyInUse key) {
        if (key.kind() == SigningKeyKind.AUTHORITY) {
            keys.abandonAuthority(key.id());
        } else {
            keys.abandonStationKey(key.id());
        }
    }

    private List<KeyInUse> locked(List<KeyInUse> inUse) {
        return inUse.stream().filter(key -> !opens(key)).toList();
    }

    private boolean opens(KeyInUse key) {
        try {
            wrap.unwrap(key.key());
            return true;
        } catch (SigningKeyWrapException e) {
            return false;
        }
    }

    private static Set<String> normalized(List<String> serialNumbers) {
        var serials = new HashSet<String>();
        for (var serial : serialNumbers) {
            serials.add(SigningCertificates.serialNumber(serial).orElse(serial));
        }
        return serials;
    }

    private static Set<String> serialsOf(List<KeyInUse> keys) {
        var serials = new HashSet<String>();
        keys.forEach(key -> serials.add(key.key().serialNumber()));
        return serials;
    }

    private static List<String> serialsOf(List<KeyInUse> keys, SigningKeyKind kind) {
        return keys.stream()
                .filter(key -> key.kind() == kind)
                .map(key -> key.key().serialNumber())
                .toList();
    }

    private static LockedSigningKey shown(KeyInUse key) {
        return new LockedSigningKey(
                key.kind(),
                key.key().serialNumber(),
                PublishedCertificates.fingerprintOf(key.key().certificate()),
                key.active(),
                key.stationName(),
                key.key().validUntil());
    }
}

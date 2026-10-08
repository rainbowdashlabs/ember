/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Moves every stored signing key from an old at-rest secret to the current one.
 *
 * <p>When the at-rest secret changes, the keys wrapped under the old one no longer open. This
 * re-encrypts every authority and every station key, active and retired alike, so retired keys stay
 * readable for revocation and history. Keys of deleted stations have no private key left and are passed
 * over. The whole run is one transaction: the rows are locked, the
 * authorities before the station keys as {@link SigningKeyRepository} prescribes, each
 * key is unwrapped with the old secret and wrapped with the current one, and a single key that does
 * not open with the old secret rolls everything back, leaving every stored key as it was.
 *
 * <p>Nothing calls this, on purpose: Ember has no way to change the at-rest secret, and changing it by
 * hand already leaves federation keys and mailbox passwords unreadable. It is ready for the day the
 * secret can be rotated as a whole, and then belongs in that rotation: run once, right after the secret
 * changed and before anything is sealed again, with the old secret still at hand. A key created under
 * the new secret in between does not open with the old one and stops the run. Restoring a database
 * backup does not need it, since the key file from the same backup opens the keys as they are.
 */
@Singleton
public class SigningKeyRewrap {
    private final SigningKeyRepository keys;
    private final SigningKeyWrap current;

    /**
     * @param keys    where the keys are stored
     * @param current wraps under the current at-rest secret
     */
    @Inject
    public SigningKeyRewrap(SigningKeyRepository keys, SigningKeyWrap current) {
        this.keys = keys;
        this.current = current;
    }

    /**
     * Re-wraps every stored key from the old secret to the current one, all or nothing.
     *
     * @param previous wraps under the old at-rest secret
     * @return how many keys were re-wrapped
     * @throws SigningKeyWrapException when any key does not open with the old secret; nothing is
     *                                 changed then
     */
    public int rewrapFrom(SigningKeyWrap previous) {
        return Transactions.call(() -> {
            int count = 0;
            for (var authority : keys.lockAuthorities()) {
                keys.replaceAuthorityWrap(authority.id(), rewrapped(previous, authority.key(), "authority"));
                count++;
            }
            for (var stationKey : keys.lockStationKeys()) {
                keys.replaceStationKeyWrap(stationKey.id(), rewrapped(previous, stationKey.key(), "station key"));
                count++;
            }
            return count;
        });
    }

    private byte[] rewrapped(SigningKeyWrap previous, StoredSigningKey stored, String kind) {
        try {
            return current.wrap(previous.unwrap(stored));
        } catch (SigningKeyWrapException e) {
            throw new SigningKeyWrapException(
                    "Re-wrapping stopped and nothing was changed: the " + kind + " with certificate serial "
                            + stored.serialNumber() + " does not open with the previous at-rest secret",
                    e);
        }
    }
}

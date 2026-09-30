/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Carries a station's federation key from the instance it leaves to the instance it moves to.
 *
 * <p>The key has to move with the station: its partners hold the matching public key and verify
 * every request against it, and there is no way to tell them about a new one. It cannot travel as
 * it is stored either, because it is encrypted with the source instance's own key, which the
 * destination does not have. So the source decrypts it and seals it again with a key derived from
 * the transfer token, which the operator carried from one instance to the other by hand, and the
 * destination opens it with the same token and stores it under its own key. The key therefore never
 * appears in plaintext in the exported station, and a captured page is useless without the token.
 *
 * <p>The sealed key travels beside the station's settings, under {@value #FIELD}, and never among
 * the station's columns, so the generic column copy cannot write it verbatim.
 */
@Singleton
public class StationKeyTransfer {
    /** The field of the exported station page that carries the sealed key. */
    public static final String FIELD = "federationKey";

    private static final Logger log = LoggerFactory.getLogger(StationKeyTransfer.class);
    private static final String PURPOSE = "ember station transfer: federation key";
    private static final String LEGACY_COLUMN = "federation_private_key";

    private final StationKeyStore keys;

    @Inject
    public StationKeyTransfer(StationKeyStore keys) {
        this.keys = keys;
    }

    /**
     * The station's key sealed for one transfer.
     *
     * @param stationId the station being exported
     * @param token     the transfer token the destination pulls with
     * @return the sealed key, or empty when the station has none
     */
    public Optional<String> seal(int stationId, String token) {
        return keys.privateKey(stationId)
                .map(key -> CredentialCipher.derivedFrom(PURPOSE, token).seal(StationKeyStore.encode(key)));
    }

    /**
     * Makes the key that came with an exported station page the imported station's own.
     *
     * <p>A page from a source that still exported the key as a plain column is accepted as well, and
     * the key is encrypted here the moment it is stored.
     *
     * @param stationId the station the page was imported into
     * @param page      the exported station page
     * @param station   the station's columns from that page
     * @param token     the transfer token the page was pulled with
     * @return true when a key was adopted
     */
    public boolean adopt(int stationId, Map<String, Object> page, Map<String, Object> station, String token) {
        var encoded = sealedKey(page)
                .map(sealedKey -> CredentialCipher.derivedFrom(PURPOSE, token).unseal(sealedKey))
                .or(() -> legacyKey(station));
        if (encoded.isEmpty()) {
            log.warn("The station imported into station {} brought no federation key", stationId);
            return false;
        }
        keys.adopt(stationId, StationKeyStore.decode(encoded.get()));
        log.info("Station {} took over the federation key of the station it was imported from", stationId);
        return true;
    }

    private static Optional<String> sealedKey(Map<String, Object> page) {
        return page == null ? Optional.empty() : text(page.get(FIELD)).filter(CredentialCipher::isSealed);
    }

    private static Optional<String> legacyKey(Map<String, Object> station) {
        return station == null
                ? Optional.empty()
                : text(station.get(LEGACY_COLUMN)).filter(value -> !CredentialCipher.isSealed(value));
    }

    private static Optional<String> text(Object value) {
        return value instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
    }
}

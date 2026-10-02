/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.StationKey;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carries the AI provider keys a station keeps from the instance it leaves to the instance it moves
 * to, the same way its federation key is carried.
 *
 * <p>A stored key is encrypted with the source instance's own key, which the destination does not
 * have, so the copied table row arrives with a key nobody can open. The source therefore opens every
 * key the station keeps and seals the lot, with provider and model, with a key derived from the
 * transfer token; the destination opens it with the same token and stores each key under its own
 * key. No key appears in plaintext in the exported station, and a captured page is useless without
 * the token.
 *
 * <p>The sealed keys travel beside the station's settings under {@value #FIELD}. They are stored
 * before the tables are copied, so the copied row for the same provider finds its place taken and
 * is skipped. A key that no longer opens on the source is not carried and arrives as before: as a
 * key the station enters again.
 */
@Singleton
public class StationAiKeyTransfer {
    /** The field of the exported station page that carries the sealed keys. */
    public static final String FIELD = "aiKeys";

    private static final Logger log = LoggerFactory.getLogger(StationAiKeyTransfer.class);
    private static final String PURPOSE = "ember station transfer: AI keys";
    private static final TypeReference<List<CarriedKey>> KEYS = new TypeReference<>() {};

    private final AiCredentialService credentials;

    @Inject
    public StationAiKeyTransfer(AiCredentialService credentials) {
        this.credentials = credentials;
    }

    /**
     * The station's AI keys sealed for one transfer.
     *
     * @param stationId the station being exported
     * @param token     the transfer token the destination pulls with
     * @return the sealed keys, or empty when the station keeps none that opens
     */
    public Optional<String> seal(int stationId, String token) {
        List<CarriedKey> keys =
                credentials.stationKeys(stationId).stream().map(CarriedKey::of).toList();
        if (keys.isEmpty()) return Optional.empty();
        return Optional.of(cipher(token).seal(Json.MAPPER.writeValueAsString(keys)));
    }

    /**
     * Stores the AI keys that came with an exported station page as the imported station's own.
     *
     * @param stationId the station the page was imported into
     * @param page      the exported station page
     * @param token     the transfer token the page was pulled with
     * @return how many keys were stored; a key for a provider this instance does not know is left out
     */
    public int adopt(int stationId, Map<String, Object> page, String token) {
        if (!(page.get(FIELD) instanceof String sealed) || !CredentialCipher.isSealed(sealed)) return 0;
        List<StationKey> keys = Json.MAPPER.readValue(cipher(token).unseal(sealed), KEYS).stream()
                .flatMap(carried -> carried.known().stream())
                .toList();
        keys.forEach(key -> credentials.saveStationKey(stationId, key.provider(), key.key(), key.model()));
        log.info("Station {} took over {} AI key(s) of the station it was imported from", stationId, keys.size());
        return keys.size();
    }

    static CredentialCipher cipher(String token) {
        return CredentialCipher.derivedFrom(PURPOSE, token);
    }

    /**
     * One key as it travels: the provider under its stored key rather than its constant name, which is
     * what instances before and after the provider became an enum both read.
     *
     * @param provider the provider's stored key, for example {@code openai}
     * @param model    the model asked by default, or {@code null} for the provider's default
     * @param key      the plaintext key
     */
    record CarriedKey(String provider, @Nullable String model, String key) {
        static CarriedKey of(StationKey key) {
            return new CarriedKey(key.provider().key(), key.model(), key.key());
        }

        Optional<StationKey> known() {
            return AiVendor.fromKey(provider).map(vendor -> new StationKey(vendor, model, key));
        }
    }
}

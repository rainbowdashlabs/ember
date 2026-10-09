/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.StationKeyRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.PartnersLeftBehind;
import dev.chojo.ember.feature.federation.service.StationKeyStore;
import dev.chojo.ember.feature.federation.service.StationKeyTransfer;
import dev.chojo.ember.feature.federation.service.StationSigner;
import dev.chojo.ember.feature.quiz.repository.AccountAiCredentialRepository;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.service.AiCredentialService;
import dev.chojo.ember.feature.quiz.service.StationAiKeyTransfer;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.jspecify.annotations.Nullable;

import java.util.Base64;

/**
 * Builds the station key services against the test database with a fixed encryption key, so a test
 * that needs stations able to sign does not have to know how their keys are stored.
 */
public final class TestStationKeys {
    /** The at-rest key every test store encrypts with. */
    public static final String CIPHER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private TestStationKeys() {}

    /** A cipher with the fixed test key. */
    public static CredentialCipher cipher() {
        return new CredentialCipher(CIPHER_KEY);
    }

    /** A key store over the test database. */
    public static StationKeyStore store() {
        return new StationKeyStore(new StationKeyRepository(), cipher());
    }

    /** A signer over a fresh key store. */
    public static StationSigner signer() {
        return new StationSigner(store(), new FederationSigningService());
    }

    /**
     * The partner fix-up of a station transfer over a fresh key store.
     *
     * @param federation the partnerships
     * @param http       what announces to remote partners, or {@code null} where nothing is announced
     */
    public static FederationPartnerTransferFixupService partnerFixup(
            FederationRepository federation, @Nullable FederationHttpClient http) {
        return new FederationPartnerTransferFixupService(federation, http, store(), new TaskScheduler());
    }

    /** The transfer of station keys over a fresh key store. */
    public static StationKeyTransfer transfer() {
        return new StationKeyTransfer(store());
    }

    /** The partners a moving station leaves behind, with their keys from a fresh key store. */
    public static PartnersLeftBehind partnersLeftBehind() {
        return new PartnersLeftBehind(new FederationRepository(), new StationRepository(), store());
    }

    /** The transfer of a station's AI keys over the test database. */
    public static StationAiKeyTransfer aiKeyTransfer() {
        return new StationAiKeyTransfer(
                new AiCredentialService(new AccountAiCredentialRepository(), new AiProviderRepository(), cipher()));
    }
}

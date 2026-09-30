/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.AccountAiCredential;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.repository.AccountAiCredentialRepository;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.CredentialCipherException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The AI provider key a person keeps on the server, encrypted, instead of in their browser.
 *
 * <p>The key is sealed by {@link CredentialCipher} before it is written and only ever opened to make
 * a call to the provider. What a person is shown of it is which provider and model it is for and its
 * last four characters, which is enough to recognise it and useless to anybody else.
 *
 * <p>A key that no longer opens, because the instance's credential key was lost or replaced, counts
 * as no key at all: the person is asked to enter it again rather than shown a failure they cannot
 * fix.
 */
@Singleton
public class AiCredentialService {
    private static final Logger log = LoggerFactory.getLogger(AiCredentialService.class);
    private static final int VISIBLE_ENDING = 4;

    private final AccountAiCredentialRepository repository;
    private final CredentialCipher cipher;

    @Inject
    public AiCredentialService(AccountAiCredentialRepository repository, CredentialCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * What a person may see of their own key.
     *
     * @param accountId the account
     * @return the provider, the model and the end of the key, or empty when the account keeps none
     */
    public Optional<AiCredentialSummary> summary(int accountId) {
        return repository.find(accountId).map(credential -> {
            Optional<String> key = open(credential);
            return new AiCredentialSummary(
                    credential.provider(),
                    credential.model(),
                    key.isPresent(),
                    key.map(AiCredentialService::ending).orElse(null));
        });
    }

    /**
     * Saves a person's key, or only their choice of provider and model.
     *
     * <p>Without a new key the one already stored is kept, which lets somebody change the model without
     * typing the key again. That only works for the same provider: a key for one provider is no use for
     * another.
     *
     * @param accountId the account
     * @param provider  the provider the key is for
     * @param model     the model to ask by default, or blank for the provider's default
     * @param apiKey    the new key, or blank to keep the stored one
     * @return what became of it
     */
    public SaveOutcome save(int accountId, String provider, String model, String apiKey) {
        if (AiVendor.fromKey(provider).isEmpty()) return SaveOutcome.PROVIDER_UNKNOWN;
        String chosenModel = model == null || model.isBlank() ? null : model.trim();
        if (apiKey != null && !apiKey.isBlank()) {
            repository.save(accountId, provider, chosenModel, cipher.seal(apiKey.trim()));
            return SaveOutcome.SAVED;
        }
        Optional<AccountAiCredential> kept = repository
                .find(accountId)
                .filter(credential -> credential.provider().equals(provider))
                .filter(credential -> open(credential).isPresent());
        if (kept.isEmpty()) return SaveOutcome.KEY_MISSING;
        repository.save(accountId, provider, chosenModel, kept.get().sealedKey());
        return SaveOutcome.SAVED;
    }

    /**
     * Forgets a person's key.
     *
     * @param accountId the account
     * @return whether there was one to forget
     */
    public boolean delete(int accountId) {
        return repository.delete(accountId);
    }

    /**
     * The key to call a provider with on a person's behalf.
     *
     * @param accountId the account
     * @param provider  the provider to be called
     * @return the plaintext key, or empty when the account keeps none for this provider
     */
    public Optional<String> keyFor(int accountId, String provider) {
        return repository
                .find(accountId)
                .filter(credential -> credential.provider().equals(provider))
                .flatMap(this::open);
    }

    private Optional<String> open(AccountAiCredential credential) {
        try {
            return Optional.of(cipher.unseal(credential.sealedKey()));
        } catch (CredentialCipherException e) {
            log.warn(
                    "The AI key of account {} could not be decrypted and has to be entered again",
                    credential.accountId());
            return Optional.empty();
        }
    }

    private static String ending(String key) {
        return key.length() <= VISIBLE_ENDING ? "" : key.substring(key.length() - VISIBLE_ENDING);
    }

    /**
     * What a person is shown of their key.
     *
     * @param provider  the provider the key is for
     * @param model     the model asked by default, or {@code null} for the provider's default
     * @param usable    whether the stored key still opens; false means it has to be entered again
     * @param keyEnding the last four characters of the key, or {@code null} when it does not open
     */
    public record AiCredentialSummary(String provider, String model, boolean usable, String keyEnding) {}

    /** What became of a save. */
    public enum SaveOutcome {
        /** The key and its settings were written. */
        SAVED,
        /** The provider is none this instance can call. */
        PROVIDER_UNKNOWN,
        /** No key was given and none is stored for this provider to keep. */
        KEY_MISSING
    }
}

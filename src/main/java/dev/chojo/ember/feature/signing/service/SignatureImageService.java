/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.signing.entity.AccountSignature;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignaturePicture;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's own signature picture and their consent to letters being signed with it.
 *
 * <p>The picture belongs to the account, not to a membership, so it is the same at every station. It is
 * cleaned before it is kept ({@link SignatureImages}) and stored as one PNG in the account's file store;
 * the account's row in {@code account_signature} names it by its hash. Only its owner reads or replaces
 * it here. A signing act draws it into a document only after the owner's step-up, and a letter only under
 * the owner's consent; nobody can place another person's picture.
 *
 * <p>The consent is kept apart from the picture: it can be given before a picture exists and outlasts
 * replacing or deleting one, but signs nothing while no picture is saved.
 */
@Singleton
public class SignatureImageService {
    private static final Logger log = LoggerFactory.getLogger(SignatureImageService.class);
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_SIGNATURE;
    private static final String KEY = "signature.png";
    private static final String PNG = "image/png";

    private final AccountSignatureRepository signatures;
    private final AccountRepository accounts;
    private final StorageService storage;
    private final Clock clock;

    @Inject
    public SignatureImageService(
            AccountSignatureRepository signatures, AccountRepository accounts, StorageService storage) {
        this(signatures, accounts, storage, Clock.systemUTC());
    }

    /**
     * @param clock when pictures are saved and consent is given, which a test moves
     */
    public SignatureImageService(
            AccountSignatureRepository signatures, AccountRepository accounts, StorageService storage, Clock clock) {
        this.signatures = signatures;
        this.accounts = accounts;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * @param accountId the account
     * @return what the account keeps about its signature, all empty where it never saved anything
     */
    public AccountSignature settings(int accountId) {
        return signatures.find(accountId).orElseGet(() -> AccountSignature.none(accountId));
    }

    /**
     * Cleans a picture and keeps it as the account's signature, in place of the one before.
     *
     * @param accountId the account
     * @param data      the picture as sent
     * @param source    how it was made
     * @return what the account now keeps
     */
    public AccountSignature save(int accountId, byte[] data, SignatureImageSource source) {
        return save(accountId, SignatureImages.clean(data), source);
    }

    /**
     * Keeps an already cleaned picture as the account's signature, in place of the one before.
     *
     * @param accountId the account
     * @param picture   the cleaned picture
     * @param source    how it was made
     * @return what the account now keeps
     */
    public AccountSignature save(int accountId, SignaturePicture picture, SignatureImageSource source) {
        storage.store(scopeOf(accountId), CATEGORY, KEY, picture.png(), PNG);
        signatures.saveImage(accountId, picture.sha256(), source, clock.instant());
        log.info("Signature picture saved for account {} ({})", accountId, source);
        return settings(accountId);
    }

    /**
     * @param accountId the account
     * @return the account's signature picture, or empty where none is saved
     */
    public Optional<byte[]> image(int accountId) {
        if (!settings(accountId).hasImage()) return Optional.empty();
        return storage.readAllBytes(scopeOf(accountId), CATEGORY, KEY);
    }

    /**
     * Forgets the account's signature picture. Its consent stays, and signs nothing until a picture is saved
     * again.
     *
     * @param accountId the account
     * @return what the account now keeps
     */
    public AccountSignature delete(int accountId) {
        storage.delete(scopeOf(accountId), CATEGORY, KEY);
        signatures.clearImage(accountId);
        log.info("Signature picture deleted for account {}", accountId);
        return settings(accountId);
    }

    /**
     * Gives or takes back the consent to letters the person issues being signed with their picture.
     *
     * @param accountId the account
     * @param consented whether they consent from now on
     * @return what the account now keeps
     */
    public AccountSignature consent(int accountId, boolean consented) {
        @Nullable Instant at = consented ? clock.instant() : null;
        signatures.setConsent(accountId, at);
        log.info("Account {} {} automatic signing of letters", accountId, consented ? "agreed to" : "withdrew");
        return settings(accountId);
    }

    /**
     * Removes the stored picture of an account that is being deleted. Its row goes with the account.
     *
     * @param accountUid the account
     */
    public void deleteFiles(UUID accountUid) {
        storage.delete(new StorageScope.Account(accountUid), CATEGORY, KEY);
    }

    private StorageScope.Account scopeOf(int accountId) {
        UUID uid = accounts.findById(accountId)
                .map(Account::uid)
                .orElseThrow(() -> new IllegalArgumentException("No account " + accountId));
        return new StorageScope.Account(uid);
    }
}

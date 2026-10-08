/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.ParkedSigningStart;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Keeps started signing acts on the server between the start and the signer's confirmation, in the store
 * every WebAuthn ceremony parks its challenges in, under a challenge of its own purpose.
 *
 * <p>The browser holds only a random token; the store keeps its hash. A start is spent by the first
 * completion that names it, whether that completion succeeds or is refused, so a start is never used
 * twice, and it expires after {@link #LIFETIME}. Only the account that started it can spend it. Expired
 * starts nobody completed go with the store's regular sweep.
 *
 * <p>Every start writes a row, so an account holds at most {@link #MAX_OPEN_PER_ACCOUNT} that are neither
 * completed nor expired; a further start is refused until one of them is.
 */
@Singleton
public class SigningStarts {
    /** How long a started act waits for the signer's confirmation. */
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    /** How many started acts an account may hold that are neither completed nor expired. */
    public static final int MAX_OPEN_PER_ACCOUNT = 10;

    private final WebAuthnChallengeRepository challenges;

    @Inject
    public SigningStarts(WebAuthnChallengeRepository challenges) {
        this.challenges = challenges;
    }

    /**
     * Keeps a start until it is completed or expires.
     *
     * @param start what the completion needs
     * @return the token it is kept under and when it expires
     */
    public Parked park(ParkedSigningStart start) {
        if (challenges.countLive(ChallengePurpose.SIGNING, start.accountId()) >= MAX_OPEN_PER_ACCOUNT) {
            throw DocumentRefusal.SIGNING_STARTS_TOO_MANY.raise();
        }
        String token = RandomTokens.hex(32);
        Instant expiresAt = Instant.now().plus(LIFETIME);
        challenges.create(
                token, ChallengePurpose.SIGNING, start.accountId(), Json.MAPPER.writeValueAsString(start), expiresAt);
        return new Parked(token, expiresAt);
    }

    /**
     * Spends the start kept under the token. It is gone after this call, whatever the outcome.
     *
     * @param token     the token the start was handed out under
     * @param accountId the account completing it
     * @return the start
     */
    public ParkedSigningStart spend(String token, int accountId) {
        var stored = challenges
                .consume(token)
                .filter(challenge -> challenge.purpose() == ChallengePurpose.SIGNING)
                .filter(challenge -> Objects.equals(challenge.accountId(), accountId))
                .orElseThrow(DocumentRefusal.SIGNING_START_UNKNOWN::raise);
        if (stored.isExpired()) throw DocumentRefusal.SIGNING_START_EXPIRED.raise();
        return Json.MAPPER.readValue(stored.optionsJson(), ParkedSigningStart.class);
    }

    /**
     * A start as it was handed out.
     *
     * @param token     the token the browser hands back with the confirmation
     * @param expiresAt when the start can no longer be completed
     */
    public record Parked(String token, Instant expiresAt) {}
}

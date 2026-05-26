/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class FeedTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final FeedTokenRepository tokenRepository;

    @Inject
    public FeedTokenService(FeedTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public Optional<FeedToken> findByMember(int memberId) {
        return tokenRepository.findByMember(memberId);
    }

    public Optional<FeedToken> findByToken(String token) {
        return tokenRepository.findByToken(token);
    }

    /**
     * Gets or creates a feed token for the given member.
     */
    public FeedToken getOrCreate(int memberId) {
        return tokenRepository
                .findByMember(memberId)
                .orElseGet(() -> tokenRepository.create(memberId, generateToken()));
    }

    /**
     * Regenerates the feed token for the given member, invalidating the old one.
     */
    public FeedToken regenerate(int memberId) {
        return tokenRepository.create(memberId, generateToken());
    }

    /**
     * Records that the iCal feed was polled for the given member.
     */
    public void recordIcalPoll(int memberId) {
        tokenRepository.updateIcalPolled(memberId);
    }

    /**
     * Records that a notification feed (RSS/Atom) was polled for the given member.
     */
    public void recordNotificationPoll(int memberId) {
        tokenRepository.updateNotificationPolled(memberId);
    }

    /**
     * Deletes the feed token for the given member.
     */
    public boolean revoke(int memberId) {
        return tokenRepository.delete(memberId);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

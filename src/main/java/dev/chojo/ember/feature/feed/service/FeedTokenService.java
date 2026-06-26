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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class FeedTokenService {
    private static final Logger log = LoggerFactory.getLogger(FeedTokenService.class);
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
        return tokenRepository.findByMember(memberId).orElseGet(() -> {
            var created = tokenRepository.create(memberId, generateToken());
            log.info("Created feed token for member {}", memberId);
            return created;
        });
    }

    /**
     * Regenerates the feed token for the given member, invalidating the old one.
     */
    public FeedToken regenerate(int memberId) {
        var token = tokenRepository.create(memberId, generateToken());
        log.info("Regenerated feed token for member {}", memberId);
        return token;
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
        boolean revoked = tokenRepository.delete(memberId);
        if (revoked) {
            log.info("Revoked feed token for member {}", memberId);
        } else {
            log.warn("Feed token revoke affected zero rows for member {}", memberId);
        }
        return revoked;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

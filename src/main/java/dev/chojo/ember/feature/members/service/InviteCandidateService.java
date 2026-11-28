/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import dev.chojo.ember.feature.members.repository.InviteCandidateRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Finds the people a member manager may invite into their station by name, so somebody who already has
 * an account on this instance can be asked to join without anybody knowing their address.
 *
 * <p>Who can be found is decided by {@link InviteCandidateRepository}. The search needs one word of a
 * few letters before it answers and returns a handful of names at most, so it narrows down to somebody
 * the manager has in mind rather than listing who is here: single letters such as "a e n" match almost
 * every name and do not count.
 */
@Singleton
public class InviteCandidateService {

    /** The fewest letters the longest word of a search has to carry. */
    public static final int MIN_LETTERS = 3;

    /** The most people one search returns. */
    public static final int MAX_RESULTS = 10;

    private final InviteCandidateRepository candidates;
    private final InviteCandidateRateLimiter rateLimiter;

    @Inject
    public InviteCandidateService(InviteCandidateRepository candidates, InviteCandidateRateLimiter rateLimiter) {
        this.candidates = candidates;
        this.rateLimiter = rateLimiter;
    }

    /**
     * The people the manager may invite whose name holds every word of the search.
     *
     * @param by     the manager, at the station they invite into
     * @param search what they typed
     * @return at most {@link #MAX_RESULTS} people, by last and first name
     */
    public List<InviteCandidate> search(StationSession by, String search) {
        List<String> words = Arrays.stream(search.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .toList();
        if (words.stream().noneMatch(word -> word.length() >= MIN_LETTERS)) {
            throw MemberRefusal.INVITE_SEARCH_TOO_SHORT.raise();
        }
        RateLimits.enforce(MemberRefusal.INVITE_SEARCHING_TOO_OFTEN, rateLimiter.trySearch(by.accountId()));
        return candidates.find(by.accountId(), by.stationId(), words, MAX_RESULTS);
    }

    /**
     * The account behind this uid, checked to be one the manager may invite by name now: one they can
     * find, not at the station already, not waited on, not having declined the station lately, and not
     * invited by name from this station within the last day.
     *
     * @param by         the manager, at the station they invite into
     * @param accountUid the account, which the request could name freely
     * @return the account as a candidate
     */
    public InviteCandidate invitable(StationSession by, UUID accountUid) {
        var candidate = candidates
                .findReachable(by.accountId(), by.stationId(), accountUid)
                .orElseThrow(MemberRefusal.INVITE_ACCOUNT_NOT_FINDABLE::raise);
        if (candidate.alreadyHere()) throw MemberRefusal.INVITE_ACCOUNT_ALREADY_HERE.raise();
        if (candidate.alreadyAsked()) throw MemberRefusal.INVITE_ACCOUNT_ALREADY_ASKED.raise();
        if (candidate.declinedLately()) throw MemberRefusal.INVITE_ACCOUNT_DECLINED_LATELY.raise();
        RateLimits.enforce(
                MemberRefusal.INVITE_ACCOUNT_INVITED_LATELY,
                rateLimiter.tryInvite(by.stationId(), candidate.accountId()));
        return candidate;
    }
}

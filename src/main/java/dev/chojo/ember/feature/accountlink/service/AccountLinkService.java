/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.entity.LinkState;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A station asking a person to link their existing account to one of its members.
 *
 * <p>An account belongs to the person. A station that names an address it found on an import, or that
 * a member manager typed into an invitation, has not shown that the person behind it agrees to join,
 * so the member is kept without an account and the person is asked: in the app after their next
 * sign-in, and by mail where the installation can send mail. Until they accept, the station cannot
 * reach the account at all.
 */
@Singleton
public class AccountLinkService {
    private static final Logger log = LoggerFactory.getLogger(AccountLinkService.class);

    /** How long a person has to answer a request. */
    public static final Duration VALIDITY = Duration.ofDays(30);

    /** How long a station waits before it may send a request again. */
    public static final Duration SEND_AGAIN_AFTER = Duration.ofDays(1);

    private final AccountLinkRepository repository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    @Inject
    public AccountLinkService(
            AccountLinkRepository repository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            TokenHasher tokenHasher) {
        this(
                repository,
                accountRepository,
                stationRepository,
                memberRepository,
                emailService,
                mailLocaleService,
                tokenHasher,
                Clock.systemUTC());
    }

    /**
     * @param clock what tells the time, so a test can look at a request whose time is up
     */
    public AccountLinkService(
            AccountLinkRepository repository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            TokenHasher tokenHasher,
            Clock clock) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.emailService = emailService;
        this.mailLocaleService = mailLocaleService;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    /**
     * Asks the owner of an account whether the station may link it to one of its members. A member that
     * already waits for a link keeps the request it has.
     *
     * @param stationId the station that asks
     * @param memberId  the member, which exists without an account
     * @param accountId the account found by the address the station named
     * @param origin    how the station came to ask
     * @param createdBy the member who invited the address, or null for an import
     * @return the request
     */
    public AccountLinkRequest ask(
            int stationId, int memberId, int accountId, LinkOrigin origin, @Nullable Integer createdBy) {
        var unanswered = repository.findUnansweredForMember(memberId);
        if (unanswered.isPresent()) {
            if (unanswered.get().waits(clock.instant())) return unanswered.get();
            repository.answer(unanswered.get().id(), LinkAnswer.EXPIRED);
        }
        var account = accountRepository.findById(accountId).orElse(null);
        String token = account != null && mailGoesTo(account) ? RandomTokens.urlSafe(32) : null;
        var request = repository.create(
                stationId,
                memberId,
                accountId,
                origin,
                createdBy,
                token == null ? null : tokenHasher.hash(token),
                clock.instant().plus(VALIDITY));
        if (account != null && token != null) mail(account, request, token);
        log.info(
                "Station {} asks account {} to link to member {} ({}), mailed: {}",
                stationId,
                accountId,
                memberId,
                origin,
                token != null);
        return request;
    }

    /**
     * Where the member's latest link request stands, for the station that asked.
     *
     * @param stationId the station asking
     * @param memberId  the member
     * @return the state, or empty where the station never asked for this member
     */
    public Optional<LinkState> stateOf(int stationId, int memberId) {
        requireMemberHere(stationId, memberId);
        return repository.findLatestForMember(memberId).map(this::stateOf);
    }

    /**
     * Where the latest link request of every member of the station stands, for the member list.
     *
     * @param stationId the station asking
     * @return the state of each member the station ever asked for, by member id
     */
    public Map<Integer, LinkState> statesAt(int stationId) {
        var states = new TreeMap<Integer, LinkState>();
        repository
                .findLatestByStation(stationId)
                .forEach((memberId, request) -> states.put(memberId, stateOf(request)));
        return states;
    }

    /**
     * Sends a member's link request again: a request that still waits goes out once more with a fresh
     * deadline, and one that ran out is asked anew. Refused for a request the person declined, which is
     * their answer, and within a day of the last time it was sent, so a station cannot flood anybody's
     * postbox.
     *
     * @param stationId the station asking
     * @param memberId  the member
     * @param actorId   the member who sends it again
     * @return where the request stands now
     */
    public LinkState sendAgain(int stationId, int memberId, int actorId) {
        StationMember member = requireMemberHere(stationId, memberId);
        if (member.accountId() != null) throw MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN.raise();
        var latest =
                repository.findLatestForMember(memberId).orElseThrow(MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN::raise);
        var state = stateOf(latest);
        if (state.status() == LinkStatus.DECLINED) throw MemberRefusal.LINK_DECLINED_NOT_SENT_AGAIN.raise();
        Instant from = state.sendAgainFrom();
        if (from == null) throw MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN.raise();
        if (clock.instant().isBefore(from)) throw MemberRefusal.LINK_SENT_TOO_RECENTLY.raise();
        if (state.status() == LinkStatus.WAITING) {
            resend(latest);
        } else {
            ask(stationId, memberId, latest.accountId(), latest.origin(), actorId);
        }
        log.info("Member {} of station {} sent the link of member {} again", actorId, stationId, memberId);
        return repository
                .findLatestForMember(memberId)
                .map(this::stateOf)
                .orElseThrow(MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN::raise);
    }

    /**
     * Marks every request whose thirty days ran out without an answer as expired, for the sweep.
     *
     * @return how many ran out
     */
    public int expireOverdue() {
        int expired = repository.expireOverdue(clock.instant());
        if (expired > 0) log.info("{} link request(s) ran out without an answer", expired);
        return expired;
    }

    private void resend(AccountLinkRequest request) {
        var account = accountRepository.findById(request.accountId()).orElse(null);
        String token = account != null && mailGoesTo(account) ? RandomTokens.urlSafe(32) : null;
        repository.sendAgain(
                request.id(),
                token == null ? null : tokenHasher.hash(token),
                clock.instant().plus(VALIDITY));
        if (account != null && token != null) mail(account, request, token);
    }

    private LinkState stateOf(AccountLinkRequest request) {
        LinkStatus status = statusOf(request);
        Instant sendAgainFrom = status == LinkStatus.WAITING || status == LinkStatus.EXPIRED
                ? request.sentAt().plus(SEND_AGAIN_AFTER)
                : null;
        return new LinkState(
                status, request.origin(), request.sentAt(), request.expiresAt(), request.answeredAt(), sendAgainFrom);
    }

    private LinkStatus statusOf(AccountLinkRequest request) {
        LinkAnswer answer = request.answer();
        if (answer == null) return request.waits(clock.instant()) ? LinkStatus.WAITING : LinkStatus.EXPIRED;
        return switch (answer) {
            case ACCEPTED -> LinkStatus.ACCEPTED;
            case DECLINED -> LinkStatus.DECLINED;
            case EXPIRED -> LinkStatus.EXPIRED;
        };
    }

    private StationMember requireMemberHere(int stationId, int memberId) {
        return memberRepository
                .findById(memberId)
                .filter(member -> member.stationId() == stationId)
                .orElseThrow(MemberRefusal.LINK_MEMBER_NOT_HERE::raise);
    }

    /** Whether a link mail can reach this account: an address of its own and an installation that sends. */
    private boolean mailGoesTo(Account account) {
        return account.hasRealEmail() && emailService.isGlobalMailConfigured();
    }

    private void mail(Account account, AccountLinkRequest request, String token) {
        String stationName = stationRepository
                .findById(request.stationId())
                .map(Station::name)
                .orElse("");
        String memberName = memberRepository
                .findById(request.memberId())
                .map(StationMember::displayName)
                .orElse("");
        String address = account.email();
        if (address == null) return;
        emailService.sendAccountLinkRequest(
                address,
                NameParts.of(account).greeting(),
                stationName,
                memberName,
                token,
                mailLocaleService.forAccount(account.id()));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
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
        var waiting = repository.findUnansweredForMember(memberId);
        if (waiting.isPresent()) return waiting.get();
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

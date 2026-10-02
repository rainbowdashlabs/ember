/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.auth.OneTimePasswords;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * One-time passwords: the way into an account for somebody who cannot be sent a link, because the
 * instance has no mail or the person has no address that works.
 *
 * <p>Two doors lead here. A station administrator may issue one only for an account that belongs to
 * their station alone: no membership at any other station, current or former, no role in an
 * association, not an administrator of the instance, and not their own. Anything wider is somebody
 * else's account as well, and its password is not one station's to decide. An instance administrator
 * may issue one for any account but their own.
 *
 * <p>The password is drawn by Ember, shown once and kept only as a hash. It works for
 * {@link #VALIDITY}, and the first sign-in with it asks for a new password before there is a session.
 */
@Singleton
public class OneTimePasswordService {

    /** How long a one-time password works. */
    public static final Duration VALIDITY = Duration.ofDays(7);

    private final AccountRepository accountRepository;
    private final StationMemberRepository memberRepository;
    private final AuthService authService;
    private final TwoFactorAuditService auditService;
    private final Clock clock;

    @Inject
    public OneTimePasswordService(
            AccountRepository accountRepository,
            StationMemberRepository memberRepository,
            AuthService authService,
            TwoFactorAuditService auditService) {
        this(accountRepository, memberRepository, authService, auditService, Clock.systemUTC());
    }

    /**
     * @param clock what tells the time, so a test can issue a password that has already expired
     */
    public OneTimePasswordService(
            AccountRepository accountRepository,
            StationMemberRepository memberRepository,
            AuthService authService,
            TwoFactorAuditService auditService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Issues a one-time password on behalf of a station's administration.
     *
     * @param stationId the station that asks
     * @param actorId   the administrator's account
     * @param accountId the account the password is for
     * @return the password, to be shown once
     */
    public IssuedOneTimePassword issueForStation(
            int stationId, int actorId, int accountId, @Nullable String userAgent, @Nullable String country) {
        if (memberRepository.findByStationAndAccount(stationId, accountId).isEmpty()) {
            throw MemberRefusal.ACCOUNT_NOT_HERE_ON_ONE_TIME_PASSWORD.raise();
        }
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(MemberRefusal.ACCOUNT_NOT_HERE_ON_ONE_TIME_PASSWORD::raise);
        requireStationsAlone(account, stationId, actorId);
        return issue(
                account,
                actorId,
                stationId,
                MemberRefusal.ONE_TIME_PASSWORD_PASSWORDS_SWITCHED_OFF,
                userAgent,
                country);
    }

    /**
     * Issues a one-time password on behalf of the instance's administration.
     *
     * @param actorId   the administrator's account
     * @param accountId the account the password is for
     * @return the password, to be shown once
     */
    public IssuedOneTimePassword issueForInstance(
            int actorId, int accountId, @Nullable String userAgent, @Nullable String country) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(AdminRefusal.ACCOUNT_NOT_HERE_ON_ONE_TIME_PASSWORD::raise);
        if (account.id() == actorId) {
            throw AdminRefusal.ONE_TIME_PASSWORD_FOR_YOURSELF.raise();
        }
        return issue(account, actorId, null, AdminRefusal.ONE_TIME_PASSWORD_PASSWORDS_SWITCHED_OFF, userAgent, country);
    }

    /**
     * Refuses an account that is not the station's alone, naming the tie that stands in the way so
     * the administrator knows to ask an instance administrator.
     */
    private void requireStationsAlone(Account account, int stationId, int actorId) {
        if (account.id() == actorId) {
            throw MemberRefusal.ONE_TIME_PASSWORD_FOR_YOURSELF.raise();
        }
        if (account.instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            throw MemberRefusal.ONE_TIME_PASSWORD_FOR_INSTANCE_ADMINISTRATOR.raise();
        }
        var ties = accountRepository.findTies(account.id(), stationId);
        if (ties.association()) {
            throw MemberRefusal.ONE_TIME_PASSWORD_FOR_ASSOCIATION_ACCOUNT.raise();
        }
        if (ties.elsewhere()) {
            throw MemberRefusal.ONE_TIME_PASSWORD_FOR_SHARED_ACCOUNT.raise();
        }
    }

    private IssuedOneTimePassword issue(
            Account account,
            int actorId,
            @Nullable Integer stationId,
            Refusal passwordless,
            @Nullable String userAgent,
            @Nullable String country) {
        String password = OneTimePasswords.generate();
        Instant expiresAt = clock.instant().plus(VALIDITY);
        if (authService.issueOneTimePassword(account, password, expiresAt) != AuthService.SetPasswordOutcome.OK) {
            throw passwordless.raise();
        }
        auditService.recordByAdministration(
                account.id(), actorId, TwoFactorEvent.ONE_TIME_PASSWORD_ISSUED, stationId, userAgent, country);
        return new IssuedOneTimePassword(
                account.id(), NameParts.of(account).called(), account.loginName(), password, expiresAt);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The person's side of a link request: the requests waiting for their account, and accepting or
 * declining one. Stations ask to link the account to one of their members; associations ask it to take
 * a role, which {@link AssociationLinkAnswers} carries out. The person sees both in one list.
 *
 * <p>Only a signed-in session of the account a request names reaches it. The mailed link carries a
 * token that finds the request, but it only opens the question for that session and answers nothing on
 * its own: whoever holds the mail without the account learns nothing and can do nothing with it. Every
 * other miss, a request of somebody else, one already answered and one whose time ran out, is refused
 * alike, so a probe cannot tell them apart.
 */
@Singleton
public class LinkAnswerService {
    private static final Logger log = LoggerFactory.getLogger(LinkAnswerService.class);

    private final AccountLinkRepository repository;
    private final StationMemberRepository memberRepository;
    private final MemberNameResolver nameResolver;
    private final TwoFactorAuditService auditService;
    private final Notifier notifier;
    private final TokenHasher tokenHasher;
    private final AssociationLinkAnswers associationAnswers;
    private final Clock clock;

    @Inject
    public LinkAnswerService(
            AccountLinkRepository repository,
            StationMemberRepository memberRepository,
            MemberNameResolver nameResolver,
            TwoFactorAuditService auditService,
            Notifier notifier,
            TokenHasher tokenHasher,
            AssociationLinkAnswers associationAnswers) {
        this(
                repository,
                memberRepository,
                nameResolver,
                auditService,
                notifier,
                tokenHasher,
                associationAnswers,
                Clock.systemUTC());
    }

    /**
     * @param clock what tells the time, so a test can answer a request whose time is up
     */
    public LinkAnswerService(
            AccountLinkRepository repository,
            StationMemberRepository memberRepository,
            MemberNameResolver nameResolver,
            TwoFactorAuditService auditService,
            Notifier notifier,
            TokenHasher tokenHasher,
            AssociationLinkAnswers associationAnswers,
            Clock clock) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.nameResolver = nameResolver;
        this.auditService = auditService;
        this.notifier = notifier;
        this.tokenHasher = tokenHasher;
        this.associationAnswers = associationAnswers;
        this.clock = clock;
    }

    /**
     * The requests of stations and associations waiting for this account, oldest first.
     *
     * @param accountId the signed-in account
     * @return the requests
     */
    public List<LinkPrompt> waitingFor(int accountId) {
        Instant now = clock.instant();
        var waiting = new ArrayList<>(repository.findWaitingForAccount(accountId, now));
        waiting.addAll(associationAnswers.waitingFor(accountId, now));
        waiting.sort(Comparator.comparing(LinkPrompt::createdAt));
        return waiting;
    }

    /**
     * The request a mailed link opens, for the signed-in account it names. Answers nothing.
     *
     * @param accountId the signed-in account
     * @param token     the token from the link
     * @return the request as the person is shown it
     */
    public LinkPrompt opened(int accountId, String token) {
        String tokenHash = tokenHasher.hash(token);
        UUID uid = repository
                .findByTokenHash(tokenHash)
                .filter(found -> isWaitingFor(found, accountId))
                .map(AccountLinkRequest::uid)
                .or(() -> associationAnswers
                        .findOpenedBy(accountId, tokenHash, clock.instant())
                        .map(AssociationLinkRequest::uid))
                .orElseThrow(MemberRefusal.LINK_REQUEST_NOT_OPEN::raise);
        return waitingFor(accountId).stream()
                .filter(prompt -> prompt.uid().equals(uid))
                .findFirst()
                .orElseThrow(MemberRefusal.LINK_REQUEST_NOT_OPEN::raise);
    }

    /**
     * Accepts a request. A station's links the account to the member it asked about; an association's
     * makes the account a member there with the role it offered.
     *
     * @param accountId the signed-in account
     * @param uid       the request
     * @param userAgent the browser that answered, for the audit
     * @param country   the country it answered from, for the audit
     */
    public void accept(int accountId, UUID uid, @Nullable String userAgent, @Nullable String country) {
        var station = findWaiting(accountId, uid);
        if (station.isPresent()) {
            acceptStation(accountId, station.get(), userAgent, country);
            return;
        }
        associationAnswers.accept(accountId, requireAssociationWaiting(accountId, uid), userAgent, country);
    }

    /**
     * Declines a request. Nothing is linked or made, and whoever asked sees that it was declined.
     *
     * @param accountId the signed-in account
     * @param uid       the request
     */
    public void decline(int accountId, UUID uid) {
        var station = findWaiting(accountId, uid);
        if (station.isPresent()) {
            declineStation(accountId, station.get());
            return;
        }
        associationAnswers.decline(accountId, requireAssociationWaiting(accountId, uid));
    }

    /**
     * Links the account to the member the station asked about. The member keeps whatever the station
     * gave it, its permissions among them, which reach the account from now on. The answer and the link
     * are written together or not at all, while the account is held, so two requests of one station
     * accepted at once cannot give the account two members there. The account's own audit records it,
     * and whoever edits members at the station is told.
     */
    private void acceptStation(
            int accountId, AccountLinkRequest request, @Nullable String userAgent, @Nullable String country) {
        Transactions.run(() -> {
            repository.holdAccount(accountId);
            if (memberRepository
                    .findByStationAndAccount(request.stationId(), accountId)
                    .isPresent()) {
                throw MemberRefusal.LINK_ACCOUNT_ALREADY_AT_STATION.raise();
            }
            if (!repository.answer(request.id(), LinkAnswer.ACCEPTED)
                    || !memberRepository.linkAccount(request.memberId(), accountId)) {
                throw MemberRefusal.LINK_REQUEST_NOT_OPEN.raise();
            }
        });
        nameResolver.forget(request.memberId());
        auditService.recordAtStation(
                accountId, TwoFactorEvent.ACCOUNT_LINK_ACCEPTED, request.stationId(), userAgent, country);
        String memberName = nameResolver.official(request.memberId());
        notifier.notify(
                StationAudience.holders(request.stationId(), StationPermission.MEMBER_EDIT)
                        .except(request.memberId()),
                NotificationType.ACCOUNT_LINK_ACCEPTED,
                NotificationData.of(
                        new NotificationParams.AccountLinkAccepted(memberName),
                        NotificationLinks.member(request.memberId())),
                Delivery.EVERY_TIME);
        log.info(
                "Account {} accepted the link to member {} of station {}",
                accountId,
                request.memberId(),
                request.stationId());
    }

    /** Refuses the link. The member stays without an account, and the station sees that it was declined. */
    private void declineStation(int accountId, AccountLinkRequest request) {
        if (!repository.answer(request.id(), LinkAnswer.DECLINED)) {
            throw MemberRefusal.LINK_REQUEST_NOT_OPEN.raise();
        }
        log.info(
                "Account {} declined the link to member {} of station {}",
                accountId,
                request.memberId(),
                request.stationId());
    }

    private Optional<AccountLinkRequest> findWaiting(int accountId, UUID uid) {
        return repository.findByUid(uid).filter(found -> isWaitingFor(found, accountId));
    }

    private AssociationLinkRequest requireAssociationWaiting(int accountId, UUID uid) {
        return associationAnswers
                .findWaiting(accountId, uid, clock.instant())
                .orElseThrow(MemberRefusal.LINK_REQUEST_NOT_OPEN::raise);
    }

    private boolean isWaitingFor(AccountLinkRequest request, int accountId) {
        return request.accountId() == accountId && request.waits(clock.instant());
    }
}

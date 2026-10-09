/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkState;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.accountlink.repository.AssociationLinkRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An association asking a person to take a role there with the account they already have.
 *
 * <p>An address an association administrator types names an account, not the person's agreement to
 * act for the association. Until the person accepts, the association has no member for the account, so
 * nothing it does reaches the account and nothing about the account counts as tied to the association.
 * The person is asked in the app after their next sign-in, and by mail where the installation can send
 * mail. The association sees its requests by the address it typed, and may send one again once a day.
 */
@Singleton
public class AssociationLinkService {
    private static final Logger log = LoggerFactory.getLogger(AssociationLinkService.class);

    private final AssociationLinkRepository repository;
    private final AccountLinkRepository links;
    private final ClusterRepository clusterRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    @Inject
    public AssociationLinkService(
            AssociationLinkRepository repository,
            AccountLinkRepository links,
            ClusterRepository clusterRepository,
            AccountRepository accountRepository,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            TokenHasher tokenHasher) {
        this(
                repository,
                links,
                clusterRepository,
                accountRepository,
                emailService,
                mailLocaleService,
                tokenHasher,
                Clock.systemUTC());
    }

    /**
     * @param clock what tells the time, so a test can look at a request whose time is up
     */
    public AssociationLinkService(
            AssociationLinkRepository repository,
            AccountLinkRepository links,
            ClusterRepository clusterRepository,
            AccountRepository accountRepository,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            TokenHasher tokenHasher,
            Clock clock) {
        this.repository = repository;
        this.links = links;
        this.clusterRepository = clusterRepository;
        this.accountRepository = accountRepository;
        this.emailService = emailService;
        this.mailLocaleService = mailLocaleService;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    /**
     * Asks the owner of an account whether they take a role at the association. Refused for an account
     * that already holds a role there, and while an earlier request to it still waits.
     *
     * @param clusterId the association that asks
     * @param accountId the account found by the address the association typed
     * @param userType  the role it offers
     * @param address   the address it typed
     * @return where the request stands
     */
    public AssociationLinkState ask(int clusterId, int accountId, ClusterUserType userType, String address) {
        if (clusterRepository.findMember(clusterId, accountId).isPresent()) {
            throw ClusterRefusal.CLUSTER_ACCOUNT_ALREADY_A_MEMBER.raise();
        }
        var unanswered = repository.findUnanswered(clusterId, accountId);
        if (unanswered.isPresent()) {
            if (unanswered.get().waits(clock.instant())) throw ClusterRefusal.CLUSTER_LINK_ALREADY_WAITING.raise();
            links.answer(unanswered.get().id(), LinkAnswer.EXPIRED);
        }
        return stateOf(send(clusterId, accountId, userType, address));
    }

    /**
     * The association's requests that did not end in a membership: the ones that wait, the ones the
     * person declined and the ones that ran out, newest first. One per account, the latest.
     *
     * @param clusterId the association
     * @return where each stands
     */
    public List<AssociationLinkState> statesAt(int clusterId) {
        return repository.findLatestByCluster(clusterId).stream()
                .map(this::stateOf)
                .filter(state -> state.status() != LinkStatus.ACCEPTED)
                .toList();
    }

    /**
     * Sends a request again: one that still waits goes out once more with a fresh deadline, and one that
     * ran out is asked anew with the same role. Refused for a request the person declined, which is their
     * answer, for one a newer request replaced, and within a day of the last time it was sent.
     *
     * @param clusterId the association
     * @param uid       the request
     * @return where the request stands now
     */
    public AssociationLinkState sendAgain(int clusterId, UUID uid) {
        var request = repository
                .findByUid(uid)
                .filter(found -> found.clusterId() == clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_LINK_REQUEST_NOT_HERE::raise);
        boolean latest = repository
                .findLatest(clusterId, request.accountId())
                .map(found -> found.id() == request.id())
                .orElse(false);
        if (!latest
                || clusterRepository.findMember(clusterId, request.accountId()).isPresent()) {
            throw ClusterRefusal.CLUSTER_LINK_NOTHING_TO_SEND_AGAIN.raise();
        }
        var state = stateOf(request);
        if (state.status() == LinkStatus.DECLINED) throw ClusterRefusal.CLUSTER_LINK_DECLINED_NOT_SENT_AGAIN.raise();
        Instant from = state.sendAgainFrom();
        if (from == null) throw ClusterRefusal.CLUSTER_LINK_NOTHING_TO_SEND_AGAIN.raise();
        if (clock.instant().isBefore(from)) throw ClusterRefusal.CLUSTER_LINK_SENT_TOO_RECENTLY.raise();
        log.info("Cluster {} sends its request {} again", clusterId, uid);
        if (state.status() == LinkStatus.WAITING) return resend(request);
        if (request.answer() == null) links.answer(request.id(), LinkAnswer.EXPIRED);
        return stateOf(send(clusterId, request.accountId(), request.userType(), request.address()));
    }

    private AssociationLinkRequest send(int clusterId, int accountId, ClusterUserType userType, String address) {
        var account = accountRepository.findById(accountId).orElse(null);
        String token = mailableToken(account);
        var request = repository.create(
                clusterId,
                accountId,
                userType,
                address,
                hashOf(token),
                clock.instant().plus(AccountLinkService.VALIDITY));
        if (account != null && token != null) mail(account, request, token);
        log.info(
                "Cluster {} asks account {} to take the role {}, mailed: {}",
                clusterId,
                accountId,
                userType,
                token != null);
        return request;
    }

    private AssociationLinkState resend(AssociationLinkRequest request) {
        var account = accountRepository.findById(request.accountId()).orElse(null);
        String token = mailableToken(account);
        links.sendAgain(request.id(), hashOf(token), clock.instant().plus(AccountLinkService.VALIDITY));
        if (account != null && token != null) mail(account, request, token);
        return repository
                .findByUid(request.uid())
                .map(this::stateOf)
                .orElseThrow(ClusterRefusal.CLUSTER_LINK_REQUEST_NOT_HERE::raise);
    }

    private AssociationLinkState stateOf(AssociationLinkRequest request) {
        LinkStatus status = request.statusAt(clock.instant());
        return new AssociationLinkState(
                request.uid(),
                request.address(),
                request.userType(),
                status,
                request.sentAt(),
                request.expiresAt(),
                request.answeredAt(),
                AccountLinkService.sendAgainFrom(status, request.sentAt()));
    }

    /** A fresh token where a link mail can reach the account: an address of its own and an installation that sends. */
    private @Nullable String mailableToken(@Nullable Account account) {
        boolean reachable = account != null && account.hasRealEmail() && emailService.isGlobalMailConfigured();
        return reachable ? RandomTokens.urlSafe(32) : null;
    }

    private @Nullable String hashOf(@Nullable String token) {
        return token == null ? null : tokenHasher.hash(token);
    }

    private void mail(Account account, AssociationLinkRequest request, String token) {
        String address = account.email();
        if (address == null) return;
        String associationName = clusterRepository
                .findById(request.clusterId())
                .map(Cluster::name)
                .orElse("");
        emailService.sendAssociationLinkRequest(
                address,
                NameParts.of(account).greeting(),
                associationName,
                request.userType() == ClusterUserType.CLUSTER_ADMIN,
                token,
                mailLocaleService.forAccount(account.id()));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.accountlink.repository.AssociationLinkRepository;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The person's answer to an association's request to take a role there.
 *
 * <p>Reached only through {@link LinkAnswerService}, which finds the request for the signed-in account
 * and refuses every miss alike. Accepting makes the account a member of the association with the role
 * it offered, together with the answer or not at all. Declining leaves nothing behind but the answer.
 */
@Singleton
public class AssociationLinkAnswers {
    private static final Logger log = LoggerFactory.getLogger(AssociationLinkAnswers.class);

    private final AssociationLinkRepository repository;
    private final AccountLinkRepository links;
    private final ClusterRepository clusterRepository;
    private final AccountRepository accountRepository;
    private final TwoFactorAuditService auditService;
    private final Notifier notifier;

    @Inject
    public AssociationLinkAnswers(
            AssociationLinkRepository repository,
            AccountLinkRepository links,
            ClusterRepository clusterRepository,
            AccountRepository accountRepository,
            TwoFactorAuditService auditService,
            Notifier notifier) {
        this.repository = repository;
        this.links = links;
        this.clusterRepository = clusterRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.notifier = notifier;
    }

    /**
     * The associations' requests waiting for this account, oldest first.
     *
     * @param accountId the signed-in account
     * @param now       the moment asked about
     * @return the requests
     */
    List<LinkPrompt> waitingFor(int accountId, Instant now) {
        return repository.findWaitingForAccount(accountId, now);
    }

    /**
     * The association's request by its uid, while it waits for this account.
     *
     * @param accountId the signed-in account
     * @param uid       the request
     * @param now       the moment asked about
     * @return the request, or empty where none of this account waits under the uid
     */
    Optional<AssociationLinkRequest> findWaiting(int accountId, UUID uid, Instant now) {
        return repository.findByUid(uid).filter(found -> isWaitingFor(found, accountId, now));
    }

    /**
     * The association's request a mailed link carries the token of, while it waits for this account.
     *
     * @param accountId the signed-in account
     * @param tokenHash the hash of the token from the link
     * @param now       the moment asked about
     * @return the request, or empty where none of this account waits under the token
     */
    Optional<AssociationLinkRequest> findOpenedBy(int accountId, String tokenHash, Instant now) {
        return repository.findByTokenHash(tokenHash).filter(found -> isWaitingFor(found, accountId, now));
    }

    /**
     * Makes the account a member of the association with the role it offered. The answer and the
     * membership are written together or not at all. The account's own audit records it, and whoever
     * runs the association is told.
     *
     * @param accountId the signed-in account
     * @param request   the request, waiting for that account
     * @param userAgent the browser that answered, for the audit
     * @param country   the country it answered from, for the audit
     */
    void accept(int accountId, AssociationLinkRequest request, @Nullable String userAgent, @Nullable String country) {
        if (clusterRepository.findMember(request.clusterId(), accountId).isPresent()) {
            throw MemberRefusal.LINK_ACCOUNT_ALREADY_IN_ASSOCIATION.raise();
        }
        ClusterMember member = Transactions.call(() -> {
            if (!links.answer(request.id(), LinkAnswer.ACCEPTED)) throw MemberRefusal.LINK_REQUEST_NOT_OPEN.raise();
            return clusterRepository.addMember(request.clusterId(), accountId, request.userType());
        });
        auditService.record(accountId, null, TwoFactorEvent.ASSOCIATION_LINK_ACCEPTED, null, userAgent, country);
        String personName = accountRepository
                .findById(accountId)
                .map(account -> NameParts.of(account).identified())
                .orElse("");
        notifier.notify(
                ClusterAudience.holders(request.clusterId(), ClusterPermission.CLUSTER_ADMINISTRATOR)
                        .except(member.id()),
                NotificationType.ASSOCIATION_LINK_ACCEPTED,
                NotificationData.of(
                        new NotificationParams.AssociationLinkAccepted(personName), NotificationLinks.clusterTeam()),
                Delivery.EVERY_TIME);
        log.info("Account {} accepted the role {} at cluster {}", accountId, request.userType(), request.clusterId());
    }

    /**
     * Refuses the role. Nothing is made, and the association sees that it was declined.
     *
     * @param accountId the signed-in account
     * @param request   the request, waiting for that account
     */
    void decline(int accountId, AssociationLinkRequest request) {
        if (!links.answer(request.id(), LinkAnswer.DECLINED)) throw MemberRefusal.LINK_REQUEST_NOT_OPEN.raise();
        log.info("Account {} declined the role at cluster {}", accountId, request.clusterId());
    }

    private static boolean isWaitingFor(AssociationLinkRequest request, int accountId, Instant now) {
        return request.accountId() == accountId && request.waits(now);
    }
}

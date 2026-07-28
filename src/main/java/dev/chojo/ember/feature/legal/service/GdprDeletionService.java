/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.engine.GenericGdprDeleter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * GDPR-compliant deletion driven by {@code data_tracking.json}. Every TRACKED
 * {@code gdprDeletion} strategy is applied by {@link GenericGdprDeleter}; this service is just the
 * orchestrator that resolves the identity, runs the engine, and handles the few side effects that
 * sit outside the relational schema (avatar files on disk, account row finalization).
 *
 * <p>The hand-coded SQL queries from the previous implementation have been removed — the source
 * of truth for what gets deleted, anonymised, or retained is the tracking JSON.
 */
@Singleton
public class GdprDeletionService {

    private static final Logger log = getLogger(GdprDeletionService.class);

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final AvatarService avatarService;
    private final GenericGdprDeleter engine;

    @Inject
    public GdprDeletionService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            AvatarService avatarService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.avatarService = avatarService;
        DataTracking t;
        try {
            t = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json — GDPR deletion engine will be a no-op", e);
            t = DataTrackingLoader.empty();
        }
        this.engine = new GenericGdprDeleter(t);
    }

    /**
     * Deletes an account: each membership is anonymised first, then account-level data is removed
     * via the engine using the {@code ACCOUNT_ID} identity. CASCADE FKs from {@code account.id}
     * (sessions, tokens, credentials, etc.) take care of dependent rows.
     */
    public void deleteAccount(int accountId) {
        log.info("GDPR: starting account deletion for account {}", accountId);
        var members = stationMemberRepository.findAllByAccountId(accountId);
        for (var member : members) {
            anonymizeMember(member.id());
        }
        deleteAccountData(accountId);
        log.info("GDPR: account {} processed (memberships handled: {})", accountId, members.size());
    }

    /**
     * Anonymises a station member by running the engine for both the integer-id identity
     * ({@code MEMBER_ID}) and the UUID identity ({@code MEMBER_UID}). The avatar file is removed
     * from disk as a non-DB side effect.
     */
    public void anonymizeMember(int memberId) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        Integer accountId = member != null ? member.accountId() : null;
        UUID memberUid = memberLookupService.resolveUid(memberId);

        var memberReport = engine.deleteByIdentity(IdentityType.MEMBER_ID, memberId);
        memberReport.log(log);

        if (memberUid != null) {
            var uidReport = engine.deleteByIdentity(IdentityType.MEMBER_UID, memberUid);
            uidReport.log(log);
        }

        // The account is cleaned up at the end if the deleted member was the only membership.
        if (accountId != null) {
            var remaining = stationMemberRepository.findAllByAccountId(accountId);
            if (remaining.isEmpty()) {
                log.info("GDPR: account {} has no remaining members, deleting account", accountId);
                deleteAccountData(accountId);
            }
        }
    }

    private void deleteAccountData(int accountId) {
        UUID accountUid = accountRepository.resolveUid(accountId);
        var report = engine.deleteByIdentity(IdentityType.ACCOUNT_ID, accountId);
        report.log(log);
        if (accountUid != null) {
            avatarService.delete(accountUid);
        }
        // account.id has DELETE_EXPLICIT on the strategy list and the engine handles it in phase 2.
        // The repository delete is now redundant in the happy path, but kept as a final safeguard so
        // a missing strategy entry doesn't leave a dangling account row.
        if (accountRepository.findById(accountId).isPresent()) {
            accountRepository.delete(accountId);
        }
    }
}

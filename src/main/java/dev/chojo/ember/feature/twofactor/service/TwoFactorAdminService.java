/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.TwoFactorRefusal;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.repository.AccountRepository.PickerAccount;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorAuditEntry;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What the administration of second factors needs beside the policies: clearing a member's second
 * factor for their station, finding accounts, and reading the audit log.
 */
@Singleton
public class TwoFactorAdminService {
    private final TwoFactorRepository repository;
    private final TwoFactorService twoFactorService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository memberRepository;
    private final AccountReach accountReach;

    @Inject
    public TwoFactorAdminService(
            TwoFactorRepository repository,
            TwoFactorService twoFactorService,
            AccountRepository accountRepository,
            StationMemberRepository memberRepository,
            AccountReach accountReach) {
        this.repository = repository;
        this.twoFactorService = twoFactorService;
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.accountReach = accountReach;
    }

    /**
     * Clears a member's second factor on behalf of the station that looks after them.
     *
     * <p>The target has to be a member of the caller's own station and must not administer the
     * instance: a station administrator may only act on people they actually manage, and an
     * instance administrator is somebody only another instance administrator may reach. The account
     * also has to be the station's alone, as {@link AccountReach} decides.
     *
     * @param stationId      the caller's station
     * @param targetId       the account whose second factor is cleared
     * @param actorAccountId who clears it
     * @param userAgent      the caller's browser, for the audit log
     * @param country        the caller's country, for the audit log
     */
    public void resetForStation(
            int stationId, int targetId, int actorAccountId, @Nullable String userAgent, @Nullable String country) {
        if (memberRepository.findByStationAndAccount(stationId, targetId).isEmpty()) {
            throw TwoFactorRefusal.MEMBER_NOT_YOURS_TO_RESET.raise();
        }
        var target =
                accountRepository.findById(targetId).orElseThrow(TwoFactorRefusal.MEMBER_NOT_YOURS_TO_RESET::raise);
        if (target.instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            throw TwoFactorRefusal.ADMIN_ONLY_RESET_BY_ADMIN.raise();
        }
        accountReach.require(stationId, targetId, AccountAction.SECOND_FACTOR_RESET);
        if (!twoFactorService.resetAccount2FA(targetId, actorAccountId, userAgent, country)) {
            throw TwoFactorRefusal.SECOND_FACTOR_NOT_RESET_ON_STATION.raise();
        }
    }

    /**
     * Finds accounts for the administrator's picker. A UUID wins over a search text; one that is
     * not a UUID finds nobody.
     *
     * @param search a fragment of the name or address
     * @param uid    one account's UUID as the client sent it, or null
     * @param limit  how many at most, held between 1 and 50
     * @return the accounts found
     */
    public List<AccountSearchResult> searchAccounts(@Nullable String search, @Nullable String uid, int limit) {
        if (uid != null && !uid.isBlank()) {
            UUID lookup;
            try {
                lookup = UUID.fromString(uid);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
            return accountRepository
                    .findPickerByUid(lookup)
                    .map(TwoFactorAdminService::toResult)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return accountRepository.searchForPicker(search, Math.clamp(limit, 1, 50)).stream()
                .map(TwoFactorAdminService::toResult)
                .toList();
    }

    private static AccountSearchResult toResult(PickerAccount a) {
        return new AccountSearchResult(a.id(), a.uid(), a.displayName(), a.firstName(), a.lastName(), a.email());
    }

    /**
     * A page of the audit log, of one account or of everybody. A limit outside 1 to 200 reads as
     * 50 and a negative offset as the first page.
     *
     * @param accountId the account, or null for everybody
     * @param limit     how many entries
     * @param offset    how many to skip
     * @return the entries, newest first
     */
    public AuditResponse audit(@Nullable Integer accountId, int limit, int offset) {
        int size = limit <= 0 || limit > 200 ? 50 : limit;
        int skip = Math.max(0, offset);
        List<TwoFactorAuditEntry> entries = accountId != null
                ? repository.findAuditLog(accountId, size, skip)
                : repository.findRecentAudit(size, skip);
        return new AuditResponse(entries);
    }

    public record AuditResponse(List<TwoFactorAuditEntry> entries) {}

    public record AccountSearchResult(
            int id,
            UUID uid,
            String displayName,
            String firstName,
            String lastName,
            @Nullable String email) {}
}

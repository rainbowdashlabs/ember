/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The name shown next to knowledge-base content a member authored: a file, one of its versions, or
 * a comment. The station display name wins over the account name, so a member appears under the
 * name their station knows them by.
 */
@Singleton
public class KbAuthorNameService {
    private static final String UNKNOWN = "Unbekannt";

    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public KbAuthorNameService(StationMemberRepository stationMemberRepository, AccountRepository accountRepository) {
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Resolves the name shown for a member on knowledge-base content, preferring their station
     * display name over the account name and falling back to a placeholder when neither resolves.
     *
     * @param memberId the authoring member
     * @return the name to show
     */
    public String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(member -> {
                    if (member.displayName() != null && !member.displayName().isBlank()) {
                        return member.displayName();
                    }
                    if (member.accountId() != null) {
                        return accountRepository
                                .findById(member.accountId())
                                .map(Account::fullName)
                                .orElse(UNKNOWN);
                    }
                    return UNKNOWN;
                })
                .orElse(UNKNOWN);
    }
}

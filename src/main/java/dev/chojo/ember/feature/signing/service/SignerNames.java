/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * The official names a signing act shows and records: the holder of the account whose step-up confirms
 * it, and the member it concerns. Never a nickname, so the screen the signer reads and the evidence name
 * the same people the same way.
 */
@Singleton
public class SignerNames {
    private final AccountRepository accounts;
    private final MemberNameResolver names;

    @Inject
    public SignerNames(AccountRepository accounts, MemberNameResolver names) {
        this.accounts = accounts;
        this.names = names;
    }

    /**
     * @param accountId the account whose step-up confirms the act
     * @return the official name of its holder
     */
    public String accountHolder(int accountId) {
        return accounts.findById(accountId)
                .map(account -> NameParts.of(account).official())
                .orElseThrow(() -> new IllegalStateException("The signing account " + accountId + " is gone"));
    }

    /**
     * @param memberId the member the act concerns, or null where the account holder signs for themselves
     * @return the member's official name, or null where no member is named
     */
    public @Nullable String member(@Nullable Integer memberId) {
        if (memberId == null) return null;
        NameParts parts = names.parts(memberId);
        if (!parts.known()) throw new IllegalStateException("The member " + memberId + " is not known");
        return parts.official();
    }
}

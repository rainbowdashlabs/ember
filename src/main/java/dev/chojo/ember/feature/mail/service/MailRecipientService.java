/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Where an account's mail actually goes.
 *
 * <p>Somebody with an address of their own is written to at it. Somebody without one is a member a
 * guardian looks after, signing in with a name rather than an address, and everything Ember would have
 * written to them goes to the people who look after them instead. Where there is nobody either, the
 * mail is not sent: an account with no address is never written to directly, and no mail is invented
 * for it.
 *
 * <p>Every recipient is named after the person the mail is about, not after themselves. A guardian
 * looking after two children gets one mail per child and has to be able to tell which is which.
 */
@Singleton
public class MailRecipientService {

    private final AccountRepository accountRepository;
    private final StationMemberRepository memberRepository;

    @Inject
    public MailRecipientService(AccountRepository accountRepository, StationMemberRepository memberRepository) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * One address a mail about this account goes to.
     *
     * @param email    where the mail is delivered
     * @param name     the person the mail is about, for the greeting
     * @param guardian whether it is being read by somebody who looks after them, rather than by them
     */
    public record Recipient(String email, String name, boolean guardian) {}

    /**
     * Everybody a mail about this account goes to, which is empty when nobody can be reached.
     */
    public List<Recipient> forAccount(int accountId) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return List.of();
        if (account.hasRealEmail()) {
            return List.of(new Recipient(account.email(), account.firstName(), false));
        }
        return guardiansOf(account);
    }

    /**
     * Whether anything written to this account would reach anybody at all.
     */
    public boolean isReachable(int accountId) {
        return !forAccount(accountId).isEmpty();
    }

    private List<Recipient> guardiansOf(Account account) {
        var byAddress = new LinkedHashMap<String, Recipient>();
        for (var membership : memberRepository.findByAccount(account.id())) {
            for (var manager : memberRepository.findManagers(membership.id())) {
                if (manager.accountId() == null) continue;
                accountRepository
                        .findById(manager.accountId())
                        .filter(Account::hasRealEmail)
                        .ifPresent(guardian -> byAddress.putIfAbsent(
                                guardian.email().toLowerCase(Locale.ROOT),
                                new Recipient(guardian.email(), account.firstName(), true)));
            }
        }
        return new ArrayList<>(byAddress.values());
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether a station may act on the account behind one of its members.
 *
 * <p>An account belongs to the person, not to the station. Where the person is a member of this
 * station alone, the station looks after the account on their behalf and may reach its address and
 * its ways of signing in. Where the account also belongs to another station, current or former, or
 * holds a role in an association, it is not this station's to decide: the address and the
 * credentials are the person's to manage themselves. Each reason has its own refusal, so the screen
 * can say why.
 */
@Singleton
public class AccountReach {
    private static final Logger log = LoggerFactory.getLogger(AccountReach.class);

    private final AccountRepository accountRepository;

    @Inject
    public AccountReach(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Refuses the action when the account is not this station's alone.
     *
     * @param stationId the station that acts
     * @param accountId the account it acts on
     * @param action    what it does to the account
     */
    public void require(int stationId, int accountId, AccountAction action) {
        var ties = accountRepository.findTies(accountId, stationId);
        if (ties.association()) {
            log.info(
                    "Station {} refused {} on account {}: it holds a role in an association",
                    stationId,
                    action,
                    accountId);
            throw MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION.raise();
        }
        if (ties.elsewhere()) {
            log.info(
                    "Station {} refused {} on account {}: it belongs to another station", stationId, action, accountId);
            throw MemberRefusal.ACCOUNT_SHARED_WITH_ANOTHER_STATION.raise();
        }
    }
}

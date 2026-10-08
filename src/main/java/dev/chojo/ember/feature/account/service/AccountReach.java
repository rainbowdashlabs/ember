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
 * <p>An account belongs to the person, not to the station. Every action a station or a guardian takes
 * on it comes through here with what it does ({@link AccountAction}). What stays inside the station,
 * a name it shows or whether the member may sign in there, is the station's to decide. What changes
 * the account's address or the ways it signs in is the station's only where the person is a member of
 * this station alone and has confirmed the account:
 * <ul>
 *   <li>an account a station import created waits for its owner to sign in through the link sent to
 *       its address, or on an installation without mail to replace the imported password, so a bundle
 *       cannot plant an account under somebody else's address and then reset it;</li>
 *   <li>an account that also belongs to another station, current or former, or holds a role in an
 *       association, is the person's to manage themselves.</li>
 * </ul>
 * Each reason has its own refusal, so the screen can say why.
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
     * Refuses the action where it reaches past the station and the account is not this station's to
     * decide on.
     *
     * @param stationId the station that acts
     * @param accountId the account it acts on
     * @param action    what it does to the account
     */
    public void require(int stationId, int accountId, AccountAction action) {
        if (!action.reachesPastStation()) return;
        if (accountRepository.isUnconfirmed(accountId)) {
            log.info(
                    "Station {} refused {} on account {}: its owner has not confirmed it",
                    stationId,
                    action,
                    accountId);
            throw MemberRefusal.ACCOUNT_NOT_CONFIRMED_YET.raise();
        }
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

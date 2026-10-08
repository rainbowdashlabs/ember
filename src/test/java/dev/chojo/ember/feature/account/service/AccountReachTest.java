/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which accounts a station may reach the address and the sign-in of: its own alone, never one it
 * shares with another station or an association.
 */
class AccountReachTest extends RepositoryTestBase {
    private Station station;
    private Account account;
    private AccountReach reach;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Reach " + System.nanoTime());
        account = accountRepo.create("reach-" + System.nanoTime() + "@test.com", "Jan", "Kraus", true);
        stationMemberRepo.create(station.id(), account.id());
        reach = new AccountReach(accountRepo);
    }

    @Test
    void anAccountOfThisStationAloneIsReached() {
        for (AccountAction action : AccountAction.values()) {
            assertDoesNotThrow(() -> reach.require(station.id(), account.id(), action));
        }
    }

    @Test
    void anAccountThatIsAlsoAMemberElsewhereIsRefused() {
        var elsewhere = stationRepo.create("Reach elsewhere " + System.nanoTime());
        stationMemberRepo.create(elsewhere.id(), account.id());

        var refused = assertThrows(
                RefusalResponse.class, () -> reach.require(station.id(), account.id(), AccountAction.EMAIL_CHANGE));

        assertEquals(MemberRefusal.ACCOUNT_SHARED_WITH_ANOTHER_STATION, refused.refusal());
    }

    @Test
    void anImportedAccountItsOwnerHasNotConfirmedIsRefusedUntilTheyDo() {
        accountRepo.markUnconfirmed(account.id());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> reach.require(station.id(), account.id(), AccountAction.ONE_TIME_PASSWORD));
        assertEquals(MemberRefusal.ACCOUNT_NOT_CONFIRMED_YET, refused.refusal());

        accountRepo.confirm(account.id());
        assertDoesNotThrow(() -> reach.require(station.id(), account.id(), AccountAction.ONE_TIME_PASSWORD));
    }

    /**
     * What stays inside the station is the station's to decide even for an account it shares or its
     * owner has not confirmed: the name it shows, sign-in there, the setup mail the person still needs.
     */
    @Test
    void actionsInsideTheStationReachEveryAccountOfItsMembers() {
        var elsewhere = stationRepo.create("Reach inside " + System.nanoTime());
        stationMemberRepo.create(elsewhere.id(), account.id());
        accountRepo.markUnconfirmed(account.id());

        for (AccountAction action : AccountAction.values()) {
            if (action.reachesPastStation()) {
                assertThrows(RefusalResponse.class, () -> reach.require(station.id(), account.id(), action));
            } else {
                assertDoesNotThrow(() -> reach.require(station.id(), account.id(), action));
            }
        }
    }

    @Test
    void anAccountWithAnAssociationRoleIsRefused() {
        var association = clusterRepo.create("Reach association " + System.nanoTime(), null, station.id());
        clusterRepo.addMember(association.id(), account.id(), ClusterUserType.CLUSTER_USER);

        var refused = assertThrows(
                RefusalResponse.class, () -> reach.require(station.id(), account.id(), AccountAction.PASSWORD_RESET));

        assertEquals(MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION, refused.refusal());
    }
}

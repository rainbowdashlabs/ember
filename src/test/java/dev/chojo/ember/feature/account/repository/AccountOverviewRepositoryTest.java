/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.feature.account.entity.AccountOverview.AssociationRole;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account list as the database answers it: one row per account with what it belongs to and how
 * it signs in, searched and paged in the query.
 */
class AccountOverviewRepositoryTest extends RepositoryTestBase {
    private final AccountOverviewRepository repository = new AccountOverviewRepository();

    private AccountOverview only(String search) {
        var rows = repository.page(search, 10, 0);
        assertEquals(1, rows.size(), "exactly one account answers " + search);
        return rows.getFirst();
    }

    @Test
    void anAccountShowsItsStationsAssociationsAndHowItSignsIn() {
        String marker = "ovw" + System.nanoTime();
        var account = accountRepo.create(marker + "@test.com", "Lena", "Weber", true);
        var home = stationRepo.create("Overview Home " + marker);
        var left = stationRepo.create("Overview Left " + marker);
        stationMemberRepo.create(home.id(), account.id());
        stationMemberRepo.setFormer(
                stationMemberRepo.create(left.id(), account.id()).id(), true);
        var association = clusterRepo.create("Overview Association " + marker, null, home.id());
        clusterRepo.addMember(association.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
        Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        accountRepo.setOneTimePassword(account.id(), "{otp}", deadline);
        var factors = new TwoFactorRepository();
        factors.createFactor(account.id(), TwoFactorKind.TOTP, "phone");

        var row = only(marker);

        assertEquals(account.id(), row.id());
        assertEquals("Lena Weber", row.name());
        assertEquals(marker + "@test.com", row.email());
        assertEquals(marker + "@test.com", row.loginName());
        assertEquals(InstanceUserType.USER, row.instanceUserType());
        assertNull(row.lastSignInAt());
        assertEquals(List.of(home.name()), row.stations(), "a station left behind is not listed");
        assertEquals(
                List.of(new AssociationRole(association.name(), ClusterUserType.CLUSTER_ADMIN)), row.associations());
        assertTrue(row.hasPassword());
        assertTrue(row.passwordSignIn());
        assertEquals(deadline, row.oneTimePasswordExpiresAt());
        assertEquals(0, row.passkeys());
        assertTrue(row.twoFactor());
    }

    @Test
    void anAccountWithoutAddressOrPasswordSaysSo() {
        String marker = "ovwname" + System.nanoTime();
        var account = accountRepo.create(marker + "@station.local", "Kim", "Ohne", true);
        accountRepo.updateUsername(account.id(), marker);

        var row = only(marker);

        assertNull(row.email(), "a made-up address is no address");
        assertEquals(marker, row.loginName());
        assertFalse(row.hasPassword());
        assertFalse(row.passwordSignIn());
        assertFalse(row.twoFactor());
        assertTrue(row.stations().isEmpty());
        assertTrue(row.associations().isEmpty());
    }

    @Test
    void theSearchAndThePagesAreAnsweredByTheDatabase() {
        String marker = "ovwpage" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            accountRepo.create(marker + i + "@test.com", "Page", "Person " + i, true);
        }

        assertEquals(3, repository.count(marker));
        assertEquals(2, repository.page(marker, 2, 0).size());
        assertEquals(1, repository.page(marker, 2, 2).size());
        assertEquals("Page Person 2", repository.page(marker, 2, 2).getFirst().name());
        assertTrue(repository.count(null) >= 3);
        assertEquals(0, repository.count(marker + "nobody"));
    }
}

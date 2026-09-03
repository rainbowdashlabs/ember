/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.repository;

import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operator's counts, asserted as deltas: the database is shared with every other test class,
 * so absolute numbers would count somebody else's fixtures.
 */
class PasskeyRepositoryTest extends RepositoryTestBase {

    private static final PasskeyRepository repo = new PasskeyRepository();

    private int newAccount(String email) {
        return accountRepo.create(email, "PK", "Repo", true).id();
    }

    private int createPasskey(int accountId, boolean tried) {
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Passkey");
        twoFactorRepo.createWebAuthn(
                factor.id(),
                ("pkrepo-" + factor.id()).getBytes(),
                new byte[] {1},
                0,
                null,
                List.of("internal"),
                "none",
                new byte[64],
                true,
                false,
                true,
                true);
        if (tried) twoFactorRepo.touchFactorUsed(factor.id());
        return factor.id();
    }

    @Test
    void adoptionFiguresCountWhatTheyName() {
        var before = repo.adoptionFigures();

        int withTried = newAccount("fig1-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(withTried, "hash");
        createPasskey(withTried, true);

        int passwordOnly = newAccount("fig2-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(passwordOnly, "hash");

        int untried = newAccount("fig3-" + UUID.randomUUID() + "@test.com");
        createPasskey(untried, false);

        var after = repo.adoptionFigures();
        assertEquals(
                before.accountsWithTriedPasskey() + 1,
                after.accountsWithTriedPasskey(),
                "only the exercised passkey counts as adoption");
        assertEquals(before.accountsWithPassword() + 2, after.accountsWithPassword());
        assertEquals(
                before.accountsWithPasswordAndNoPasskey() + 1,
                after.accountsWithPasswordAndNoPasskey(),
                "the password holder without a passkey is the group that cannot move yet");
    }

    @Test
    void dependingOnPasskeyMeansNoOtherWayIn() {
        int before = repo.countAccountsDependingOnPasskey();

        int passwordOff = newAccount("dep1-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(passwordOff, "hash");
        accountRepo.setPasswordLoginDisabled(passwordOff, true);
        createPasskey(passwordOff, true);

        int noCredential = newAccount("dep2-" + UUID.randomUUID() + "@test.com");
        createPasskey(noCredential, true);

        int passwordStillOn = newAccount("dep3-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(passwordStillOn, "hash");
        createPasskey(passwordStillOn, true);

        assertEquals(
                before + 2,
                repo.countAccountsDependingOnPasskey(),
                "an account whose password still works does not depend on its passkey");
    }

    @Test
    void reportSeparatesTheResidue() {
        var before = repo.passwordlessReport();

        // Holds a password and a tried passkey: keeps the password, but can move.
        int movable = newAccount("rep1-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(movable, "hash");
        createPasskey(movable, true);
        accountRepo.touchLastSignIn(movable);

        // Holds a password, no passkey, and no reachable address: the QR code's population.
        int qrOnly = newAccount("rep2-" + UUID.randomUUID() + "@family.local");
        accountRepo.createCredential(qrOnly, "hash");

        var after = repo.passwordlessReport();
        assertEquals(before.wouldKeepPassword() + 2, after.wouldKeepPassword());
        assertEquals(before.withoutPasskey() + 1, after.withoutPasskey());
        assertEquals(before.reachableOnlyByQr() + 1, after.reachableOnlyByQr());
        assertTrue(
                after.dormantForAYear() >= before.dormantForAYear() + 1,
                "an account that never signed in counts as dormant");
    }

    @Test
    void offerAnswerRoundTrip() {
        int accountId = newAccount("offer-rt-" + UUID.randomUUID() + "@test.com");
        assertTrue(repo.findOfferAnswer(accountId).isEmpty(), "never answered reads as no answer");

        repo.answerOffer(accountId, false);
        var later = repo.findOfferAnswer(accountId).orElseThrow();
        assertFalse(later.declined());

        repo.answerOffer(accountId, true);
        assertTrue(repo.findOfferAnswer(accountId).orElseThrow().declined());
    }
}

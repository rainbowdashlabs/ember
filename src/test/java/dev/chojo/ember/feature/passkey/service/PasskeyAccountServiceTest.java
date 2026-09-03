/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasskeyAccountServiceTest extends RepositoryTestBase {

    private static final PasskeyRepository passkeyRepo = new PasskeyRepository();

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private PasskeyAccountService serviceInMode(String mode) throws Exception {
        var settings = new PasskeySettings();
        setField(settings, "mode", mode);
        var modeService = new PasskeyModeService(settings, new Demo(), new RelyingParties(null, null, false));
        return new PasskeyAccountService(
                passkeyRepo, twoFactorRepo, accountRepo, new TwoFactorAuditService(twoFactorRepo), modeService);
    }

    private int newAccount(String email) {
        return accountRepo.create(email, "PK", "Account", true).id();
    }

    private int createPasskey(int accountId, boolean tried) {
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Passkey");
        byte[] userHandle = new byte[64];
        twoFactorRepo.createWebAuthn(
                factor.id(),
                ("pkacc-" + factor.id()).getBytes(),
                new byte[] {1},
                0,
                null,
                List.of("internal"),
                "none",
                userHandle,
                true,
                false,
                true,
                true);
        if (tried) twoFactorRepo.touchFactorUsed(factor.id());
        return factor.id();
    }

    @Test
    void removingTheLastPasskeyOfAPasswordlessAccountIsRefused() throws Exception {
        var service = serviceInMode("PREFERRED");
        int accountId = newAccount("valve-" + UUID.randomUUID() + "@test.com");
        int factorId = createPasskey(accountId, true);

        var outcome = service.remove(accountId, factorId, "ua", null);
        assertEquals(
                PasskeyAccountService.RemovalOutcome.REFUSED_NO_PASSWORD,
                outcome,
                "a valve that opens onto nothing must stay shut");
        assertEquals(1, service.list(accountId).size(), "the passkey must still be there");
    }

    @Test
    void removingTheLastPasskeySwitchesPasswordSignInBackOn() throws Exception {
        var service = serviceInMode("PREFERRED");
        int accountId = newAccount("valve2-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(accountId, "hash");
        accountRepo.setPasswordLoginDisabled(accountId, true);
        int factorId = createPasskey(accountId, true);

        var outcome = service.remove(accountId, factorId, "ua", null);
        assertEquals(PasskeyAccountService.RemovalOutcome.REMOVED_PASSWORD_REENABLED, outcome);
        assertTrue(
                accountRepo.findCredential(accountId).orElseThrow().passwordLoginEnabled(),
                "the safety valve must open the password door again");
    }

    @Test
    void removingOnePasskeyOfSeveralLeavesTheSwitchAlone() throws Exception {
        var service = serviceInMode("PREFERRED");
        int accountId = newAccount("valve3-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(accountId, "hash");
        accountRepo.setPasswordLoginDisabled(accountId, true);
        int first = createPasskey(accountId, true);
        createPasskey(accountId, true);

        var outcome = service.remove(accountId, first, "ua", null);
        assertEquals(PasskeyAccountService.RemovalOutcome.REMOVED, outcome);
        assertFalse(
                accountRepo.findCredential(accountId).orElseThrow().passwordLoginEnabled(),
                "another passkey still opens the account, so the switch stays as the member set it");
    }

    @Test
    void anotherAccountsPasskeyIsNotReachable() throws Exception {
        var service = serviceInMode("PREFERRED");
        int owner = newAccount("owner-" + UUID.randomUUID() + "@test.com");
        int other = newAccount("other-" + UUID.randomUUID() + "@test.com");
        int factorId = createPasskey(owner, true);

        assertEquals(PasskeyAccountService.RemovalOutcome.NOT_FOUND, service.remove(other, factorId, "ua", null));
        assertFalse(service.rename(other, factorId, "stolen"));
    }

    @Test
    void switchingPasswordSignInOffIsGuarded() throws Exception {
        int accountId = newAccount("switch-" + UUID.randomUUID() + "@test.com");
        accountRepo.createCredential(accountId, "hash");

        assertEquals(
                PasskeyAccountService.SwitchOutcome.MODE_FORBIDS,
                serviceInMode("OPTIONAL").setPasswordLogin(accountId, false, "ua", null));

        var preferred = serviceInMode("PREFERRED");
        assertEquals(
                PasskeyAccountService.SwitchOutcome.NO_TRIED_PASSKEY,
                preferred.setPasswordLogin(accountId, false, "ua", null));

        int factorId = createPasskey(accountId, false);
        assertEquals(
                PasskeyAccountService.SwitchOutcome.NO_TRIED_PASSKEY,
                preferred.setPasswordLogin(accountId, false, "ua", null),
                "a passkey that never completed a ceremony is not evidence");

        twoFactorRepo.touchFactorUsed(factorId);
        assertEquals(PasskeyAccountService.SwitchOutcome.OK, preferred.setPasswordLogin(accountId, false, "ua", null));
        assertFalse(accountRepo.findCredential(accountId).orElseThrow().passwordLoginEnabled());

        assertEquals(PasskeyAccountService.SwitchOutcome.OK, preferred.setPasswordLogin(accountId, true, "ua", null));
        assertTrue(accountRepo.findCredential(accountId).orElseThrow().passwordLoginEnabled());
    }

    @Test
    void switchingOffNeedsAReachableAddress() throws Exception {
        int accountId = newAccount("kid-" + UUID.randomUUID() + "@family.local");
        accountRepo.createCredential(accountId, "hash");
        int factorId = createPasskey(accountId, true);
        twoFactorRepo.touchFactorUsed(factorId);

        assertEquals(
                PasskeyAccountService.SwitchOutcome.NO_REACHABLE_ADDRESS,
                serviceInMode("PREFERRED").setPasswordLogin(accountId, false, "ua", null),
                "the recovery mail for that account cannot be delivered");
    }

    @Test
    void theOfferAppearsOnceAndAnAnswerHolds() throws Exception {
        var service = serviceInMode("ENCOURAGED");
        int accountId = newAccount("offer-" + UUID.randomUUID() + "@test.com");

        assertTrue(service.shouldOffer(accountId));

        service.answerOffer(accountId, false);
        assertFalse(service.shouldOffer(accountId), "\"later\" holds the offer back for thirty days");

        service.answerOffer(accountId, true);
        assertFalse(service.shouldOffer(accountId), "\"no thanks\" ends it");
    }

    @Test
    void theOfferIsSuppressedWhereItWouldMislead() throws Exception {
        int withPasskey = newAccount("offer2-" + UUID.randomUUID() + "@test.com");
        createPasskey(withPasskey, false);
        assertFalse(serviceInMode("ENCOURAGED").shouldOffer(withPasskey), "somebody holding one is not offered one");

        int noAddress = newAccount("offer3-" + UUID.randomUUID() + "@family.local");
        assertFalse(
                serviceInMode("ENCOURAGED").shouldOffer(noAddress),
                "an account with no reachable address is never nagged");

        int optionalMode = newAccount("offer4-" + UUID.randomUUID() + "@test.com");
        assertFalse(serviceInMode("OPTIONAL").shouldOffer(optionalMode), "OPTIONAL suggests nothing to anybody");
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.data.ByteArray;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The second-factor view of the credential store: after a password only second factors are
 * offered, so a passkey is never asked for as if it were one, while the read-through lookups
 * still see every credential the account has.
 */
class SecondFactorCredentialStoreTest extends RepositoryTestBase {

    private static SecondFactorCredentialStore store;
    private static WebAuthnCredentialStore fullStore;

    @BeforeAll
    static void setup() {
        fullStore = new WebAuthnCredentialStore(twoFactorRepo);
        store = new SecondFactorCredentialStore(twoFactorRepo, fullStore);
    }

    private int newAccount() {
        return accountRepo
                .create("sf-store-" + UUID.randomUUID() + "@test.com", "SF", "Store", true)
                .id();
    }

    private byte[] plant(int accountId, boolean signIn, boolean secondFactor, byte[] userHandle) {
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Credential");
        byte[] credentialId = ("sf-store-" + factor.id()).getBytes();
        twoFactorRepo.createWebAuthn(
                factor.id(),
                credentialId,
                new byte[] {1},
                0,
                null,
                List.of("usb"),
                "none",
                userHandle,
                signIn,
                secondFactor,
                signIn ? true : null,
                signIn);
        return credentialId;
    }

    @Test
    void onlySecondFactorsAreOfferedAfterAPassword() {
        int accountId = newAccount();
        plant(accountId, true, false, new byte[64]);
        byte[] keyId = plant(accountId, false, true, new byte[64]);

        var offered = store.getCredentialIdsForUsername(Integer.toString(accountId));

        assertEquals(1, offered.size(), "the passkey is never asked for as if it were a second factor");
        assertEquals(new ByteArray(keyId), offered.iterator().next().getId());
    }

    @Test
    void aUsernameThatIsNoAccountIdOffersNothing() {
        assertTrue(store.getCredentialIdsForUsername("not-a-number").isEmpty());
    }

    @Test
    void theLookupsReadThroughToTheFullStore() {
        int accountId = newAccount();
        byte[] userHandle = new byte[64];
        userHandle[0] = 42;
        byte[] credentialId = plant(accountId, true, false, userHandle);

        assertEquals(
                fullStore.getUserHandleForUsername(Integer.toString(accountId)),
                store.getUserHandleForUsername(Integer.toString(accountId)));
        assertEquals(
                Integer.toString(accountId),
                store.getUsernameForUserHandle(new ByteArray(userHandle)).orElseThrow());
        assertTrue(store.lookup(new ByteArray(credentialId), new ByteArray(userHandle))
                .isPresent());
        assertFalse(store.lookupAll(new ByteArray(credentialId)).isEmpty());
    }
}

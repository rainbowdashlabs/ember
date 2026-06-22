/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.data.ByteArray;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebAuthnCredentialStoreTest extends RepositoryTestBase {

    private int newAccount() {
        return accountRepo
                .create("wa-store-" + UUID.randomUUID() + "@test.com", "WA", "Store", true)
                .id();
    }

    @Test
    void emptyAccountReturnsEmptySets() {
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        int accountId = newAccount();
        assertTrue(store.getCredentialIdsForUsername(String.valueOf(accountId)).isEmpty());
        assertTrue(store.getUserHandleForUsername(String.valueOf(accountId)).isEmpty());
        assertTrue(store.lookup(new ByteArray(new byte[] {1, 2, 3}), new ByteArray(new byte[64]))
                .isEmpty());
        assertTrue(store.lookupAll(new ByteArray(new byte[] {1, 2, 3})).isEmpty());
    }

    @Test
    void usernameAndHandleResolution() {
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        int accountId = newAccount();
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Key");
        byte[] credentialId = new byte[] {9, 9, 9};
        byte[] userHandle = new byte[64];
        for (int i = 0; i < userHandle.length; i++) userHandle[i] = (byte) (i + 1);
        twoFactorRepo.createWebAuthn(
                factor.id(), credentialId, new byte[] {1, 2, 3}, 0, null, List.of("usb"), "packed", userHandle);

        var descriptors = store.getCredentialIdsForUsername(String.valueOf(accountId));
        assertEquals(1, descriptors.size());

        var handle = store.getUserHandleForUsername(String.valueOf(accountId)).orElseThrow();
        assertArrayEquals(userHandle, handle.getBytes());

        var resolvedUsername =
                store.getUsernameForUserHandle(new ByteArray(userHandle)).orElseThrow();
        assertEquals(String.valueOf(accountId), resolvedUsername);

        // lookup with matching userHandle returns a RegisteredCredential
        var rc = store.lookup(new ByteArray(credentialId), new ByteArray(userHandle))
                .orElseThrow();
        assertEquals(0L, rc.getSignatureCount());

        // mismatched user handle returns empty
        assertTrue(store.lookup(new ByteArray(credentialId), new ByteArray(new byte[64]))
                .isEmpty());

        // lookupAll returns all matches by credential id (ignores user handle)
        assertEquals(1, store.lookupAll(new ByteArray(credentialId)).size());
    }

    @Test
    void invalidUsernameSafelyReturnsEmpty() {
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        assertTrue(store.getCredentialIdsForUsername("not-a-number").isEmpty());
        assertTrue(store.getUserHandleForUsername(null).isEmpty());
    }
}
